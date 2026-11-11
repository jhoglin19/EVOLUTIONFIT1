package com.evolutionfit.modules.receipts;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Datos del pago que van en el comprobante. Los arma el módulo de pagos. */
public record ReceiptContent(String clientName,
                             String clientDocumentType,
                             String clientDocument,
                             String concept,
                             String planName,
                             LocalDate membershipStart,
                             LocalDate membershipEnd,
                             BigDecimal listPrice,
                             BigDecimal discountAmount,
                             String discountReason,
                             BigDecimal amount,
                             String paymentMethod,
                             String reference,
                             String receivedBy,
                             String notes) {
}
