package com.evolutionfit.modules.memberships.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Fechas que tendría una membresía nueva, sin guardarla. {@code scheduled} = renovación antes de vencer. */
public record MembershipPreviewResponse(String planName,
                                        BigDecimal price,
                                        LocalDate startDate,
                                        LocalDate endDate,
                                        boolean scheduled,
                                        LocalDate currentEndDate) {
}
