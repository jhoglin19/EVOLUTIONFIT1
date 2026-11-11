package com.evolutionfit.modules.reports.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.evolutionfit.modules.payments.PaymentMethod;
import com.evolutionfit.modules.payments.PaymentStatus;

/**
 * Descuentos aplicados en pagos del periodo, por la fecha del pago. Los totales no incluyen los pagos que
 * después se anularon (la fila sí aparece, marcada como anulada).
 */
public record DiscountsReportResponse(LocalDate from, LocalDate to, long count, BigDecimal discountAmount,
                                      long voidedCount, List<Row> rows) {

    public record Row(Long paymentId, String receiptNumber, Long clientId, String clientName, String clientDocument,
                      String planName, PaymentMethod method, PaymentStatus status, BigDecimal listPrice,
                      BigDecimal discountAmount, BigDecimal amount, String discountReason, String discountedBy,
                      LocalDateTime paidAt) {
    }
}
