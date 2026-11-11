package com.evolutionfit.modules.cash.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.evolutionfit.modules.cash.CashClosingStatus;
import com.evolutionfit.modules.cash.ClosingMode;

public record CashClosingResponse(Long id, LocalDate cashDate, LocalDateTime windowStart, LocalDateTime windowEnd,
                                  CashClosingStatus status, ClosingMode closingMode, boolean wasOpenDay,
                                  boolean hasCashActivity, BigDecimal openingBase, BigDecimal cashIncome,
                                  BigDecimal cashVoided, BigDecimal cashWithdrawals, BigDecimal expectedCash,
                                  LocalDateTime closedAt, BigDecimal postCutIncome, BigDecimal postCutOutflow,
                                  BigDecimal adjustedExpectedCash, BigDecimal countedCash, BigDecimal difference,
                                  String countNote, String countedBy, LocalDateTime countedAt, String reviewedBy,
                                  LocalDateTime reviewedAt, String reviewNote) {
}
