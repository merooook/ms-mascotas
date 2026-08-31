package com.duoc.ms_mascotas.service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.duoc.ms_mascotas.DTO.ActualizarMascotaDTO;
import com.duoc.ms_mascotas.DTO.CrearMascotaDTO;
import com.duoc.ms_mascotas.DTO.MascotaResponseDTO;
import com.duoc.ms_mascotas.DTO.UbicacionDTO;
import com.duoc.ms_mascotas.model.Estado;
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
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MascotaResponseDTO crearMascota(CrearMascotaDTO dto, String usuarioId) {
        log.info("Creando mascota: nombre='{}', tipo={}, usuarioId={}", dto.getNombre(), dto.getTipoMascota(), usuarioId);

        Mascota mascota = Mascota.builder()
                .usuarioId(usuarioId)
                .tipoMascota(dto.getTipoMascota())
                .nombre(dto.getNombre())
                .fotografia(dto.getFotografia())
                .estado(dto.getEstado() != null ? dto.getEstado() : Estado.EXTRAVIADO)
                .ubicacion(toGeoJsonPoint(dto.getUbicacion()))
                .fecha(LocalDateTime.now())
                .descripcion(dto.getDescripcion())
                .caracteristicas(parseCaracteristicas(dto.getDescripcion(), dto.getCaracteristicas()))
                .build();

        Mascota guardada = mascotaRepository.save(mascota);
        log.info("Mascota creada exitosamente: idMascota={}, nombre='{}'", guardada.getIdMascota(), guardada.getNombre());
        return mapToResponseDTO(guardada);
    }

    public Page<MascotaResponseDTO> listarTodas(Estado estado, Pageable pageable) {
        log.debug("Listando mascotas, estado={}", estado);

        Page<Mascota> mascotas;
        if (estado != null) {
            mascotas = mascotaRepository.findByEstado(estado, pageable);
        } else {
            mascotas = mascotaRepository.findAll(pageable);
        }

        return mascotas.map(this::mapToResponseDTO);
    }

    public Page<MascotaResponseDTO> listarConFiltros(String usuarioId, Estado estado, String tipoMascota,
            Pageable pageable) {
        log.debug("Listando mascotas filtradas: usuarioId={}, estado={}, tipoMascota={}",
            usuarioId, estado, tipoMascota);

        if (estado == null && tipoMascota == null) {
            return mascotaRepository.findAll(pageable).map(this::mapToResponseDTO);
        }

        Query query = new Query();
        if (estado != null) {
            query.addCriteria(Criteria.where("estado").is(estado));
        }
        if (tipoMascota != null) {
            query.addCriteria(Criteria.where("tipoMascota").is(tipoMascota));
        }

        long total = mongoTemplate.count(query, Mascota.class);
        List<Mascota> mascotas = mongoTemplate.find(query.with(pageable), Mascota.class);
        List<MascotaResponseDTO> dtos = mascotas.stream().map(this::mapToResponseDTO).toList();
        return new PageImpl<>(dtos, pageable, total);
    }

    public Optional<MascotaResponseDTO> obtenerPorId(String id) {
        log.debug("Buscando mascota por id={}", id);
        return mascotaRepository.findById(id).map(this::mapToResponseDTO);
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

        Mascota actualizada = mascotaRepository.save(mascota);
        log.info("Estado cambiado exitosamente: idMascota={}, estado={}", id, nuevoEstado);
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
                .usuarioId(mascota.getUsuarioId())
                .nombre(mascota.getNombre())
                .tipoMascota(mascota.getTipoMascota())
                .fotografia(mascota.getFotografia())
                .estado(mascota.getEstado())
                .ubicacion(toUbicacionDTO(mascota.getUbicacion()))
                .descripcion(mascota.getDescripcion())
                .caracteristicas(mascota.getCaracteristicas())
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
        return new UbicacionDTO(ubicacion.getY(), ubicacion.getX());
    }
}
