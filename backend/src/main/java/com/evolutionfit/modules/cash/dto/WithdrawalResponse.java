package com.evolutionfit.modules.cash.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.evolutionfit.modules.cash.WithdrawalCategory;

public record WithdrawalResponse(Long id, BigDecimal amount, WithdrawalCategory category, String categoryLabel,
                                 String concept, LocalDateTime occurredAt, LocalDate cashDate, String registeredBy,
                                 boolean voided, LocalDateTime voidedAt, String voidReason) {
}
