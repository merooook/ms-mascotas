package com.duoc.ms_mascotas.DTO;

import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/*
 * Edición de un reporte. A propósito NO trae "estado": el estado cambia solo por PATCH /{id}/estado,
 * que valida la transición, publica el evento de reunificación y limpia el token. Antes un
 * {"estado":"REUNIFICADO"} acá saltaba todo eso (la alerta quedaba activa y no salía el correo de cierre)
 * y un {"estado":"EXTRAVIADO"} reabría un reporte ya reunificado.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ActualizarMascotaDTO {

    @Valid
    private UbicacionDTO ubicacion;

    @Size(max = 100, message = "La comuna no puede superar los 100 caracteres")
    @Pattern(regexp = "^[\\P{Cntrl}]*$", message = "La comuna no puede tener saltos de línea ni caracteres de control")
    private String comuna;

    @Size(max = 500, message = "La fotografía no puede superar los 500 caracteres")
    private String fotografia;

    @Size(max = 1000, message = "La descripción no puede superar los 1000 caracteres")
    @Pattern(regexp = "^[\\P{Cntrl}\\n\\t]*$", message = "La descripción no puede tener caracteres de control")
    private String descripcion;

    @Size(max = 20, message = "No se pueden enviar más de 20 características")
    private Map<String, Object> caracteristicas;
}
