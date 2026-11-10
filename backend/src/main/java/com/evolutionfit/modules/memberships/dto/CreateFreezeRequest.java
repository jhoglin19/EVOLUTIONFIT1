package com.evolutionfit.modules.memberships.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** El máximo de días (30) es configurable y lo valida el servicio. */
public record CreateFreezeRequest(
        @NotNull(message = "La fecha de inicio es obligatoria")
        LocalDate startDate,

        @NotNull(message = "Los días son obligatorios")
        @Min(value = 1, message = "La congelación debe ser de al menos 1 día")
        Integer days,

        @NotBlank(message = "El motivo es obligatorio")
        @Size(max = 255, message = "El motivo no puede superar 255 caracteres")
        String reason) {
}
