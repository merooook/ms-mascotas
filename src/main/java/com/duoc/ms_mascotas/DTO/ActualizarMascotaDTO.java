package com.duoc.ms_mascotas.DTO;

import java.util.Map;

import com.duoc.ms_mascotas.model.Estado;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ActualizarMascotaDTO {

    private Estado estado;
    private String ubicacion;
    private String color;
    private String fotografia;
    private String descripcion;
    private Map<String, Object> caracteristicas;
}