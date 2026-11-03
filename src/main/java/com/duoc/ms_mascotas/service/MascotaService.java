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
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import com.duoc.ms_mascotas.DTO.ActualizarMascotaDTO;
import com.duoc.ms_mascotas.DTO.CrearMascotaDTO;
import com.duoc.ms_mascotas.DTO.MascotaEliminadaEvento;
import com.duoc.ms_mascotas.DTO.MascotaReportadaEvento;
import com.duoc.ms_mascotas.DTO.MascotaReunificadaEvento;
import com.duoc.ms_mascotas.DTO.MascotaResponseDTO;
import com.duoc.ms_mascotas.DTO.UbicacionDTO;
import com.duoc.ms_mascotas.exception.AccesoDenegadoException;
import com.duoc.ms_mascotas.exception.RecursoNoEncontradoException;
import com.duoc.ms_mascotas.messaging.SnsEventPublisher;
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
    private final SnsEventPublisher snsEventPublisher;
    private final FotografiaService fotografiaService;

    public static final String TIPO_EVENTO_MASCOTA_REPORTADA = "mascota.reportada";
    public static final String TIPO_EVENTO_MASCOTA_REUNIFICADA = "mascota.reunificada";
    public static final String TIPO_EVENTO_MASCOTA_ELIMINADA = "mascota.eliminada";

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

    public MascotaResponseDTO crearMascota(CrearMascotaDTO dto, String usuarioId) {
        log.info("Creando mascota: nombre='{}', tipo={}, usuarioId={}", dto.getNombre(), dto.getTipoMascota(), usuarioId);

        Estado estadoInicial = dto.getEstado() != null ? dto.getEstado() : Estado.EXTRAVIADO;
        if (estadoInicial == Estado.REUNIFICADO) {
            throw new IllegalArgumentException("El estado inicial debe ser EXTRAVIADO o ENCONTRADO");
        }
        validarFotografia(dto.getFotografia());
        validarCaracteristicas(dto.getCaracteristicas());

        Mascota mascota = Mascota.builder()
                .usuarioId(usuarioId)
                .emailContacto(dto.getEmailContacto())
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
        log.info("Mascota creada exitosamente: idMascota={}, nombre='{}'", guardada.getIdMascota(), guardada.getNombre());
        publicarMascotaReportada(guardada);
        return mapToResponseDTO(guardada);
    }

    // Solo si hay ubicación: sin lat/lng, ms-alertas no tiene con qué crear
    // la alerta geográfica de zona (radio de 3 km alrededor del punto).
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
     * La escritura en Mongo ya se hizo cuando se llega acá. Si SNS (o LocalStack) falla, antes la petición
     * terminaba en 500: la persona reintentaba y duplicaba el reporte, y la alerta de zona nunca se creaba.
     * Ahora se registra el error y la petición responde bien.
     * LÍMITE CONOCIDO: el evento perdido no se reintenta (haría falta un "outbox" que lo reenvíe).
     */
    private void publicarSinFallar(String tipoEvento, Object evento, String mascotaId) {
        try {
            snsEventPublisher.publicar(tipoEvento, evento);
            log.info("Evento {} publicado: idMascota={}", tipoEvento, mascotaId);
        } catch (RuntimeException e) {
            log.error("No se pudo publicar el evento {} de la mascota {}; el cambio ya quedó guardado",
                    tipoEvento, mascotaId, e);
        }
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
                throw new IllegalArgumentException("No se puede ordenar por '" + orden.getProperty()
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
        Criteria criterio = estadoModeracion == EstadoModeracion.APROBADA
                ? new Criteria().orOperator(
                        Criteria.where("estadoModeracion").is(EstadoModeracion.APROBADA),
                        Criteria.where("estadoModeracion").exists(false))
                : Criteria.where("estadoModeracion").is(estadoModeracion);
        Query query = new Query(criterio);
        long total = mongoTemplate.count(query, Mascota.class);
        List<MascotaResponseDTO> contenido = mongoTemplate.find(query.with(pageable), Mascota.class)
                .stream().map(this::mapToResponseDTO).toList();
        return new PageImpl<>(contenido, pageable, total);
    }

    public MascotaResponseDTO aprobarMascota(String id) {
        return cambiarEstadoModeracion(id, EstadoModeracion.APROBADA);
    }

    public MascotaResponseDTO rechazarMascota(String id) {
        return cambiarEstadoModeracion(id, EstadoModeracion.RECHAZADA);
    }

    public void eliminarComoAdministrador(String id) {
        Mascota mascota = buscar(id);
        mascotaRepository.delete(mascota);
        log.info("Mascota eliminada por administración: idMascota={}", id);
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

        mascota.setEstado(Estado.REUNIFICADO);
        mascota.setTokenReunificacion(null);
        mascota.setTokenReunificacionExpira(null);

        Mascota actualizada = mascotaRepository.save(mascota);
        log.info("Reunificacion confirmada por token: idMascota={}", id);
        publicarMascotaReunificada(actualizada);
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
        if (dto.getFotografia() != null && !Objects.equals(dto.getFotografia(), mascota.getFotografia())) {
            validarFotografia(dto.getFotografia());
            mascota.setFotografia(dto.getFotografia());
            cambiaContenidoPublico = true;
        }
        if (dto.getCaracteristicas() != null && !dto.getCaracteristicas().isEmpty()) {
            validarCaracteristicas(dto.getCaracteristicas());
            Map<String, Object> combinadas = new HashMap<>(mascota.getCaracteristicas());
            combinadas.putAll(dto.getCaracteristicas());
            if (combinadas.size() > MAX_CARACTERISTICAS) {
                throw new IllegalArgumentException("Un reporte no puede tener más de " + MAX_CARACTERISTICAS + " características");
            }
            mascota.setCaracteristicas(combinadas);
        }

        if (cambiaContenidoPublico && estaAprobada(mascota)) {
            mascota.setEstadoModeracion(EstadoModeracion.PENDIENTE);
            log.info("El reporte vuelve a moderación por cambios en su contenido público: id={}", id);
        }

        Mascota actualizada = mascotaRepository.save(mascota);
        log.info("Mascota actualizada: id={}", id);
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
     * PENDIENTE en otros repos: que ms-alertas y ms-chat consuman el evento (hoy se publica y nadie lo escucha).
     */
    private void limpiarDatosAsociados(Mascota mascota) {
        fotografiaService.eliminar(mascota.getFotografia());
        publicarSinFallar(TIPO_EVENTO_MASCOTA_ELIMINADA, new MascotaEliminadaEvento(mascota.getIdMascota()),
                mascota.getIdMascota());
    }

    private void validarTransicion(Estado estadoActual, Estado nuevoEstado) {
        if (estadoActual == Estado.REUNIFICADO) {
            throw new IllegalArgumentException("No se puede cambiar el estado de una mascota reunificada");
        }
        log.debug("Transición válida: {} -> {}", estadoActual, nuevoEstado);
    }

    private MascotaResponseDTO cambiarEstadoModeracion(String id, EstadoModeracion nuevoEstado) {
        Mascota mascota = buscar(id);
        if (mascota.getEstadoModeracion() != EstadoModeracion.PENDIENTE) {
            throw new IllegalArgumentException("Solo se pueden moderar reportes pendientes");
        }
        mascota.setEstadoModeracion(nuevoEstado);
        return mapToResponseDTO(mascotaRepository.save(mascota));
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

    private void validarFotografia(String fotografia) {
        if (fotografia != null && !fotografiaService.esPermitida(fotografia)) {
            throw new IllegalArgumentException(
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
            throw new IllegalArgumentException("No se pueden enviar más de " + MAX_CARACTERISTICAS + " características");
        }
        for (Map.Entry<String, Object> entrada : caracteristicas.entrySet()) {
            String clave = entrada.getKey();
            if (clave == null || clave.isBlank() || clave.length() > LARGO_MAXIMO_CLAVE_CARACTERISTICA
                    || clave.contains(".") || clave.startsWith("$")) {
                throw new IllegalArgumentException("Nombre de característica inválido");
            }
            Object valor = entrada.getValue();
            if (valor == null) {
                continue;
            }
            if (!(valor instanceof String || valor instanceof Number || valor instanceof Boolean)) {
                throw new IllegalArgumentException(
                        "El valor de la característica '" + clave + "' debe ser texto, número o booleano");
            }
            if (valor instanceof String texto && texto.length() > LARGO_MAXIMO_VALOR_CARACTERISTICA) {
                throw new IllegalArgumentException(
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
