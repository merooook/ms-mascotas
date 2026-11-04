package com.duoc.ms_mascotas.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import com.duoc.ms_mascotas.DTO.ActualizarMascotaDTO;
import com.duoc.ms_mascotas.DTO.CrearMascotaDTO;
import com.duoc.ms_mascotas.DTO.MascotaEliminadaEvento;
import com.duoc.ms_mascotas.DTO.MascotaReportadaEvento;
import com.duoc.ms_mascotas.DTO.MascotaRetiradaEvento;
import com.duoc.ms_mascotas.DTO.MascotaReunificadaEvento;
import com.duoc.ms_mascotas.DTO.MascotaResponseDTO;
import com.duoc.ms_mascotas.DTO.UbicacionDTO;
import com.duoc.ms_mascotas.exception.AccesoDenegadoException;
import com.duoc.ms_mascotas.exception.RecursoNoEncontradoException;
import com.duoc.ms_mascotas.exception.SolicitudInvalidaException;
import com.duoc.ms_mascotas.messaging.OutboxEventos;
import com.duoc.ms_mascotas.model.Estado;
import com.duoc.ms_mascotas.model.EstadoModeracion;
import com.duoc.ms_mascotas.model.Mascota;
import com.duoc.ms_mascotas.repository.MascotaRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class MascotaService {

    private final MascotaRepository mascotaRepository;
    private final MongoTemplate mongoTemplate;
    private final OutboxEventos eventos;
    private final FotografiaService fotografiaService;

    public static final String TIPO_EVENTO_MASCOTA_REPORTADA = "mascota.reportada";
    public static final String TIPO_EVENTO_MASCOTA_REUNIFICADA = "mascota.reunificada";
    public static final String TIPO_EVENTO_MASCOTA_ELIMINADA = "mascota.eliminada";
    /* dejó de estar publicado sin cerrarse (rechazado o devuelto a moderación): ms-alertas desactiva su alerta */
    public static final String TIPO_EVENTO_MASCOTA_RETIRADA = "mascota.retirada";

    private static final Duration DURACION_TOKEN_CONFIRMACION = Duration.ofDays(7);

    /* el listado público acepta ordenar solo por campos públicos: antes valía cualquiera, así que
     * ?sort=emailContacto,asc (sin login) ordenaba por el correo privado de los dueños y revelaba datos
     * que las respuestas públicas ocultan; ordenar por un campo sin índice tampoco es gratis para Mongo */
    private static final Set<String> CAMPOS_ORDENABLES = Set.of("fecha", "nombre", "comuna", "estado", "tipoMascota");
    private static final int TAMANO_MAXIMO_PAGINA = 200;

    private static final int MAX_CARACTERISTICAS = 20;
    private static final int LARGO_MAXIMO_CLAVE_CARACTERISTICA = 50;
    private static final int LARGO_MAXIMO_VALOR_CARACTERISTICA = 200;

    public enum ResultadoConfirmacion { CONFIRMADA, YA_REUNIFICADA, TOKEN_INVALIDO, TOKEN_EXPIRADO }

    /* emailVerificado: el correo del id_token que validó el BFF. El del body no se usa (ver CrearMascotaDTO). */
    public MascotaResponseDTO crearMascota(CrearMascotaDTO dto, String usuarioId, String emailVerificado) {
        /* sin el nombre en el log: es texto libre del usuario (falsificación de líneas de log) */
        log.info("Creando mascota: tipo={}, usuarioId={}", dto.getTipoMascota(), usuarioId);
        String correo = correoValido(emailVerificado);
        if (correo == null) {
            throw new SolicitudInvalidaException(
                    "No se pudo verificar tu correo. Cierra sesión y vuelve a iniciarla");
        }

        Estado estadoInicial = dto.getEstado() != null ? dto.getEstado() : Estado.EXTRAVIADO;
        if (estadoInicial == Estado.REUNIFICADO) {
            throw new SolicitudInvalidaException("El estado inicial debe ser EXTRAVIADO o ENCONTRADO");
        }
        validarFotografia(dto.getFotografia(), usuarioId);
        validarCaracteristicas(dto.getCaracteristicas());

        Mascota mascota = Mascota.builder()
                .usuarioId(usuarioId)
                .emailContacto(correo)
                .tipoMascota(dto.getTipoMascota())
                .nombre(dto.getNombre())
                .fotografia(dto.getFotografia())
                .estado(estadoInicial)
                .estadoModeracion(EstadoModeracion.PENDIENTE)
                .tipoReporte(estadoInicial)
                .ubicacion(toGeoJsonPoint(dto.getUbicacion()))
                .comuna(dto.getComuna())
                .fecha(LocalDateTime.now())
                .descripcion(dto.getDescripcion())
                .caracteristicas(construirCaracteristicas(dto.getDescripcion(), dto.getCaracteristicas()))
                .build();

        Mascota guardada = mascotaRepository.save(mascota);
        log.info("Mascota creada exitosamente: idMascota={}", guardada.getIdMascota());
        /* la alerta de zona NO se crea acá: el reporte nace PENDIENTE y solo se publica al aprobarse (antes se
         * publicaba al crear y la alerta quedaba visible en el mapa aunque el reporte se rechazara) */
        return mapToResponseDTO(guardada);
    }

    /* null si no parece un correo: evita guardar basura o saltos de línea que acaben en un encabezado de correo */
    static String correoValido(String correo) {
        if (correo == null) {
            return null;
        }
        String limpio = correo.strip();
        if (limpio.isEmpty() || limpio.length() > 150 || !limpio.matches("[^\\s@\\r\\n]+@[^\\s@\\r\\n]+\\.[^\\s@\\r\\n]+")) {
            return null;
        }
        return limpio;
    }

    // Se publica al APROBAR (ms-alertas crea entonces la alerta de zona, radio 1,5 km). Solo si hay ubicación:
    // sin lat/lng ms-alertas no tiene con qué crearla.
    private void publicarMascotaReportada(Mascota mascota) {
        if (mascota.getUbicacion() == null) {
            log.debug("Mascota sin ubicación, no se publica evento de alerta: idMascota={}", mascota.getIdMascota());
            return;
        }

        MascotaReportadaEvento evento = new MascotaReportadaEvento(
                mascota.getIdMascota(),
                mascota.getUbicacion().getY(),
                mascota.getUbicacion().getX());

        publicarSinFallar(TIPO_EVENTO_MASCOTA_REPORTADA, evento, mascota.getIdMascota());
    }

    // Avisa a quien creó ESTE reporte (dueño si era EXTRAVIADO, quien la
    // encontró si era ENCONTRADO) — cada reporte se notifica a sí mismo, sin
    // cruzarse con el otro. Sin correo guardado, no hay a quién avisar.
    private void publicarMascotaReunificada(Mascota mascota) {
        if (mascota.getEmailContacto() == null || mascota.getEmailContacto().isBlank()) {
            log.debug("Mascota sin emailContacto, no se publica evento de reunificacion: idMascota={}", mascota.getIdMascota());
            return;
        }

        MascotaReunificadaEvento evento = new MascotaReunificadaEvento(
                mascota.getIdMascota(),
                mascota.getNombre(),
                mascota.getEmailContacto());

        publicarSinFallar(TIPO_EVENTO_MASCOTA_REUNIFICADA, evento, mascota.getIdMascota());
    }

    /*
     * La escritura en Mongo ya se hizo cuando se llega acá. Si SNS (o LocalStack) falla, la petición no se rompe
     * (antes terminaba en 500 y la persona duplicaba el reporte) y el evento queda guardado en Mongo para que
     * OutboxEventos lo reintente con espera creciente (ver su comentario sobre qué cubre y qué no).
     */
    private void publicarSinFallar(String tipoEvento, Object evento, String mascotaId) {
        eventos.publicar(tipoEvento, evento, mascotaId);
    }

    public Page<MascotaResponseDTO> listarTodas(Estado estado, Pageable pageable) {
        log.debug("Listando mascotas, estado={}", estado);
        return listarConFiltros(null, estado, null, null, pageable);
    }

    public Page<MascotaResponseDTO> listarConFiltros(String usuarioId, Estado estado, String tipoMascota,
            String comuna, Pageable pageable) {
        log.debug("Listando mascotas filtradas: usuarioId={}, estado={}, tipoMascota={}, comuna={}",
            usuarioId, estado, tipoMascota, comuna);

        Pageable paginacion = paginacionSegura(pageable);

        List<Criteria> criterios = new java.util.ArrayList<>();
        criterios.add(new Criteria().orOperator(
                Criteria.where("estadoModeracion").is(EstadoModeracion.APROBADA),
                Criteria.where("estadoModeracion").exists(false)));
        if (estado != null) {
            criterios.add(Criteria.where("estado").is(estado));
        }
        if (tipoMascota != null) {
            criterios.add(Criteria.where("tipoMascota").is(tipoMascota));
        }
        if (comuna != null) {
            // regex + case-insensitive: evita que "Viña del Mar" y
            // "viña del mar" cuenten como comunas distintas.
            criterios.add(Criteria.where("comuna").regex("^" + java.util.regex.Pattern.quote(comuna) + "$", "i"));
        }

        Query query = new Query(new Criteria().andOperator(criterios.toArray(Criteria[]::new)));
        long total = mongoTemplate.count(query, Mascota.class);
        List<Mascota> mascotas = mongoTemplate.find(query.with(paginacion), Mascota.class);
        List<MascotaResponseDTO> dtos = mascotas.stream().map(this::mapToResponseDTO).toList();
        return new PageImpl<>(dtos, paginacion, total);
    }

    /* orden solo por campos permitidos y tamaño de página acotado; sin paginar, primera página */
    Pageable paginacionSegura(Pageable pedida) {
        if (pedida == null || pedida.isUnpaged()) {
            return PageRequest.of(0, TAMANO_MAXIMO_PAGINA);
        }
        for (Sort.Order orden : pedida.getSort()) {
            if (!CAMPOS_ORDENABLES.contains(orden.getProperty())) {
                throw new SolicitudInvalidaException("No se puede ordenar por '" + orden.getProperty()
                        + "'. Campos permitidos: " + String.join(", ", CAMPOS_ORDENABLES.stream().sorted().toList()));
            }
        }
        return PageRequest.of(pedida.getPageNumber(), Math.min(pedida.getPageSize(), TAMANO_MAXIMO_PAGINA), pedida.getSort());
    }

    // usuarioId es opcional (invitados pueden ver el detalle de una mascota
    // aprobada sin estar logueados) — pero si viene y coincide con el dueño
    // del reporte, deja ver el detalle aunque esté pendiente o rechazado:
    // la persona que lo creó debe poder revisar su propia publicación.
    public Optional<MascotaResponseDTO> obtenerPorId(String id, String usuarioId) {
        log.debug("Buscando mascota por id={}", id);
        return mascotaRepository.findById(id)
                .filter(m -> estaAprobada(m) || (usuarioId != null && usuarioId.equals(m.getUsuarioId())))
                .map(this::mapToResponseDTO);
    }

    // Para el endpoint interno que consume ms-alertas (POST
    // /internal/mascotas/{id}/contacto) — nunca se expone al frontend, así
    // que no pasa por mapToResponseDTO ni por el redondeo de ubicación.
    public Optional<Mascota> obtenerParaContacto(String id) {
        log.debug("Resolviendo contacto interno para mascotaId={}", id);
        return mascotaRepository.findById(id).filter(this::estaAprobada);
    }

    public Page<MascotaResponseDTO> listarParaModeracion(EstadoModeracion estadoModeracion, Pageable pageable) {
        Pageable paginacion = paginacionSegura(pageable);
        Criteria criterio = estadoModeracion == EstadoModeracion.APROBADA
                ? new Criteria().orOperator(
                        Criteria.where("estadoModeracion").is(EstadoModeracion.APROBADA),
                        Criteria.where("estadoModeracion").exists(false))
                : Criteria.where("estadoModeracion").is(estadoModeracion);
        Query query = new Query(criterio);
        long total = mongoTemplate.count(query, Mascota.class);
        List<MascotaResponseDTO> contenido = mongoTemplate.find(query.with(paginacion), Mascota.class)
                .stream().map(this::mapToResponseDTO).toList();
        return new PageImpl<>(contenido, paginacion, total);
    }

    /* adminSub: queda en el log de cada decisión para poder rastrearla (sin datos personales: solo el sub y el id) */
    public MascotaResponseDTO aprobarMascota(String id, String adminSub) {
        MascotaResponseDTO respuesta = cambiarEstadoModeracion(id, EstadoModeracion.APROBADA);
        log.info("Moderación: admin={} accion=APROBAR idMascota={}", adminSub, id);
        return respuesta;
    }

    public MascotaResponseDTO rechazarMascota(String id, String adminSub) {
        MascotaResponseDTO respuesta = cambiarEstadoModeracion(id, EstadoModeracion.RECHAZADA);
        log.info("Moderación: admin={} accion=RECHAZAR idMascota={}", adminSub, id);
        return respuesta;
    }

    public void eliminarComoAdministrador(String id, String adminSub) {
        Mascota mascota = buscar(id);
        mascotaRepository.delete(mascota);
        log.info("Moderación: admin={} accion=ELIMINAR idMascota={}", adminSub, id);
        limpiarDatosAsociados(mascota);
    }

    // Se llama cuando alguien contacta sobre esta mascota (ver InternalMascotaController).
    //
    // Si ya hay un token vigente se reutiliza: antes cada llamada generaba uno nuevo e invalidaba el
    // anterior, así que quien pudiera llamar al endpoint dejaba sin efecto el enlace que el dueño real
    // ya había recibido por correo. Solo se emite uno nuevo cuando no hay o ya venció.
    //
    // Solo para reportes EXTRAVIADO: ahí quien recibe el correo de contacto es siempre el dueño real
    // (el que perdió la mascota). En un reporte ENCONTRADO, quien lo recibe es quien encontró la mascota,
    // no el dueño — no tiene autoridad para confirmar la reunificación de otra persona.
    public Mascota generarTokenConfirmacion(String id) {
        Mascota mascota = buscar(id);

        if (mascota.getEstado() == Estado.REUNIFICADO) {
            return mascota; // ya está cerrada, no tiene sentido emitir un token
        }
        if (mascota.getTipoReporte() != Estado.EXTRAVIADO) {
            return mascota; // ENCONTRADO (o sin tipoReporte): sin token, el correo sale sin botón
        }
        if (mascota.getTokenReunificacion() != null
                && mascota.getTokenReunificacionExpira() != null
                && mascota.getTokenReunificacionExpira().isAfter(LocalDateTime.now())) {
            return mascota; // el token vigente sigue sirviendo
        }

        mascota.setTokenReunificacion(UUID.randomUUID().toString());
        mascota.setTokenReunificacionExpira(LocalDateTime.now().plus(DURACION_TOKEN_CONFIRMACION));
        return mascotaRepository.save(mascota);
    }

    // Confirmación pública (sin JWT) desde el link del correo de contacto —
    // el token hace de autorización en vez de la sesión del dueño.
    public ResultadoConfirmacion confirmarReunificacion(String id, String token) {
        Optional<Mascota> opt = mascotaRepository.findById(id);
        if (opt.isEmpty()) {
            return ResultadoConfirmacion.TOKEN_INVALIDO;
        }
        Mascota mascota = opt.get();

        if (mascota.getEstado() == Estado.REUNIFICADO) {
            return ResultadoConfirmacion.YA_REUNIFICADA;
        }
        if (!tokensIguales(token, mascota.getTokenReunificacion())) {
            return ResultadoConfirmacion.TOKEN_INVALIDO;
        }
        if (mascota.getTokenReunificacionExpira() == null
                || mascota.getTokenReunificacionExpira().isBefore(LocalDateTime.now())) {
            return ResultadoConfirmacion.TOKEN_EXPIRADO;
        }

        /* el token se consume con una sola operación atómica: con dos clics casi a la vez (o un escáner antispam
         * que abre el enlace al mismo tiempo) solo uno gana, así no se publican dos reunificaciones ni se envían
         * dos correos de cierre; el otro encuentra el reporte ya cerrado */
        Mascota ganada = mongoTemplate.findAndModify(
                new Query(Criteria.where("_id").is(id)
                        .and("tokenReunificacion").is(token)
                        .and("estado").ne(Estado.REUNIFICADO)),
                new Update().set("estado", Estado.REUNIFICADO)
                        .unset("tokenReunificacion").unset("tokenReunificacionExpira"),
                FindAndModifyOptions.options().returnNew(true),
                Mascota.class);
        if (ganada == null) {
            return ResultadoConfirmacion.YA_REUNIFICADA;
        }
        log.info("Reunificacion confirmada por token: idMascota={}", id);
        publicarMascotaReunificada(ganada);
        return ResultadoConfirmacion.CONFIRMADA;
    }

    /* comparación en tiempo constante: no filtra cuántos caracteres del token coinciden */
    private boolean tokensIguales(String recibido, String guardado) {
        if (recibido == null || recibido.isBlank() || guardado == null) {
            return false;
        }
        return MessageDigest.isEqual(recibido.getBytes(StandardCharsets.UTF_8), guardado.getBytes(StandardCharsets.UTF_8));
    }

    public Page<MascotaResponseDTO> misMascotas(String usuarioId, Pageable pageable) {
        log.debug("Listando mascotas del usuario={}", usuarioId);
        return mascotaRepository.findByUsuarioId(usuarioId, paginacionSegura(pageable)).map(this::mapToResponseDTO);
    }

    public MascotaResponseDTO actualizarMascota(String id, ActualizarMascotaDTO dto, String usuarioId) {
        log.info("Actualizando mascota idMascota={} del usuario={}", id, usuarioId);

        Mascota mascota = buscar(id);
        exigirDueno(mascota, usuarioId, "No tienes permiso para actualizar esta mascota");

        /* si cambia lo que el público ve (foto, descripción o zona) el reporte vuelve a moderación: antes
         * un reporte aprobado podía cambiar su foto o su texto sin que nadie lo revisara */
        boolean cambiaContenidoPublico = false;

        if (dto.getUbicacion() != null) {
            GeoJsonPoint nueva = toGeoJsonPoint(dto.getUbicacion());
            /* el frontend solo conoce la ubicación redondeada: si la zona (2 decimales) es la misma, no se
             * considera un cambio ni se pisa la coordenada exacta guardada con una aproximada */
            if (!mismaZona(mascota.getUbicacion(), nueva)) {
                mascota.setUbicacion(nueva);
                cambiaContenidoPublico = true;
            }
        }
        if (dto.getComuna() != null) {
            mascota.setComuna(dto.getComuna());
        }
        if (dto.getDescripcion() != null && !Objects.equals(dto.getDescripcion(), mascota.getDescripcion())) {
            mascota.setDescripcion(dto.getDescripcion());
            cambiaContenidoPublico = true;
        }
        String fotoAnterior = null;
        if (dto.getFotografia() != null && !Objects.equals(dto.getFotografia(), mascota.getFotografia())) {
            validarFotografia(dto.getFotografia(), usuarioId);
            fotoAnterior = mascota.getFotografia();
            mascota.setFotografia(dto.getFotografia());
            cambiaContenidoPublico = true;
        }
        if (dto.getCaracteristicas() != null && !dto.getCaracteristicas().isEmpty()) {
            validarCaracteristicas(dto.getCaracteristicas());
            Map<String, Object> combinadas = new HashMap<>(mascota.getCaracteristicas());
            combinadas.putAll(dto.getCaracteristicas());
            if (combinadas.size() > MAX_CARACTERISTICAS) {
                throw new SolicitudInvalidaException("Un reporte no puede tener más de " + MAX_CARACTERISTICAS + " características");
            }
            mascota.setCaracteristicas(combinadas);
        }

        boolean vuelveAModeracion = cambiaContenidoPublico && estaAprobada(mascota);
        if (vuelveAModeracion) {
            mascota.setEstadoModeracion(EstadoModeracion.PENDIENTE);
            log.info("El reporte vuelve a moderación por cambios en su contenido público: id={}", id);
        }

        Mascota actualizada = mascotaRepository.save(mascota);
        log.info("Mascota actualizada: id={}", id);
        if (vuelveAModeracion) {
            /* deja de estar publicado: su alerta de zona sale del mapa hasta que se vuelva a aprobar */
            publicarMascotaRetirada(actualizada);
        }
        if (fotoAnterior != null) {
            /* la foto reemplazada ya no la usa este reporte: se borra del bucket si ningún otro la usa */
            borrarFotoSiNadieLaUsa(fotoAnterior, id);
        }
        return mapToResponseDTO(actualizada);
    }

    public MascotaResponseDTO cambiarEstado(String id, Estado nuevoEstado, String usuarioId) {
        log.info("Cambiando estado de mascota idMascota={} a {}", id, nuevoEstado);

        Mascota mascota = buscar(id);
        exigirDueno(mascota, usuarioId, "No tienes permiso para cambiar el estado");

        validarTransicion(mascota.getEstado(), nuevoEstado);
        mascota.setEstado(nuevoEstado);
        if (nuevoEstado == Estado.REUNIFICADO) {
            mascota.setTokenReunificacion(null);
            mascota.setTokenReunificacionExpira(null);
        }

        Mascota actualizada = mascotaRepository.save(mascota);
        log.info("Estado cambiado exitosamente: idMascota={}, estado={}", id, nuevoEstado);
        if (nuevoEstado == Estado.REUNIFICADO) {
            publicarMascotaReunificada(actualizada);
        }
        return mapToResponseDTO(actualizada);
    }

    public void eliminarMascota(String id, String usuarioId) {
        log.info("Eliminando mascota idMascota={} del usuario={}", id, usuarioId);

        Mascota mascota = buscar(id);
        exigirDueno(mascota, usuarioId, "No tienes permiso para eliminar esta mascota");

        mascotaRepository.deleteById(id);
        log.info("Mascota eliminada: idMascota={}", id);
        limpiarDatosAsociados(mascota);
    }

    /*
     * Al borrar un reporte no deben quedar datos huérfanos (regla del proyecto: permitir eliminar los datos
     * asociados): la foto se borra del bucket y se publica "mascota.eliminada" para que ms-alertas y ms-chat
     * limpien lo suyo. Ambas cosas son "lo mejor posible": el reporte ya se borró y no deben romper la petición.
     * ms-alertas (desactiva sus alertas) y ms-chat (borra conversaciones y correos) consumen ese evento.
     */
    private void limpiarDatosAsociados(Mascota mascota) {
        borrarFotoSiNadieLaUsa(mascota.getFotografia(), mascota.getIdMascota());
        publicarSinFallar(TIPO_EVENTO_MASCOTA_ELIMINADA, new MascotaEliminadaEvento(mascota.getIdMascota()),
                mascota.getIdMascota());
    }

    /* La clave de una foto solo la pide quien la sube, pero aun así se borra del bucket únicamente si ningún OTRO
     * reporte la referencia (como clave o como URL): borrar la de un reporte no debe dejar sin imagen a otro. */
    private void borrarFotoSiNadieLaUsa(String fotografia, String idMascotaExcluida) {
        List<String> formas = fotografiaService.formasDe(fotografia);
        if (formas.isEmpty()) {
            return; // relleno, texto ajeno o sin foto: nada que borrar del bucket
        }
        if (mascotaRepository.existsByFotografiaInAndIdMascotaNot(formas, idMascotaExcluida)) {
            log.info("La foto sigue en uso por otro reporte, no se borra del bucket (idMascota={})", idMascotaExcluida);
            return;
        }
        fotografiaService.eliminar(fotografia);
    }

    private void publicarMascotaRetirada(Mascota mascota) {
        publicarSinFallar(TIPO_EVENTO_MASCOTA_RETIRADA, new MascotaRetiradaEvento(mascota.getIdMascota()),
                mascota.getIdMascota());
    }

    private void validarTransicion(Estado estadoActual, Estado nuevoEstado) {
        if (estadoActual == Estado.REUNIFICADO) {
            throw new SolicitudInvalidaException("No se puede cambiar el estado de una mascota reunificada");
        }
        log.debug("Transición válida: {} -> {}", estadoActual, nuevoEstado);
    }

    private MascotaResponseDTO cambiarEstadoModeracion(String id, EstadoModeracion nuevoEstado) {
        Mascota mascota = buscar(id);
        if (mascota.getEstadoModeracion() != EstadoModeracion.PENDIENTE) {
            throw new SolicitudInvalidaException("Solo se pueden moderar reportes pendientes");
        }
        mascota.setEstadoModeracion(nuevoEstado);
        Mascota guardada = mascotaRepository.save(mascota);
        if (nuevoEstado == EstadoModeracion.APROBADA) {
            publicarMascotaReportada(guardada); // recién ahora aparece la alerta de zona en el mapa
        } else {
            publicarMascotaRetirada(guardada);  // por si ya había una alerta (reporte editado y luego rechazado)
        }
        return mapToResponseDTO(guardada);
    }

    private Mascota buscar(String id) {
        return mascotaRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Mascota no encontrada"));
    }

    /* Objects.equals: un documento histórico sin usuarioId daba NullPointerException con getUsuarioId().equals(...) */
    private void exigirDueno(Mascota mascota, String usuarioId, String mensaje) {
        if (usuarioId == null || !Objects.equals(mascota.getUsuarioId(), usuarioId)) {
            throw new AccesoDenegadoException(mensaje);
        }
    }

    private boolean estaAprobada(Mascota mascota) {
        return mascota.getEstadoModeracion() == null
                || mascota.getEstadoModeracion() == EstadoModeracion.APROBADA;
    }

    private void validarFotografia(String fotografia, String usuarioId) {
        if (fotografia != null && !fotografiaService.esPermitida(fotografia, usuarioId)) {
            throw new SolicitudInvalidaException(
                    "La fotografía debe ser una imagen subida con la URL firmada de la plataforma");
        }
    }

    /*
     * Las características son un mapa libre que el usuario escribe. Claves con "." o que empiezan con "$"
     * hacen fallar la escritura en Mongo (500 después de aceptar la petición) y valores anidados o enormes
     * inflan el documento. Se aceptan claves de hasta 50 caracteres y valores de texto (hasta 200), número o
     * booleano. El tope de 20 claves se aplica al mapa final al editar.
     */
    private void validarCaracteristicas(Map<String, Object> caracteristicas) {
        if (caracteristicas == null) {
            return;
        }
        if (caracteristicas.size() > MAX_CARACTERISTICAS) {
            throw new SolicitudInvalidaException("No se pueden enviar más de " + MAX_CARACTERISTICAS + " características");
        }
        for (Map.Entry<String, Object> entrada : caracteristicas.entrySet()) {
            String clave = entrada.getKey();
            if (clave == null || clave.isBlank() || clave.length() > LARGO_MAXIMO_CLAVE_CARACTERISTICA
                    || clave.contains(".") || clave.startsWith("$")) {
                throw new SolicitudInvalidaException("Nombre de característica inválido");
            }
            Object valor = entrada.getValue();
            if (valor == null) {
                continue;
            }
            if (!(valor instanceof String || valor instanceof Number || valor instanceof Boolean)) {
                throw new SolicitudInvalidaException(
                        "El valor de la característica '" + clave + "' debe ser texto, número o booleano");
            }
            if (valor instanceof String texto && texto.length() > LARGO_MAXIMO_VALOR_CARACTERISTICA) {
                throw new SolicitudInvalidaException(
                        "El valor de la característica '" + clave + "' supera los " + LARGO_MAXIMO_VALOR_CARACTERISTICA + " caracteres");
            }
        }
    }

    /* La descripción se guarda siempre como texto. Antes, si empezaba con "{" se interpretaba como JSON y se
     * mezclaba dentro del mapa: el usuario decidía la forma del documento sin pasar por ninguna validación. */
    private Map<String, Object> construirCaracteristicas(String descripcion, Map<String, Object> caracteristicas) {
        Map<String, Object> mapa = new HashMap<>();

        if (caracteristicas != null && !caracteristicas.isEmpty()) {
            mapa.putAll(caracteristicas);
        }
        if (descripcion != null && !descripcion.isBlank()) {
            mapa.put("descripcion", descripcion);
        }
        return mapa;
    }

    private MascotaResponseDTO mapToResponseDTO(Mascota mascota) {
        return MascotaResponseDTO.builder()
                .idMascota(mascota.getIdMascota())
                .nombre(mascota.getNombre())
                .tipoMascota(mascota.getTipoMascota())
                .fotografia(mascota.getFotografia())
                .estado(mascota.getEstado())
                // Los documentos históricos se consideran aprobados: ya estaban publicados.
                .estadoModeracion(mascota.getEstadoModeracion() != null
                        ? mascota.getEstadoModeracion()
                        : EstadoModeracion.APROBADA)
                // documentos creados antes de este campo (tipoReporte == null): si
                // todavía no fue reunificada, su tipo de reporte es su propio estado
                // actual; si ya fue reunificada no hay forma de recuperar el origen,
                // así que queda sin clasificar (null) en vez de inventar un dato.
                .tipoReporte(mascota.getTipoReporte() != null
                        ? mascota.getTipoReporte()
                        : (mascota.getEstado() != Estado.REUNIFICADO ? mascota.getEstado() : null))
                .ubicacion(toUbicacionDTO(mascota.getUbicacion()))
                .comuna(mascota.getComuna())
                .descripcion(mascota.getDescripcion())
                .caracteristicas(mascota.getCaracteristicas())
                .fecha(mascota.getFecha())
                .build();
    }

    private GeoJsonPoint toGeoJsonPoint(UbicacionDTO ubicacion) {
        if (ubicacion == null) {
            return null;
        }
        return new GeoJsonPoint(ubicacion.getLongitud(), ubicacion.getLatitud());
    }

    /* misma zona = mismas coordenadas redondeadas a 2 decimales (~1,1 km), que es lo que el público ve */
    private boolean mismaZona(GeoJsonPoint actual, GeoJsonPoint nueva) {
        if (actual == null || nueva == null) {
            return actual == nueva;
        }
        return redondear(actual.getX()) == redondear(nueva.getX()) && redondear(actual.getY()) == redondear(nueva.getY());
    }

    private UbicacionDTO toUbicacionDTO(GeoJsonPoint ubicacion) {
        if (ubicacion == null) {
            return null;
        }
        // Ubicación aproximada por privacidad: se redondea a 2 decimales
        // (~1,1 km de margen) antes de salir de este servicio. La coordenada
        // exacta solo vive en el documento de Mongo, nunca en una respuesta.
        return new UbicacionDTO(redondear(ubicacion.getY()), redondear(ubicacion.getX()));
    }

    private double redondear(double coordenada) {
        return Math.round(coordenada * 100.0) / 100.0;
    }
}
