package com.duoc.ms_mascotas.DTO;

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

    private String idMascota;
    private String usuarioId;
    private String nombre;
    private TipoMascota tipoMascota;
    private String color;
    private String fotografia;
    private Estado estado;
    private UbicacionDTO ubicacion;
    private String descripcion;
    private Map<String, Object> caracteristicas;
}