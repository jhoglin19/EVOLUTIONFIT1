package com.evolutionfit.modules.plans.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.evolutionfit.modules.plans.Plan;

public record PlanResponse(Long id,
                           String name,
                           String description,
                           int durationDays,
                           BigDecimal price,
                           boolean active,
                           LocalDateTime createdAt) {

    public static PlanResponse from(Plan plan) {
        return new PlanResponse(
                plan.getId(),
                plan.getName(),
                plan.getDescription(),
                plan.getDurationDays(),
                plan.getPrice(),
                plan.isActive(),
                plan.getCreatedAt());
    }
}
