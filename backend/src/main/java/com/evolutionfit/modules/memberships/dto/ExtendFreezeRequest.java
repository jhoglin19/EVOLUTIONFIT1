package com.evolutionfit.modules.memberships.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** {@code totalDays} es la nueva duración total de la congelación (no los días a agregar). */
public record ExtendFreezeRequest(
        @NotNull(message = "El total de días es obligatorio")
        @Min(value = 2, message = "El total de días debe ser mayor que el actual")
        Integer totalDays,

        @NotBlank(message = "El motivo es obligatorio")
        @Size(max = 255, message = "El motivo no puede superar 255 caracteres")
        String reason) {
}
