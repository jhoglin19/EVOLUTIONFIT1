package com.evolutionfit.modules.cash;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;

import com.evolutionfit.modules.cash.CashCalculator.CountResult;
import com.evolutionfit.modules.cash.CashCalculator.MovementType;
import com.evolutionfit.modules.cash.CashCalculator.PostCut;
import com.evolutionfit.modules.cash.CashLedger.PaymentRow;
import com.evolutionfit.modules.cash.CashLedger.WithdrawalRow;
import com.evolutionfit.modules.cash.CashSummary.MethodTotal;
import com.evolutionfit.modules.payments.PaymentMethod;
import com.evolutionfit.modules.payments.PaymentStatus;

import org.junit.jupiter.api.Test;

class CashCalculatorTest {

    private static final BigDecimal BASE = new BigDecimal("50000");
    private static final LocalDateTime T = LocalDateTime.of(2026, 11, 10, 9, 0);
    private long ids = 1;

    private PaymentRow pay(PaymentMethod method, String amount) {
        return new PaymentRow(ids++, method, new BigDecimal(amount), T.plusMinutes(ids), PaymentStatus.COMPLETED, null,
                "Cliente Ficticio", "EF-00000" + ids);
    }

    private PaymentRow voided(PaymentMethod method, String amount) {
        return new PaymentRow(ids++, method, new BigDecimal(amount), T.plusMinutes(ids), PaymentStatus.VOIDED,
                T.plusMinutes(ids + 1), "Cliente Ficticio", null);
    }

    private WithdrawalRow withdrawal(String amount, boolean voided) {
        return new WithdrawalRow(ids++, new BigDecimal(amount), WithdrawalCategory.SUPPLIES, "Servilletas",
                T.plusMinutes(ids), "Recepción", voided ? T.plusHours(1) : null, voided ? "Error" : null);
    }

    @Test
    void expectedCashIsBasePlusCashReceivedMinusVoidsMinusWithdrawals() {
        PaymentRow oldPaymentVoidedToday = voided(PaymentMethod.CASH, "30000");
        CashLedger ledger = new CashLedger(
                List.of(pay(PaymentMethod.CASH, "90000"), pay(PaymentMethod.CASH, "10000"), pay(PaymentMethod.NEQUI, "780000")),
                List.of(oldPaymentVoidedToday),
                List.of(withdrawal("5000", false), withdrawal("2000", false)));

        CashSummary s = CashCalculator.summarize(BASE, ledger);

        assertThat(s.cashIncome()).isEqualByComparingTo("100000");
        assertThat(s.cashVoided()).isEqualByComparingTo("30000");
        assertThat(s.cashWithdrawals()).isEqualByComparingTo("7000");
        assertThat(s.expectedCash()).isEqualByComparingTo("113000"); // 50.000 + 100.000 − 30.000 − 7.000
        assertThat(s.hasCashActivity()).isTrue();
    }

    @Test
    void aCashPaymentChargedAndVoidedTheSameDayNetsZero() {
        PaymentRow p = voided(PaymentMethod.CASH, "90000");
        CashSummary s = CashCalculator.summarize(BASE, new CashLedger(List.of(p), List.of(p), List.of()));

        assertThat(s.cashIncome()).isEqualByComparingTo("90000");
        assertThat(s.cashVoided()).isEqualByComparingTo("90000");
        assertThat(s.expectedCash()).isEqualByComparingTo(BASE);
        MethodTotal cash = s.methods().get(0);
        assertThat(cash.receivedCount()).isEqualTo(1);
        assertThat(cash.voidedCount()).isEqualTo(1);
        assertThat(cash.netAmount()).isEqualByComparingTo("0");
    }

    @Test
    void otherMethodsAreOnlyReportedAndDoNotChangeTheCash() {
        CashSummary s = CashCalculator.summarize(BASE, new CashLedger(
                List.of(pay(PaymentMethod.NEQUI, "90000"), pay(PaymentMethod.DAVIPLATA, "780000"),
                        pay(PaymentMethod.BANK_TRANSFER, "170000")), List.of(voided(PaymentMethod.CARD, "1000")), List.of()));

        assertThat(s.expectedCash()).isEqualByComparingTo(BASE);
        assertThat(s.hasCashActivity()).isFalse();
        assertThat(s.methods()).extracting(MethodTotal::method)
                .containsExactly(PaymentMethod.BANK_TRANSFER, PaymentMethod.NEQUI, PaymentMethod.DAVIPLATA, PaymentMethod.CARD);
    }

    @Test
    void aDayWithoutMovementsExpectsOnlyTheBase() {
        CashSummary s = CashCalculator.summarize(BASE, new CashLedger(List.of(), List.of(), List.of()));
        assertThat(s.expectedCash()).isEqualByComparingTo(BASE);
        assertThat(s.hasCashActivity()).isFalse();
        assertThat(s.methods()).isEmpty();
    }

    @Test
    void voidedWithdrawalsAreListedButDoNotCount() {
        CashLedger ledger = new CashLedger(List.of(), List.of(), List.of(withdrawal("5000", true)));
        CashSummary s = CashCalculator.summarize(BASE, ledger);
        assertThat(s.cashWithdrawals()).isEqualByComparingTo("0");
        assertThat(s.hasCashActivity()).isFalse();
        assertThat(CashCalculator.movements(ledger)).singleElement().satisfies(m -> {
            assertThat(m.voided()).isTrue();
            assertThat(m.cashEffect()).isEqualByComparingTo("0");
        });
    }

    @Test
    void theCountIsAdjustedByCashThatEnteredOrLeftAfterTheCut() {
        // Corte 10 p.m.: esperado congelado 140.000. A las 10:10 p.m. entra un cobro de 90.000 en efectivo
        // (cuenta para mañana pero ya está en la caja) y sale un retiro de 4.000. Un Nequi no cambia el efectivo.
        BigDecimal frozen = new BigDecimal("140000");
        PostCut postCut = CashCalculator.postCut(new CashLedger(
                List.of(pay(PaymentMethod.CASH, "90000"), pay(PaymentMethod.NEQUI, "90000")), List.of(),
                List.of(withdrawal("4000", false))));

        assertThat(postCut.income()).isEqualByComparingTo("90000");
        assertThat(postCut.outflow()).isEqualByComparingTo("4000");
        assertThat(postCut.movements()).extracting(CashCalculator.Movement::type)
                .containsExactly(MovementType.PAYMENT, MovementType.WITHDRAWAL);

        CountResult exact = CashCalculator.count(frozen, postCut, new BigDecimal("226000"));
        assertThat(exact.adjustedExpectedCash()).isEqualByComparingTo("226000");
        assertThat(exact.difference()).isEqualByComparingTo("0"); // sin falsa diferencia

        CountResult missing = CashCalculator.count(frozen, postCut, new BigDecimal("216000"));
        assertThat(missing.difference()).isEqualByComparingTo("-10000");
    }

    @Test
    void openDaysAreStoredAsCodes() {
        CashSettings s = new CashSettings();
        s.setOpenDaySet(EnumSet.of(DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.SATURDAY));
        assertThat(s.getOpenDays()).isEqualTo("MON,SAT,SUN");
        assertThat(s.isOpenWeekday(LocalDate.of(2026, 11, 9))).isTrue();   // lunes
        assertThat(s.isOpenWeekday(LocalDate.of(2026, 11, 10))).isFalse(); // martes
        s.setOpenDays("MON,TUE,WED,THU,FRI,SAT,SUN");
        assertThat(s.openDaySet()).hasSize(7);
    }
}
