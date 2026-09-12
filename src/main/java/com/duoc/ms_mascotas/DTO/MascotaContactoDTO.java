package com.duoc.ms_mascotas.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Respuesta de GET /internal/mascotas/{id}/contacto — mismo contrato que
 * MascotaContactoDto en ms-alertas (sanos-y-salvos-ms-alertas/.../MascotasClient).
 * Nunca se expone al frontend.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MascotaContactoDTO {
    private String mascotaId;
    private String emailDestino;
    private String nombreMascota;
}
