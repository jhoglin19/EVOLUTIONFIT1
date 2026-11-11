package com.evolutionfit.modules.cash.dto;

import java.math.BigDecimal;
import java.util.List;

import com.evolutionfit.modules.cash.WithdrawalCategory;
import com.evolutionfit.modules.cash.CashSummary.MethodTotal;

/** Reporte mensual: solo informe, suma de los cierres diarios del mes. */
public record MonthlyReportResponse(String month, int closedDays, BigDecimal cashIncome, BigDecimal cashVoided,
                                    BigDecimal cashWithdrawals, BigDecimal countedDifference, long pendingCounts,
                                    long pendingReviews, List<MethodTotal> methods,
                                    List<CategoryTotal> withdrawalsByCategory, List<CashClosingResponse> days) {

    public record CategoryTotal(WithdrawalCategory category, String label, int count, BigDecimal amount) {
    }
}
