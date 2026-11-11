package com.evolutionfit.modules.payments.dto;

import java.math.BigDecimal;

import com.evolutionfit.modules.payments.PaymentMethod;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Pago total de una membresía pendiente. El backend calcula el total; {@code expectedAmount} es el que vio
 * el usuario y debe coincidir. El descuento solo lo puede aplicar un ADMIN, con motivo.
 */
public record PaymentRequest(
        @NotNull(message = "Falta el identificador de la solicitud")
        @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
                message = "El identificador de la solicitud no es válido")
        String requestId,

        @NotNull(message = "El método de pago es obligatorio")
        PaymentMethod method,

        @Size(max = 120, message = "La referencia no puede superar 120 caracteres")
        String reference,

        @Size(max = 255, message = "Las notas no pueden superar 255 caracteres")
        String notes,

        @NotNull(message = "Falta el valor a cobrar")
        @DecimalMin(value = "0", message = "El valor no puede ser negativo")
        @Digits(integer = 10, fraction = 2, message = "El valor admite hasta 10 enteros y 2 decimales")
        BigDecimal expectedAmount,

        @DecimalMin(value = "0", message = "El descuento no puede ser negativo")
        @Digits(integer = 10, fraction = 2, message = "El descuento admite hasta 10 enteros y 2 decimales")
        BigDecimal discountAmount,

        @Size(max = 255, message = "El motivo del descuento no puede superar 255 caracteres")
        String discountReason) {
}
