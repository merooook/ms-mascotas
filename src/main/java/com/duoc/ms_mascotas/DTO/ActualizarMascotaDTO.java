package com.duoc.ms_mascotas.DTO;

import java.util.Map;

import com.duoc.ms_mascotas.model.Estado;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ActualizarMascotaDTO {

    private Estado estado;
    private UbicacionDTO ubicacion;

    @Size(max = 100, message = "La comuna no puede superar los 100 caracteres")
    private String comuna;

    @Size(max = 500, message = "La fotografía no puede superar los 500 caracteres")
    private String fotografia;

    @Size(max = 1000, message = "La descripción no puede superar los 1000 caracteres")
    private String descripcion;

    @Size(max = 20, message = "No se pueden enviar más de 20 características")
    private Map<String, Object> caracteristicas;
}