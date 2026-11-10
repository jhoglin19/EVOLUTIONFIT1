package com.evolutionfit.modules.memberships.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Membresía traída del sistema anterior: conserva su vencimiento real y no exige pago. */
public record MigratedMembershipRequest(
        @NotNull(message = "El cliente es obligatorio")
        Long clientId,

        @NotNull(message = "El plan es obligatorio")
        Long planId,

        @NotNull(message = "La fecha de inicio es obligatoria")
        LocalDate startDate,

        @NotNull(message = "La fecha de vencimiento es obligatoria")
        LocalDate endDate,

        /** Opcional: si no se envía se usa el precio actual del plan. */
        @DecimalMin(value = "0", message = "El precio no puede ser negativo")
        @Digits(integer = 10, fraction = 2, message = "El precio admite hasta 10 enteros y 2 decimales")
        BigDecimal price,

        @NotBlank(message = "La nota de migración es obligatoria")
        @Size(max = 255, message = "La nota de migración no puede superar 255 caracteres")
        String migrationNote,

        /** El cliente ya usó la congelación de esta membresía en el sistema anterior. */
        boolean freezeUsedInPreviousSystem,

        @Size(max = 2000, message = "Las notas no pueden superar 2000 caracteres")
        String notes) {
}
