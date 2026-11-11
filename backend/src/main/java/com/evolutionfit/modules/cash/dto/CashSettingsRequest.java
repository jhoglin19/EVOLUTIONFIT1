package com.evolutionfit.modules.cash.dto;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/** startDate NULL: la caja todavía no empieza. Solo se puede cambiar mientras no haya cierres. */
public record CashSettingsRequest(
        @NotNull(message = "La base es obligatoria")
        @DecimalMin(value = "0", message = "La base no puede ser negativa")
        @Digits(integer = 10, fraction = 2, message = "Base no válida")
        BigDecimal openingBase,
        LocalDate startDate,
        @NotEmpty(message = "Selecciona al menos un día de apertura")
        Set<DayOfWeek> openDays,
        /** null: no cambia. */
        Boolean closedOnHolidays) {
}
