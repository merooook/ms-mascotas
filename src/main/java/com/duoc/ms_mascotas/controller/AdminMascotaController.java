package com.duoc.ms_mascotas.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.duoc.ms_mascotas.DTO.MascotaResponseDTO;
import com.duoc.ms_mascotas.model.EstadoModeracion;
import com.duoc.ms_mascotas.service.MascotaService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/mascotas/admin")
@RequiredArgsConstructor
public class AdminMascotaController {

    private final MascotaService mascotaService;

    @GetMapping
    public Page<MascotaResponseDTO> listar(
            @RequestParam EstadoModeracion estado,
            Pageable pageable) {
        return mascotaService.listarParaModeracion(estado, pageable);
    }

    @PatchMapping("/{id}/aprobar")
    public MascotaResponseDTO aprobar(@PathVariable String id) {
        return mascotaService.aprobarMascota(id);
    }

    @PatchMapping("/{id}/rechazar")
    public MascotaResponseDTO rechazar(@PathVariable String id) {
        return mascotaService.rechazarMascota(id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable String id) {
        mascotaService.eliminarComoAdministrador(id);
        return ResponseEntity.noContent().build();
    }
}
