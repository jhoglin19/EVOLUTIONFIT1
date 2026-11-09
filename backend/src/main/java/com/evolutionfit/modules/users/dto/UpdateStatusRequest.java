package com.evolutionfit.modules.users.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateStatusRequest(
        @NotNull(message = "El estado es obligatorio")
        Boolean active) {
}
