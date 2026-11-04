package com.duoc.ms_mascotas.DTO;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Evento "mascota.eliminada": se publica al borrar un reporte para que quien guardó algo asociado
 * (alertas de zona en ms-alertas, conversaciones en ms-chat) pueda limpiarlo. Solo lleva el id.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MascotaEliminadaEvento implements Serializable {
    private String mascotaId;
}
