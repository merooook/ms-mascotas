package com.duoc.ms_mascotas.DTO;

import com.duoc.ms_mascotas.model.Estado;
import com.duoc.ms_mascotas.model.Patron;
import com.duoc.ms_mascotas.model.Color;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ActualizarMascotaDTO {
    
    private Estado estado; 
    private UbicacionDTO ubicacion; 
    private Patron patron;  
    private Color color;  
    private String fotografia;  
    private String descripcion;
}