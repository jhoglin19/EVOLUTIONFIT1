package com.evolutionfit.modules.cash;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.evolutionfit.modules.payments.PaymentMethod;
import com.evolutionfit.modules.payments.PaymentStatus;

/**
 * Filas de dinero de un periodo: pagos recibidos (aunque luego se anulen), anulaciones y retiros.
 * Es lo mismo para un cierre ya hecho, para el día en curso y para lo que pasó después de un corte.
 */
public record CashLedger(List<PaymentRow> received, List<PaymentRow> voids, List<WithdrawalRow> withdrawals) {

    public record PaymentRow(Long id, PaymentMethod method, BigDecimal amount, LocalDateTime paidAt,
                             PaymentStatus status, LocalDateTime voidedAt, String clientName, String receiptNumber) {
    }

    public record WithdrawalRow(Long id, BigDecimal amount, WithdrawalCategory category, String concept,
                                LocalDateTime occurredAt, String registeredBy, LocalDateTime voidedAt,
                                String voidReason) {

        public boolean voided() {
            return voidedAt != null;
        }
    }
}
