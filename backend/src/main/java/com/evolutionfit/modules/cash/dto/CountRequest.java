package com.evolutionfit.modules.cash.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Arqueo. expectedAdjustedCash es el esperado ajustado que vio recepción en el diálogo: si mientras tanto
 * hubo otro movimiento en efectivo, el backend no registra el arqueo y pide revisarlo de nuevo.
 */
public record CountRequest(
        @NotNull(message = "El efectivo contado es obligatorio")
        @DecimalMin(value = "0", message = "El efectivo contado no puede ser negativo")
        @Digits(integer = 10, fraction = 2, message = "Valor no válido")
        BigDecimal countedCash,
        @Size(max = 500, message = "La nota no puede superar 500 caracteres")
        String note,
        @NotNull(message = "Falta el efectivo esperado mostrado")
        BigDecimal expectedAdjustedCash) {
}
