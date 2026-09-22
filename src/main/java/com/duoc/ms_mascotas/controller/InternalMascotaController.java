package com.duoc.ms_mascotas.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.duoc.ms_mascotas.DTO.MascotaContactoDTO;
import com.duoc.ms_mascotas.service.MascotaService;

import lombok.RequiredArgsConstructor;

/**
 * Endpoint interno consumido solo por ms-alertas (MascotasClient) para resolver
 * el destinatario del flujo de Contacto — nunca se expone al frontend.
 *
 * Sin autenticación JWT porque la llamada de ms-alertas no trae ninguna (ver
 * ContactoServiceImpl/MascotasClient, RestClient sin Authorization). Su
 * protección real hoy es de red: en AWS, ms-mascotas solo debería aceptar
 * tráfico desde ms-alertas y el BFF. OJO: la tabla de Security Groups del
 * proyecto (CLAUDE.md) hoy solo abre SG-Mascotas a SG-BFF, no a SG-Alertas —
 * falta sumar esa regla o esta llamada fallará en AWS aunque funcione en local.
 */
@RestController
@RequestMapping("/internal/mascotas")
@RequiredArgsConstructor
public class InternalMascotaController {

    private final MascotaService mascotaService;

    @GetMapping("/{id}/contacto")
    public ResponseEntity<MascotaContactoDTO> obtenerContacto(@PathVariable String id) {
        return mascotaService.obtenerParaContacto(id)
                .map(mascota -> MascotaContactoDTO.builder()
                        .mascotaId(mascota.getIdMascota())
                        .emailDestino(mascota.getEmailContacto())
                        .nombreMascota(mascota.getNombre())
                        .build())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
