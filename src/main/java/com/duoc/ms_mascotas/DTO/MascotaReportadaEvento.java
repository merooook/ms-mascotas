package com.duoc.ms_mascotas.DTO;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Evento publicado cuando se crea una mascota con ubicación. ms-alertas lo
 * consume para crear automáticamente la Alerta geográfica correspondiente.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MascotaReportadaEvento implements Serializable {
    private String mascotaId;
    private Double latitud;
    private Double longitud;
}
