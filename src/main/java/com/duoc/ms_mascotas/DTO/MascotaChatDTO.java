package com.duoc.ms_mascotas.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Respuesta de GET /internal/mascotas/{id}/chat — lo consume solo ms-chat para saber
 * con quién iniciar la conversación. Nunca se expone al frontend: el sub del dueño es
 * un identificador interno y por eso tampoco aparece en MascotaResponseDTO.
 *
 * duenoEmail es el correo de contacto que el dueño ya dejó en el reporte; ms-chat lo usa solo
 * para avisarle que tiene un mensaje nuevo (sin incluir el contenido del mensaje).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MascotaChatDTO {
    private String mascotaId;
    private String nombreMascota;
    private String duenoSub;
    private String duenoEmail;
}
