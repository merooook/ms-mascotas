package com.duoc.ms_mascotas.DTO;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Evento publicado cuando un reporte pasa a REUNIFICADO. ms-alertas lo
 * consume para avisarle por correo a quien creó ESE reporte (dueño si era
 * EXTRAVIADO, quien la encontró si era ENCONTRADO) — cada reporte se
 * notifica a sí mismo, nunca se cruza con el otro.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MascotaReunificadaEvento implements Serializable {
    private String mascotaId;
    private String nombreMascota;
    private String emailContacto;
}
