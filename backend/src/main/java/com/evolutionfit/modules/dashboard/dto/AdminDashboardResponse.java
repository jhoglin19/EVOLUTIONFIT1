package com.evolutionfit.modules.dashboard.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.evolutionfit.modules.cash.CashSummary.MethodTotal;

/**
 * Ingresos del día y del mes en curso (solo ADMIN), con la misma regla de Caja y días calendario.
 * Anulaciones por fecha de anulación; descuentos de pagos no anulados.
 */
public record AdminDashboardResponse(LocalDate today, LocalDate monthStart, List<MethodTotal> todayMethods,
                                     List<MethodTotal> monthMethods, long monthVoidedCount, BigDecimal monthVoidedAmount,
                                     long monthDiscountCount, BigDecimal monthDiscountAmount) {
}
