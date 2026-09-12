package com.duoc.ms_mascotas.DTO;

import java.util.Map;

import com.duoc.ms_mascotas.model.Estado;
import com.duoc.ms_mascotas.model.TipoMascota;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CrearMascotaDTO {

    @NotNull(message = "El tipo de mascota es requerido")
    private TipoMascota tipoMascota;

    private String nombre;
    private String fotografia;

    @NotNull(message = "El estado es requerido")
    private Estado estado;

    private UbicacionDTO ubicacion;
    private String descripcion;
    private Map<String, Object> caracteristicas;

    // Correo de quien reporta — necesario para que ms-alertas pueda contactarlo
    // después. Nunca se devuelve en la respuesta pública (ver MascotaResponseDTO).
    @NotBlank(message = "El correo de contacto es requerido")
    @Email(message = "El correo de contacto no es válido")
    private String emailContacto;
}