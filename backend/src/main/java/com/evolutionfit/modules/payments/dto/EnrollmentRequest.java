package com.evolutionfit.modules.payments.dto;

import java.time.LocalDate;

import com.evolutionfit.modules.clients.dto.ClientRequest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Inscripción o renovación en un solo paso: cliente existente ({@code clientId}) o nuevo ({@code client}),
 * plan, y pago total. Todo en una transacción: si algo falla, no queda nada a medias.
 */
public record EnrollmentRequest(
        Long clientId,

        @Valid
        ClientRequest client,

        @NotNull(message = "El plan es obligatorio")
        Long planId,

        LocalDate startDate,

        @Size(max = 2000, message = "Las notas no pueden superar 2000 caracteres")
        String notes,

        @NotNull(message = "Faltan los datos del pago")
        @Valid
        PaymentRequest payment) {
}
