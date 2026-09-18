package com.duoc.ms_mascotas.DTO;

import java.time.LocalDateTime;
import java.util.Map;

import com.duoc.ms_mascotas.model.Estado;
import com.duoc.ms_mascotas.model.TipoMascota;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MascotaResponseDTO {

    // usuarioId se saca a propósito: es el identificador del dueño y no debe
    // exponerse en respuestas públicas (regla de minimización de datos del
    // proyecto). emailContacto tampoco se incluye aquí nunca — solo lo
    // devuelve el endpoint interno /internal/mascotas/{id}/contacto.
    private String idMascota;
    private String nombre;
    private TipoMascota tipoMascota;
    private String fotografia;
    private Estado estado;
    private UbicacionDTO ubicacion;
    private String comuna;
    private String descripcion;
    private Map<String, Object> caracteristicas;
    private LocalDateTime fecha; // ###################################
}