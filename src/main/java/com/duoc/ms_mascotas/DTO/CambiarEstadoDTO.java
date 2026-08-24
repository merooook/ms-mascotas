package com.duoc.ms_mascotas.DTO;

import com.duoc.ms_mascotas.model.Estado;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CambiarEstadoDTO {

    @NotNull(message = "El estado es requerido")
    private Estado estado;
}
