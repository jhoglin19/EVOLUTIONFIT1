package com.evolutionfit.modules.reports.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.evolutionfit.modules.cash.CashSummary.MethodTotal;
import com.evolutionfit.modules.reports.IncomeGrouping;

/**
 * Ingresos con la misma regla de Caja (docs/REGLAS_NEGOCIO.md, secciones 10.8 y 11): lo recibido se cuenta
 * por la fecha del pago (aunque después se anule), lo anulado por la fecha de anulación y neto = recibido −
 * anulado. A diferencia de Caja, los días son calendario (00:00 a 23:59), sin el corte de las 10 p.m.
 */
public record IncomeReportResponse(LocalDate from, LocalDate to, IncomeGrouping grouping, List<MethodTotal> methods,
                                   List<Period> periods) {

    /** cashNet: efectivo neto. otherNet: los demás métodos, neto. */
    public record Period(LocalDate start, LocalDate end, int receivedCount, BigDecimal receivedAmount, int voidedCount,
                         BigDecimal voidedAmount, BigDecimal netAmount, BigDecimal cashNet, BigDecimal otherNet) {
    }
}
