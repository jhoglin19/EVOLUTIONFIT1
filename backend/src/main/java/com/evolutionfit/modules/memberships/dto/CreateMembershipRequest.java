package com.evolutionfit.modules.memberships.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Membresía nueva o renovación. Queda pendiente de pago (el pago total la activa, fase 5). */
public record CreateMembershipRequest(
        @NotNull(message = "El cliente es obligatorio")
        Long clientId,

        @NotNull(message = "El plan es obligatorio")
        Long planId,

        /** Opcional: inicio futuro. Si el cliente tiene una vigente, la nueva empieza al día siguiente del vencimiento. */
        LocalDate startDate,

        @Size(max = 2000, message = "Las notas no pueden superar 2000 caracteres")
        String notes) {
}
