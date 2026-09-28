package com.duoc.ms_mascotas.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.duoc.ms_mascotas.DTO.ActualizarMascotaDTO;
import com.duoc.ms_mascotas.DTO.CambiarEstadoDTO;
import com.duoc.ms_mascotas.DTO.CrearMascotaDTO;
import com.duoc.ms_mascotas.DTO.MascotaResponseDTO;
import com.duoc.ms_mascotas.model.Estado;
import com.duoc.ms_mascotas.service.MascotaService;
import com.duoc.ms_mascotas.service.S3Service;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import java.util.Map;

@RestController
@RequestMapping("/mascotas")
@RequiredArgsConstructor
public class MascotaController {

    private final MascotaService mascotaService;
    private final S3Service s3Service;

    @GetMapping("/presigned-url")
    public ResponseEntity<Map<String, String>> generarUrlFirmada(
            @RequestHeader("X-User-Id") String usuarioId,
            @RequestParam String fileName,
            @RequestParam String contentType,
            @RequestParam long fileSize) {
        return ResponseEntity.ok(s3Service.generarUrlFirmada(fileName, contentType, fileSize));
    }

    @PostMapping
    public ResponseEntity<MascotaResponseDTO> crearMascota(
            @RequestHeader("X-User-Id") String usuarioId,
            @Valid @RequestBody CrearMascotaDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(mascotaService.crearMascota(dto, usuarioId));
    }

    @GetMapping
    public Page<MascotaResponseDTO> listarMascotas(
            @RequestHeader(value = "X-User-Id", required = false) String usuarioId,
            @RequestParam(required = false) Estado estado,
            @RequestParam(required = false) String tipoMascota,
            @RequestParam(required = false) String comuna,
            Pageable pageable) {
        return mascotaService.listarConFiltros(usuarioId, estado, tipoMascota, comuna, pageable);
    }

    // Sin X-User-Id obligatorio: el detalle es de acceso libre para invitados
    // (misma decisión de UX que el listado). El id no se usaba de todas
    // formas — el servicio no filtra por usuarioId acá.
    @GetMapping("/{id}")
    public ResponseEntity<MascotaResponseDTO> obtenerMascota(@PathVariable String id) {
        return mascotaService.obtenerPorId(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/mis-mascotas")
    public Page<MascotaResponseDTO> listarMisMascotas(
            @RequestHeader("X-User-Id") String usuarioId,
            Pageable pageable) {
        return mascotaService.misMascotas(usuarioId, pageable);
    }

    @PatchMapping("/{id}")
    public MascotaResponseDTO actualizarMascota(
            @RequestHeader("X-User-Id") String usuarioId,
            @PathVariable String id,
            @Valid @RequestBody ActualizarMascotaDTO dto) {
        return mascotaService.actualizarMascota(id, dto, usuarioId);
    }

    @PatchMapping("/{id}/estado")
    public MascotaResponseDTO cambiarEstado(
            @RequestHeader("X-User-Id") String usuarioId,
            @PathVariable String id,
            @Valid @RequestBody CambiarEstadoDTO dto) {
        return mascotaService.cambiarEstado(id, dto.getEstado(), usuarioId);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarMascota(
            @RequestHeader("X-User-Id") String usuarioId,
            @PathVariable String id) {
        mascotaService.eliminarMascota(id, usuarioId);
        return ResponseEntity.noContent().build();
    }
}