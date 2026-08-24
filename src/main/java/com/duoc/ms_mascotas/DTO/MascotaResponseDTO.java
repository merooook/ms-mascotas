package com.duoc.ms_mascotas.DTO;

import com.duoc.ms_mascotas.model.Estado;
import com.duoc.ms_mascotas.model.Raza;
import com.duoc.ms_mascotas.model.Patron;

import com.duoc.ms_mascotas.model.Color;
import com.duoc.ms_mascotas.model.Sexo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MascotaResponseDTO {
    
    private Long id;
    private String nombre;  
    private Raza raza;  
    private Patron patron;  
    private Color color; 
    private String fotografia; 
    private Estado estado; 
    private UbicacionDTO ubicacion;  
    private String descripcion;  
    private Sexo sexo;
}