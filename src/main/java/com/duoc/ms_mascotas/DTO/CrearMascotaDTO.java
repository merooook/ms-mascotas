package com.duoc.ms_mascotas.DTO;

import java.util.Map;

import com.duoc.ms_mascotas.model.Estado;
import com.duoc.ms_mascotas.model.TipoMascota;
import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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
    @Pattern(regexp = "^[\\P{Cntrl}]*$", message = "El nombre no puede tener saltos de línea ni caracteres de control")
    private String nombre;

    @Size(max = 500, message = "La fotografía no puede superar los 500 caracteres")
    private String fotografia;

    @NotNull(message = "El estado es requerido")
    private Estado estado;

    // @Valid: sin esto los @NotNull y los rangos de UbicacionDTO nunca se ejecutaban
    // (latitud nula daba NullPointerException y 999 llegaba hasta Mongo)
    @Valid
    private UbicacionDTO ubicacion;

    @NotBlank(message = "La comuna es requerida")
    @Size(max = 100, message = "La comuna no puede superar los 100 caracteres")
    @Pattern(regexp = "^[\\P{Cntrl}]*$", message = "La comuna no puede tener saltos de línea ni caracteres de control")
    private String comuna;

    @Size(max = 1000, message = "La descripción no puede superar los 1000 caracteres")
    @Pattern(regexp = "^[\\P{Cntrl}\\n\\t]*$", message = "La descripción no puede tener caracteres de control")
    private String descripcion;

    @Size(max = 20, message = "No se pueden enviar más de 20 características")
    private Map<String, Object> caracteristicas;

    // YA NO SE USA: el correo de contacto sale del id_token verificado (header X-User-Email que arma el BFF junto
    // con el secreto de servicio), no de lo que escriba quien reporta. Antes cualquiera podía registrar el correo de
    // un tercero y hacer que la plataforma le escribiera desde su cuenta oficial. Se acepta para no romper clientes
    // viejos, pero se ignora.
    @Email(message = "El correo de contacto no es válido")
    @Size(max = 150, message = "El correo de contacto no puede superar los 150 caracteres")
    private String emailContacto;

    // Un reporte nace EXTRAVIADO o ENCONTRADO; crearlo ya REUNIFICADO ensuciaba las estadísticas de
    // efectividad (tipoReporte=REUNIFICADO) y se saltaba el flujo de cierre.
    @JsonIgnore
    @AssertTrue(message = "El estado inicial debe ser EXTRAVIADO o ENCONTRADO")
    public boolean isEstadoInicialValido() {
        return estado != Estado.REUNIFICADO;
    }
}
