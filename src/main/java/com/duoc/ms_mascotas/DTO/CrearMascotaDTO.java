package com.duoc.ms_mascotas.DTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import com.duoc.ms_mascotas.model.Estado;
import com.duoc.ms_mascotas.model.Raza;
import com.duoc.ms_mascotas.model.Patron;
import com.duoc.ms_mascotas.model.Color;
import com.duoc.ms_mascotas.model.Sexo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CrearMascotaDTO {

    @NotBlank(message = "La especie es requerida")
    private String especie;
    
    @NotBlank(message = "El nombre de la mascota no puede estar vacío")
    private String nombre;
    
    private Raza raza;
    
    @NotNull(message = "El patrón es requerido")
    private Patron patron;
    
    @NotNull(message = "El color es requerido")
    private Color color;
    
    @NotNull(message = "El estado es requerido")
    private Estado estado;
    
    @NotNull(message = "La ubicación es requerida")
    private UbicacionDTO ubicacion;
    
    @NotBlank(message = "La descripción no puede estar vacía")
    private String descripcion;
    
    @NotNull(message = "El sexo es requerido")
    private Sexo sexo;
    
    private String fotografia;
}