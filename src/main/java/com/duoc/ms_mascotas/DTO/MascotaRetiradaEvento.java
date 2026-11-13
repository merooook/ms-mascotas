package com.duoc.ms_mascotas.DTO;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * "mascota.retirada": el reporte dejó de estar publicado sin haberse cerrado (lo rechazó un administrador, o
 * volvió a moderación porque su dueño cambió la foto, el texto o la zona). ms-alertas desactiva su alerta de zona;
 * si más tarde se aprueba, "mascota.reportada" la vuelve a crear con la ubicación vigente.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MascotaRetiradaEvento implements Serializable {
    private String mascotaId;
}
