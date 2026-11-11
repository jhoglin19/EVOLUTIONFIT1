package com.evolutionfit.modules.cash;

import java.math.BigDecimal;
import java.util.List;

import com.evolutionfit.modules.payments.PaymentMethod;

/**
 * Totales de un periodo. Efectivo esperado = base + efectivo recibido − efectivo anulado − retiros.
 * Un pago cobrado y anulado el mismo día entra y sale: neto $0.
 */
public record CashSummary(BigDecimal openingBase, BigDecimal cashIncome, BigDecimal cashVoided,
                          BigDecimal cashWithdrawals, BigDecimal expectedCash, boolean hasCashActivity,
                          List<MethodTotal> methods) {

    /** Informe por método: lo recibido y lo anulado en el periodo. */
    public record MethodTotal(PaymentMethod method, int receivedCount, BigDecimal receivedAmount,
                              int voidedCount, BigDecimal voidedAmount) {

        public BigDecimal netAmount() {
            return receivedAmount.subtract(voidedAmount);
        }
    }
}
