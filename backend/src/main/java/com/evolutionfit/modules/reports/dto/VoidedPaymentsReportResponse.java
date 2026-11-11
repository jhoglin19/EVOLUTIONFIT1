package com.evolutionfit.modules.reports.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.evolutionfit.modules.payments.PaymentMethod;

/** Pagos anulados en el periodo, por la fecha de anulación (igual que Caja). */
public record VoidedPaymentsReportResponse(LocalDate from, LocalDate to, long count, BigDecimal amount, List<Row> rows) {

    public record Row(Long paymentId, String receiptNumber, Long clientId, String clientName, String clientDocument,
                      String planName, PaymentMethod method, BigDecimal amount, LocalDateTime paidAt,
                      LocalDateTime voidedAt, String voidedBy, String voidReason) {
    }
}
