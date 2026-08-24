package com.duoc.ms_mascotas.service;

import com.duoc.ms_mascotas.DTO.CrearMascotaDTO;
import com.duoc.ms_mascotas.DTO.ActualizarMascotaDTO;
import com.duoc.ms_mascotas.DTO.MascotaResponseDTO;
import com.duoc.ms_mascotas.factory.MascotaFactory;
import com.duoc.ms_mascotas.DTO.UbicacionDTO;
import com.duoc.ms_mascotas.model.Mascota;
import com.duoc.ms_mascotas.model.Estado;
import com.duoc.ms_mascotas.repository.MascotaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.geo.Point;
import org.springframework.stereotype.Service;
import jakarta.transaction.Transactional;
import java.util.Optional;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class MascotaService {

    private final MascotaRepository mascotaRepository;
    private final MascotaFactory mascotaFactory;

    /**
     * Crear una nueva mascota
     */
    public MascotaResponseDTO crearMascota(CrearMascotaDTO dto, String duenoId) {
        log.info("Creando mascota: nombre='{}', raza={}, para dueno={}",
                dto.getNombre(), dto.getRaza(), duenoId);

        Mascota mascota = mascotaFactory.crearMascota(dto.getEspecie());
        mascota.setNombre(dto.getNombre());
        mascota.setRaza(dto.getRaza());
        mascota.setPatron(dto.getPatron());
        mascota.setColor(dto.getColor());
        mascota.setFotografia(dto.getFotografia());
        mascota.setEstado(dto.getEstado());
        mascota.setUbicacion(toPoint(dto.getUbicacion()));
        mascota.setDescripcion(dto.getDescripcion());
        mascota.setSexo(dto.getSexo());
        mascota.setDuenoId(duenoId);
        
        Mascota guardada = mascotaRepository.save(mascota);
        log.info("Mascota creada exitosamente: id={}, nombre='{}'", guardada.getId(), guardada.getNombre());

        return mapToResponseDTO(guardada);
    }

    /**
     * Listar todas las mascotas (con filtro opcional de estado)
     */
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

    /**
     * Obtener mascota por ID
     */
    public Optional<MascotaResponseDTO> obtenerPorId(Long id) {
        log.debug("Buscando mascota por id={}", id);
        return mascotaRepository.findById(id).map(this::mapToResponseDTO);
    }

    /**
     * Obtener mis mascotas (del usuario autenticado)
     */
    public Page<MascotaResponseDTO> misMascotas(String duenoId, Pageable pageable) {
        log.debug("Listando mascotas del dueno={}", duenoId);

        Page<Mascota> mascotas = mascotaRepository.findByDuenoId(duenoId, pageable);
        return mascotas.map(this::mapToResponseDTO);
    }

    /**
     * Actualizar mascota (campos generales)
     */
    public MascotaResponseDTO actualizarMascota(Long id, ActualizarMascotaDTO dto, String duenoId) {
        log.info("Actualizando mascota id={} del dueno={}", id, duenoId);

        Mascota mascota = mascotaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Mascota no encontrada"));

        if (!mascota.getDuenoId().equals(duenoId)) {
            throw new IllegalArgumentException("No tienes permiso para actualizar esta mascota");
        }

        if (dto.getEstado() != null) {
            mascota.setEstado(dto.getEstado());
        }
        if (dto.getUbicacion() != null) {
            mascota.setUbicacion(toPoint(dto.getUbicacion()));
        }
        if (dto.getDescripcion() != null) {
            mascota.setDescripcion(dto.getDescripcion());
        }
        if (dto.getPatron() != null) {
            mascota.setPatron(dto.getPatron());
        }
        if (dto.getColor() != null) {
            mascota.setColor(dto.getColor());
        }
        if (dto.getFotografia() != null) {
            mascota.setFotografia(dto.getFotografia());
        }

        Mascota actualizada = mascotaRepository.save(mascota);
        log.info("Mascota actualizada: id={}", id);

        return mapToResponseDTO(actualizada);
    }

    /**
     * Cambiar estado de la mascota
     */
    public MascotaResponseDTO cambiarEstado(Long id, Estado nuevoEstado, String duenoId) {
        log.info("Cambiando estado de mascota id={} a {}", id, nuevoEstado);

        Mascota mascota = mascotaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Mascota no encontrada"));

        // Validar que sea el dueño
        if (!mascota.getDuenoId().equals(duenoId)) {
            throw new IllegalArgumentException("No tienes permiso para cambiar el estado");
        }

        // Validar transición de estado
        validarTransicion(mascota.getEstado(), nuevoEstado);

        mascota.setEstado(nuevoEstado);
        Mascota actualizada = mascotaRepository.save(mascota);

        log.info("Estado cambiado exitosamente: id={}, estado={}", id, nuevoEstado);

        return mapToResponseDTO(actualizada);
    }

    /**
     * Eliminar mascota
     */
    public void eliminarMascota(Long id, String duenoId) {
        log.info("Eliminando mascota id={} del dueno={}", id, duenoId);

        Mascota mascota = mascotaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Mascota no encontrada"));

        // Validar que sea el dueño
        if (!mascota.getDuenoId().equals(duenoId)) {
            throw new IllegalArgumentException("No tienes permiso para eliminar esta mascota");
        }

        mascotaRepository.deleteById(id);
        log.info("Mascota eliminada: id={}", id);
    }

    /**
     * Validar transiciones de estado permitidas
     */
    private void validarTransicion(Estado estadoActual, Estado nuevoEstado) {
        // REUNIFICADO no puede transicionar a otro estado
        if (estadoActual == Estado.REUNIFICADO) {
            throw new IllegalArgumentException("No se puede cambiar el estado de una mascota reunificada");
        }

        // Validaciones adicionales si es necesario
        log.debug("Transición válida: {} -> {}", estadoActual, nuevoEstado);
    }

    /**
     * Mapear Mascota a ResponseDTO
     */
    private MascotaResponseDTO mapToResponseDTO(Mascota mascota) {
        return MascotaResponseDTO.builder()
                .id(mascota.getId())
                .nombre(mascota.getNombre())
                .raza(mascota.getRaza())
                .patron(mascota.getPatron())
                .color(mascota.getColor())
                .fotografia(mascota.getFotografia())
                .estado(mascota.getEstado())
                .ubicacion(toUbicacionDTO(mascota.getUbicacion()))
                .descripcion(mascota.getDescripcion())
                .sexo(mascota.getSexo())
                .build();
    }

    private Point toPoint(UbicacionDTO ubicacion) {
        return new Point(ubicacion.getLongitud(), ubicacion.getLatitud());
    }

    private UbicacionDTO toUbicacionDTO(Point ubicacion) {
        if (ubicacion == null) {
            return null;
        }
        return new UbicacionDTO(ubicacion.getY(), ubicacion.getX());
    }
}
