package com.evolutionfit.modules.plans.dto;

import jakarta.validation.constraints.NotNull;

public record UpdatePlanStatusRequest(
        @NotNull(message = "El estado es obligatorio")
        Boolean active) {
}
