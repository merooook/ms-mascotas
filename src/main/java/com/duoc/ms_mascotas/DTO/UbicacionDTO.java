package com.duoc.ms_mascotas.DTO;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UbicacionDTO {
    
    @NotNull(message = "La latitud es requerida")
    private Double latitud;
    
    @NotNull(message = "La longitud es requerida")
    private Double longitud;
}
