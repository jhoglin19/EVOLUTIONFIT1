package com.evolutionfit.modules.clients.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateClientStatusRequest(
        @NotNull(message = "El estado es obligatorio")
        Boolean active) {
}
