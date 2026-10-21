package com.duoc.ms_mascotas.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.duoc.ms_mascotas.DTO.ActualizarMascotaDTO;
import com.duoc.ms_mascotas.DTO.CrearMascotaDTO;
import com.duoc.ms_mascotas.DTO.MascotaReportadaEvento;
import com.duoc.ms_mascotas.DTO.MascotaReunificadaEvento;
import com.duoc.ms_mascotas.DTO.MascotaResponseDTO;
import com.duoc.ms_mascotas.DTO.UbicacionDTO;
import com.duoc.ms_mascotas.messaging.SnsEventPublisher;
import com.duoc.ms_mascotas.model.Estado;
import com.duoc.ms_mascotas.model.EstadoModeracion;
import com.duoc.ms_mascotas.model.Mascota;
import com.duoc.ms_mascotas.repository.MascotaRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

@Slf4j
@Service
@RequiredArgsConstructor
public class MascotaService {

    private final MascotaRepository mascotaRepository;
    private final MongoTemplate mongoTemplate;
    private final SnsEventPublisher snsEventPublisher;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public static final String TIPO_EVENTO_MASCOTA_REPORTADA = "mascota.reportada";
    public static final String TIPO_EVENTO_MASCOTA_REUNIFICADA = "mascota.reunificada";

    private static final Duration DURACION_TOKEN_CONFIRMACION = Duration.ofDays(7);

    public enum ResultadoConfirmacion { CONFIRMADA, YA_REUNIFICADA, TOKEN_INVALIDO, TOKEN_EXPIRADO }

    public MascotaResponseDTO crearMascota(CrearMascotaDTO dto, String usuarioId) {
        log.info("Creando mascota: nombre='{}', tipo={}, usuarioId={}", dto.getNombre(), dto.getTipoMascota(), usuarioId);

        Mascota mascota = Mascota.builder()
                .usuarioId(usuarioId)
                .emailContacto(dto.getEmailContacto())
                .tipoMascota(dto.getTipoMascota())
                .nombre(dto.getNombre())
                .fotografia(dto.getFotografia())
                .estado(dto.getEstado() != null ? dto.getEstado() : Estado.EXTRAVIADO)
                .estadoModeracion(EstadoModeracion.PENDIENTE)
                .tipoReporte(dto.getEstado() != null ? dto.getEstado() : Estado.EXTRAVIADO)
                .ubicacion(toGeoJsonPoint(dto.getUbicacion()))
                .comuna(dto.getComuna())
                .fecha(LocalDateTime.now())
                .descripcion(dto.getDescripcion())
                .caracteristicas(parseCaracteristicas(dto.getDescripcion(), dto.getCaracteristicas()))
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

        snsEventPublisher.publicar(TIPO_EVENTO_MASCOTA_REPORTADA, evento);
        log.info("Evento mascota.reportada publicado: idMascota={}", mascota.getIdMascota());
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

        snsEventPublisher.publicar(TIPO_EVENTO_MASCOTA_REUNIFICADA, evento);
        log.info("Evento mascota.reunificada publicado: idMascota={}", mascota.getIdMascota());
    }

    public Page<MascotaResponseDTO> listarTodas(Estado estado, Pageable pageable) {
        log.debug("Listando mascotas, estado={}", estado);
        return listarConFiltros(null, estado, null, null, pageable);
    }

    public Page<MascotaResponseDTO> listarConFiltros(String usuarioId, Estado estado, String tipoMascota,
            String comuna, Pageable pageable) {
        log.debug("Listando mascotas filtradas: usuarioId={}, estado={}, tipoMascota={}, comuna={}",
            usuarioId, estado, tipoMascota, comuna);

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
        List<Mascota> mascotas = mongoTemplate.find(query.with(pageable), Mascota.class);
        List<MascotaResponseDTO> dtos = mascotas.stream().map(this::mapToResponseDTO).toList();
        return new PageImpl<>(dtos, pageable, total);
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

    // Para el endpoint interno que consume ms-alertas (GET
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
        Mascota mascota = mascotaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Mascota no encontrada"));
        mascotaRepository.delete(mascota);
        log.info("Mascota eliminada por administración: idMascota={}", id);
    }

    // Se llama cada vez que alguien contacta sobre esta mascota (ver
    // InternalMascotaController) — genera un token nuevo y de paso invalida
    // cualquier token anterior sin usar, así solo el último correo de
    // contacto puede confirmar la reunificación.
    //
    // Solo para reportes EXTRAVIADO: ahí quien recibe el correo de contacto
    // es siempre el dueño real (el que perdió la mascota). En un reporte
    // ENCONTRADO, quien lo recibe es quien encontró la mascota, no el dueño
    // — no tiene autoridad para confirmar la reunificación de otra persona.
    public Mascota generarTokenConfirmacion(String id) {
        Mascota mascota = mascotaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Mascota no encontrada"));

        if (mascota.getEstado() == Estado.REUNIFICADO) {
            return mascota; // ya está cerrada, no tiene sentido emitir un token
        }
        if (mascota.getTipoReporte() != Estado.EXTRAVIADO) {
            return mascota; // ENCONTRADO (o sin tipoReporte): sin token, el correo sale sin botón
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
        if (token == null || token.isBlank() || !token.equals(mascota.getTokenReunificacion())) {
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

    public Page<MascotaResponseDTO> misMascotas(String usuarioId, Pageable pageable) {
        log.debug("Listando mascotas del usuario={}", usuarioId);
        return mascotaRepository.findByUsuarioId(usuarioId, pageable).map(this::mapToResponseDTO);
    }

    public MascotaResponseDTO actualizarMascota(String id, ActualizarMascotaDTO dto, String usuarioId) {
        log.info("Actualizando mascota idMascota={} del usuario={}", id, usuarioId);

        Mascota mascota = mascotaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Mascota no encontrada"));

        if (!mascota.getUsuarioId().equals(usuarioId)) {
            throw new IllegalArgumentException("No tienes permiso para actualizar esta mascota");
        }

        if (dto.getEstado() != null) {
            mascota.setEstado(dto.getEstado());
        }
        if (dto.getUbicacion() != null) {
            mascota.setUbicacion(toGeoJsonPoint(dto.getUbicacion()));
        }
        if (dto.getComuna() != null) {
            mascota.setComuna(dto.getComuna());
        }
        if (dto.getDescripcion() != null) {
            mascota.setDescripcion(dto.getDescripcion());
        }
        if (dto.getFotografia() != null) {
            mascota.setFotografia(dto.getFotografia());
        }
        if (dto.getCaracteristicas() != null && !dto.getCaracteristicas().isEmpty()) {
            mascota.getCaracteristicas().putAll(dto.getCaracteristicas());
        }

        Mascota actualizada = mascotaRepository.save(mascota);
        log.info("Mascota actualizada: id={}", id);
        return mapToResponseDTO(actualizada);
    }

    public MascotaResponseDTO cambiarEstado(String id, Estado nuevoEstado, String usuarioId) {
        log.info("Cambiando estado de mascota idMascota={} a {}", id, nuevoEstado);

        Mascota mascota = mascotaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Mascota no encontrada"));

        if (!mascota.getUsuarioId().equals(usuarioId)) {
            throw new IllegalArgumentException("No tienes permiso para cambiar el estado");
        }

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

        Mascota mascota = mascotaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Mascota no encontrada"));

        if (!mascota.getUsuarioId().equals(usuarioId)) {
            throw new IllegalArgumentException("No tienes permiso para eliminar esta mascota");
        }

        mascotaRepository.deleteById(id);
        log.info("Mascota eliminada: idMascota={}", id);
    }

    private void validarTransicion(Estado estadoActual, Estado nuevoEstado) {
        if (estadoActual == Estado.REUNIFICADO) {
            throw new IllegalArgumentException("No se puede cambiar el estado de una mascota reunificada");
        }
        log.debug("Transición válida: {} -> {}", estadoActual, nuevoEstado);
    }

    private MascotaResponseDTO cambiarEstadoModeracion(String id, EstadoModeracion nuevoEstado) {
        Mascota mascota = mascotaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Mascota no encontrada"));
        if (mascota.getEstadoModeracion() != EstadoModeracion.PENDIENTE) {
            throw new IllegalArgumentException("Solo se pueden moderar reportes pendientes");
        }
        mascota.setEstadoModeracion(nuevoEstado);
        return mapToResponseDTO(mascotaRepository.save(mascota));
    }

    private boolean estaAprobada(Mascota mascota) {
        return mascota.getEstadoModeracion() == null
                || mascota.getEstadoModeracion() == EstadoModeracion.APROBADA;
    }

    private Map<String, Object> parseCaracteristicas(String descripcion, Map<String, Object> caracteristicas) {
        Map<String, Object> mapa = new HashMap<>();

        if (caracteristicas != null && !caracteristicas.isEmpty()) {
            mapa.putAll(caracteristicas);
        }

        if (descripcion != null && !descripcion.isBlank()) {
            try {
                if (descripcion.trim().startsWith("{")) {
                    Map<String, Object> parsed = objectMapper.readValue(descripcion, new TypeReference<>() {});
                    mapa.putAll(parsed);
                } else {
                    mapa.put("descripcion", descripcion);
                }
            } catch (JsonProcessingException e) {
                mapa.put("descripcion", descripcion);
            }
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
                .fecha(mascota.getFecha()) // ###################################
                .build();
    }

    private GeoJsonPoint toGeoJsonPoint(UbicacionDTO ubicacion) {
        if (ubicacion == null) {
            return null;
        }
        return new GeoJsonPoint(ubicacion.getLongitud(), ubicacion.getLatitud());
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
