package com.evolutionfit.modules.memberships.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Motivo obligatorio de una anulación o cancelación. */
public record ReasonRequest(
        @NotBlank(message = "El motivo es obligatorio")
        @Size(max = 255, message = "El motivo no puede superar 255 caracteres")
        String reason) {
}
