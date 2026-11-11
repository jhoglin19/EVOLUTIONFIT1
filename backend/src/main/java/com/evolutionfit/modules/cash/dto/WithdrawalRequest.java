package com.evolutionfit.modules.cash.dto;

import java.math.BigDecimal;

import com.evolutionfit.modules.cash.WithdrawalCategory;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Retiro de efectivo para gastos del gimnasio. requestId evita registrarlo dos veces con un doble clic. */
public record WithdrawalRequest(
        @NotBlank(message = "Falta el identificador de la solicitud")
        @Size(max = 36, message = "Identificador de solicitud no válido")
        String requestId,
        @NotNull(message = "El monto es obligatorio")
        @DecimalMin(value = "0.01", message = "El monto debe ser mayor que cero")
        @Digits(integer = 10, fraction = 2, message = "Monto no válido")
        BigDecimal amount,
        @NotNull(message = "La categoría es obligatoria")
        WithdrawalCategory category,
        @NotBlank(message = "El concepto es obligatorio")
        @Size(max = 255, message = "El concepto no puede superar 255 caracteres")
        String concept) {
}
