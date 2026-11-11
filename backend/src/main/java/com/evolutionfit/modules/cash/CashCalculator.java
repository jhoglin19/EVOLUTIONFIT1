package com.evolutionfit.modules.cash;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.evolutionfit.modules.cash.CashLedger.PaymentRow;
import com.evolutionfit.modules.cash.CashLedger.WithdrawalRow;
import com.evolutionfit.modules.cash.CashSummary.MethodTotal;
import com.evolutionfit.modules.payments.PaymentMethod;

/**
 * Cálculos de caja sin base de datos. El cierre, la pantalla del día y el arqueo usan estas mismas
 * funciones, así lo que ve recepción coincide con lo que se congela.
 */
public final class CashCalculator {

    private CashCalculator() {
    }

    public static CashSummary summarize(BigDecimal openingBase, CashLedger ledger) {
        BigDecimal income = cash(ledger.received());
        BigDecimal voided = cash(ledger.voids());
        BigDecimal withdrawals = activeWithdrawals(ledger.withdrawals());
        BigDecimal expected = openingBase.add(income).subtract(voided).subtract(withdrawals);
        boolean activity = ledger.received().stream().anyMatch(p -> p.method() == PaymentMethod.CASH)
                || ledger.voids().stream().anyMatch(p -> p.method() == PaymentMethod.CASH)
                || ledger.withdrawals().stream().anyMatch(w -> !w.voided());
        return new CashSummary(openingBase, income, voided, withdrawals, expected, activity, methodTotals(ledger));
    }

    /**
     * Arqueo: el efectivo que entró después del corte ya está en la caja física, y el que salió ya no está.
     * El esperado congelado no cambia; el ajuste se guarda aparte.
     */
    public static PostCut postCut(CashLedger after) {
        BigDecimal income = cash(after.received());
        BigDecimal outflow = cash(after.voids()).add(activeWithdrawals(after.withdrawals()));
        return new PostCut(income, outflow, movements(after).stream().filter(m -> m.cashEffect().signum() != 0).toList());
    }

    public static CountResult count(BigDecimal expectedCash, PostCut postCut, BigDecimal countedCash) {
        BigDecimal adjusted = expectedCash.add(postCut.income()).subtract(postCut.outflow());
        return new CountResult(adjusted, countedCash.subtract(adjusted));
    }

    /** Movimientos en orden de hora. cashEffect: lo que suma o resta al efectivo de la caja. */
    public static List<Movement> movements(CashLedger ledger) {
        List<Movement> list = new ArrayList<>();
        for (PaymentRow p : ledger.received()) {
            list.add(new Movement(MovementType.PAYMENT, p.id(), p.paidAt(), p.method(), p.amount(),
                    p.method() == PaymentMethod.CASH ? p.amount() : BigDecimal.ZERO,
                    p.clientName(), p.receiptNumber(), null, false));
        }
        for (PaymentRow p : ledger.voids()) {
            list.add(new Movement(MovementType.PAYMENT_VOID, p.id(), p.voidedAt(), p.method(), p.amount(),
                    p.method() == PaymentMethod.CASH ? p.amount().negate() : BigDecimal.ZERO,
                    p.clientName(), p.receiptNumber(), null, false));
        }
        for (WithdrawalRow w : ledger.withdrawals()) {
            list.add(new Movement(MovementType.WITHDRAWAL, w.id(), w.occurredAt(), PaymentMethod.CASH, w.amount(),
                    w.voided() ? BigDecimal.ZERO : w.amount().negate(), w.concept(), null, w.category(), w.voided()));
        }
        list.sort(Comparator.comparing(Movement::at).thenComparing(Movement::id));
        return list;
    }

    private static List<MethodTotal> methodTotals(CashLedger ledger) {
        Map<PaymentMethod, MethodTotal> map = new EnumMap<>(PaymentMethod.class);
        for (PaymentRow p : ledger.received()) {
            MethodTotal t = map.getOrDefault(p.method(), empty(p.method()));
            map.put(p.method(), new MethodTotal(p.method(), t.receivedCount() + 1, t.receivedAmount().add(p.amount()),
                    t.voidedCount(), t.voidedAmount()));
        }
        for (PaymentRow p : ledger.voids()) {
            MethodTotal t = map.getOrDefault(p.method(), empty(p.method()));
            map.put(p.method(), new MethodTotal(p.method(), t.receivedCount(), t.receivedAmount(),
                    t.voidedCount() + 1, t.voidedAmount().add(p.amount())));
        }
        return List.copyOf(map.values());
    }

    private static MethodTotal empty(PaymentMethod method) {
        return new MethodTotal(method, 0, BigDecimal.ZERO, 0, BigDecimal.ZERO);
    }

    private static BigDecimal cash(List<PaymentRow> rows) {
        return rows.stream().filter(p -> p.method() == PaymentMethod.CASH).map(PaymentRow::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal activeWithdrawals(List<WithdrawalRow> rows) {
        return rows.stream().filter(w -> !w.voided()).map(WithdrawalRow::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public enum MovementType {
        PAYMENT, PAYMENT_VOID, WITHDRAWAL
    }

    public record Movement(MovementType type, Long id, LocalDateTime at, PaymentMethod method, BigDecimal amount,
                           BigDecimal cashEffect, String description, String receiptNumber,
                           WithdrawalCategory category, boolean voided) {
    }

    public record PostCut(BigDecimal income, BigDecimal outflow, List<Movement> movements) {
    }

    public record CountResult(BigDecimal adjustedExpectedCash, BigDecimal difference) {
    }
}
