package com.duoc.ms_mascotas.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.duoc.ms_mascotas.DTO.MascotaChatDTO;
import com.duoc.ms_mascotas.DTO.MascotaContactoDTO;
import com.duoc.ms_mascotas.model.Mascota;
import com.duoc.ms_mascotas.service.MascotaService;

import lombok.RequiredArgsConstructor;

/**
 * Endpoints internos, de servicio a servicio (ms-alertas y ms-chat) — nunca se exponen al frontend.
 *
 * Exigen el header X-Internal-Token con un secreto compartido (INTERNAL_API_TOKEN), validado en
 * SecurityConfig. Antes eran públicos y los protegía solo la red: quien alcanzara el servicio obtenía el
 * correo del dueño y un token con el que se podía cerrar cualquier reporte ajeno.
 */
@RestController
@RequestMapping("/internal/mascotas")
@RequiredArgsConstructor
public class InternalMascotaController {

    private final MascotaService mascotaService;

    /**
     * POST (no GET): puede emitir un token de reunificación, o sea tiene efecto secundario, y una lectura
     * no debería cambiar nada. Si ya hay un token vigente se reutiliza (ver MascotaService).
     */
    @PostMapping("/{id}/contacto")
    public ResponseEntity<MascotaContactoDTO> obtenerContacto(@PathVariable String id) {
        return mascotaService.obtenerParaContacto(id)
                .map(mascota -> {
                    Mascota conToken = mascotaService.generarTokenConfirmacion(mascota.getIdMascota());
                    return MascotaContactoDTO.builder()
                            .mascotaId(conToken.getIdMascota())
                            .emailDestino(conToken.getEmailContacto())
                            .nombreMascota(conToken.getNombre())
                            .tokenReunificacion(conToken.getTokenReunificacion())
                            .build();
                })
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Para ms-chat: quién es el dueño de un reporte y a qué correo avisarle de mensajes nuevos.
     * Reutiliza obtenerParaContacto, así que igual que el contacto solo responde por reportes aprobados
     * (uno pendiente o rechazado devuelve 404). Un reporte sin usuarioId (histórico) tampoco admite chat.
     */
    @GetMapping("/{id}/chat")
    public ResponseEntity<MascotaChatDTO> obtenerParaChat(@PathVariable String id) {
        return mascotaService.obtenerParaContacto(id)
                .filter(mascota -> mascota.getUsuarioId() != null && !mascota.getUsuarioId().isBlank())
                .map(mascota -> MascotaChatDTO.builder()
                        .mascotaId(mascota.getIdMascota())
                        .nombreMascota(mascota.getNombre())
                        .duenoSub(mascota.getUsuarioId())
                        .duenoEmail(mascota.getEmailContacto())
                        .build())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
