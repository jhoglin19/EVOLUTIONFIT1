package com.evolutionfit.modules.reports.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Planes más vendidos: membresías pagadas (activadas) en el periodo, sin anuladas ni migradas. planNames:
 * los nombres que tenía el plan al venderse (más de uno si se renombró).
 */
public record PlanSalesReportResponse(LocalDate from, LocalDate to, long totalSold, BigDecimal totalRevenue,
                                      List<Row> rows) {

    public record Row(Long planId, String planNames, long sold, long newClients, long renewals,
                      BigDecimal revenue, BigDecimal discounts) {
    }
}
