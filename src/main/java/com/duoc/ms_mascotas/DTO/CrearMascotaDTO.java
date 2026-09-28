package com.duoc.ms_mascotas.DTO;

import java.util.Map;

import com.duoc.ms_mascotas.model.Estado;
import com.duoc.ms_mascotas.model.TipoMascota;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CrearMascotaDTO {

    @NotNull(message = "El tipo de mascota es requerido")
    private TipoMascota tipoMascota;

    @Size(max = 100, message = "El nombre no puede superar los 100 caracteres")
    private String nombre;

    @Size(max = 500, message = "La fotografía no puede superar los 500 caracteres")
    private String fotografia;

    @NotNull(message = "El estado es requerido")
    private Estado estado;

    private UbicacionDTO ubicacion;

    @NotBlank(message = "La comuna es requerida")
    @Size(max = 100, message = "La comuna no puede superar los 100 caracteres")
    private String comuna;

    @Size(max = 1000, message = "La descripción no puede superar los 1000 caracteres")
    private String descripcion;

    @Size(max = 20, message = "No se pueden enviar más de 20 características")
    private Map<String, Object> caracteristicas;

    // Correo de quien reporta — necesario para que ms-alertas pueda contactarlo
    // después. Nunca se devuelve en la respuesta pública (ver MascotaResponseDTO).
    @NotBlank(message = "El correo de contacto es requerido")
    @Email(message = "El correo de contacto no es válido")
    @Size(max = 150, message = "El correo de contacto no puede superar los 150 caracteres")
    private String emailContacto;
}