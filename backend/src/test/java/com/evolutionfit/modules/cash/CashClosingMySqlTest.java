package com.evolutionfit.modules.cash;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import com.evolutionfit.common.calendar.ColombianHolidays;
import com.evolutionfit.common.exception.BusinessException;
import com.evolutionfit.common.exception.ConflictException;
import com.evolutionfit.common.exception.ResourceNotFoundException;
import com.evolutionfit.modules.cash.dto.CashClosingDetailResponse;
import com.evolutionfit.modules.cash.dto.CashClosingResponse;
import com.evolutionfit.modules.cash.dto.CashSettingsRequest;
import com.evolutionfit.modules.cash.dto.CashTodayResponse;
import com.evolutionfit.modules.cash.dto.CountPreviewResponse;
import com.evolutionfit.modules.cash.dto.CountRequest;
import com.evolutionfit.modules.cash.dto.MonthlyReportResponse;
import com.evolutionfit.modules.cash.dto.WithdrawalRequest;
import com.evolutionfit.modules.cash.dto.WithdrawalResponse;
import com.evolutionfit.modules.payments.PaymentMethod;
import com.evolutionfit.modules.payments.dto.PaymentResponse;

import org.junit.jupiter.api.Test;

/** Cierre automático, arqueo, retiros y reporte contra MySQL real, con el reloj movido a mano. */
class CashClosingMySqlTest extends CashMySqlTestSupport {

    @org.springframework.beans.factory.annotation.Autowired
    private GymCalendarService gymCalendarService;

    @Test
    void withoutAStartDateNothingIsClosedAndTheDayIsNotStarted() {
        at(newStartDate().atTime(23, 0));
        assertThat(closingService.closeDueDays()).isZero();
        CashTodayResponse today = cashService.today(reception);
        assertThat(today.started()).isFalse();
        assertThatThrownBy(() -> withdraw("1000")).isInstanceOf(BusinessException.class)
                .hasMessageContaining("no ha empezado");
    }

    @Test
    void afterThreeDaysOffItCatchesUpOncePerDayWithContiguousWindowsAndIsIdempotent() {
        LocalDate start = startCash();
        at(start.atTime(10, 0));
        pay(PaymentMethod.CASH);
        pay(PaymentMethod.NEQUI);

        long closeAudits = audits("CASH_CLOSING", "CLOSE");
        // Backend apagado desde ese día; vuelve tres días después, a las 9 a.m.
        at(start.plusDays(3).atTime(9, 0));
        assertThat(closingService.closeDueDays()).isEqualTo(3);
        assertThat(closingService.closeDueDays()).isZero();

        List<CashClosing> all = closingRepository().findByCashDateBetweenOrderByCashDateAsc(start, start.plusDays(10));
        assertThat(all).extracting(CashClosing::getCashDate).containsExactly(start, start.plusDays(1), start.plusDays(2));
        assertThat(all.get(0).getWindowStart()).isEqualTo(start.minusDays(1).atTime(22, 0));
        for (int i = 1; i < all.size(); i++) {
            assertThat(all.get(i).getWindowStart()).isEqualTo(all.get(i - 1).getWindowEnd());
        }
        assertThat(all).allMatch(c -> c.getClosingMode() == ClosingMode.CATCH_UP && c.getClosedBy() == null);

        CashClosing first = all.get(0);
        assertThat(first.getStatus()).isEqualTo(CashClosingStatus.PENDING_COUNT);
        assertThat(first.getExpectedCash()).isEqualByComparingTo("140000");
        // Días sin movimientos: cierre con la base, sin arqueo obligatorio.
        assertThat(all.subList(1, 3)).allSatisfy(c -> {
            assertThat(c.getStatus()).isEqualTo(CashClosingStatus.NO_ACTIVITY);
            assertThat(c.getExpectedCash()).isEqualByComparingTo("50000");
        });
        assertThat(jdbc.queryForObject("select count(*) from cash_closing_totals where cash_closing_id = ?", Long.class,
                first.getId())).isEqualTo(2);
        assertThat(audits("CASH_CLOSING", "CLOSE") - closeAudits).isEqualTo(3);
        assertThat(cashService.today(reception).cashDate()).isEqualTo(start.plusDays(3));
    }

    @Test
    void theCutIsAtTenPmSharpInBogota() {
        LocalDate start = startCash();
        at(start.atTime(21, 59, 59, 999_000_000));
        PaymentResponse before = pay(PaymentMethod.CASH);
        at(start.atTime(22, 0));
        PaymentResponse after = pay(PaymentMethod.CASH);
        assertThat(closingService.closeDueDays()).isEqualTo(1);

        CashClosing day = closing(start);
        assertThat(day.getClosingMode()).isEqualTo(ClosingMode.ON_TIME);
        assertThat(day.getCashIncome()).isEqualByComparingTo("90000");
        assertThat(stampOf(before.id())).isEqualTo(day.getId());
        assertThat(stampOf(after.id())).isNull();
        CashTodayResponse today = cashService.today(reception);
        assertThat(today.cashDate()).isEqualTo(start.plusDays(1));
        assertThat(today.summary().cashIncome()).isEqualByComparingTo("90000");
        assertThat(today.windowStart()).isEqualTo(start.atTime(22, 0));
    }

    @Test
    void aVoidCountsTheDayItIsMadeAndClosedDaysNeverChange() {
        LocalDate start = startCash();
        at(start.atTime(9, 0));
        PaymentResponse yesterday = pay(PaymentMethod.CASH);
        PaymentResponse sameDay = pay(PaymentMethod.CASH);
        at(start.atTime(11, 0));
        paymentService.voidPayment(sameDay.id(), "Error de digitación", admin.getId());
        at(start.atTime(22, 1));
        closingService.closeDueDays();
        CashClosing day1 = closing(start);
        // 50.000 + 180.000 recibidos − 90.000 anulado el mismo día (neto 0 para ese pago)
        assertThat(day1.getExpectedCash()).isEqualByComparingTo("140000");

        at(start.plusDays(1).atTime(10, 0));
        paymentService.voidPayment(yesterday.id(), "Cliente pidió devolución", admin.getId());
        at(start.plusDays(1).atTime(22, 0));
        closingService.closeDueDays();

        assertThat(closing(start).getExpectedCash()).isEqualByComparingTo("140000");
        CashClosing day2 = closing(start.plusDays(1));
        assertThat(day2.getCashIncome()).isEqualByComparingTo("0");
        assertThat(day2.getCashVoided()).isEqualByComparingTo("90000");
        assertThat(day2.getExpectedCash()).isEqualByComparingTo("-40000");
        assertThat(day2.getStatus()).isEqualTo(CashClosingStatus.PENDING_COUNT);
    }

    @Test
    void paymentsBeforeTheStartDateDoNotCountNorDoTheirVoids() {
        LocalDate start = newStartDate();
        at(start.minusDays(1).atTime(12, 0));
        PaymentResponse old = pay(PaymentMethod.CASH);
        at(start.atTime(8, 0));
        cashService.updateSettings(new CashSettingsRequest(new BigDecimal("50000"), start, EnumSet.allOf(DayOfWeek.class), null),
                admin.getId());
        at(start.atTime(9, 0));
        paymentService.voidPayment(old.id(), "Pago de prueba", admin.getId());
        at(start.atTime(22, 0));
        closingService.closeDueDays();

        CashClosing day = closing(start);
        assertThat(day.getExpectedCash()).isEqualByComparingTo("50000");
        assertThat(day.getStatus()).isEqualTo(CashClosingStatus.NO_ACTIVITY);
        assertThat(stampOf(old.id())).isNull();
    }

    @Test
    void theCountIsAdjustedForCashThatEnteredAfterTheCutAndTheFrozenExpectedDoesNotChange() {
        LocalDate start = startCash();
        at(start.atTime(9, 0));
        pay(PaymentMethod.CASH);
        withdraw("5000");
        at(start.atTime(22, 0, 5));
        closingService.closeDueDays();
        CashClosing day = closing(start);
        assertThat(day.getExpectedCash()).isEqualByComparingTo("135000");

        // 10:10 p.m.: un cobro en efectivo que cuenta para mañana, pero ya está en la caja física.
        at(start.atTime(22, 10));
        pay(PaymentMethod.CASH);
        pay(PaymentMethod.NEQUI);
        withdraw("2000");

        CountPreviewResponse preview = countService.preview(day.getId(), reception);
        assertThat(preview.postCutIncome()).isEqualByComparingTo("90000");
        assertThat(preview.postCutOutflow()).isEqualByComparingTo("2000");
        assertThat(preview.expectedAdjustedCash()).isEqualByComparingTo("223000");
        assertThat(preview.postCutMovements()).hasSize(2);

        at(start.atTime(22, 20));
        CashClosingResponse counted = countService.count(day.getId(),
                new CountRequest(new BigDecimal("223000"), null, preview.expectedAdjustedCash()), reception);

        assertThat(counted.status()).isEqualTo(CashClosingStatus.COUNTED);
        assertThat(counted.difference()).isEqualByComparingTo("0");
        assertThat(counted.expectedCash()).isEqualByComparingTo("135000");
        assertThat(counted.adjustedExpectedCash()).isEqualByComparingTo("223000");
        assertThat(counted.countedBy()).isEqualTo("Recepción Prueba");
        CashClosingDetailResponse detail = reportService.detail(day.getId());
        assertThat(detail.postCutMovements()).hasSize(2);
        assertThat(audits("CASH_CLOSING", "COUNT")).isGreaterThanOrEqualTo(1);

        // El cobro de las 10:10 p.m. queda en el día siguiente.
        at(start.plusDays(1).atTime(22, 0));
        closingService.closeDueDays();
        assertThat(closing(start.plusDays(1)).getCashIncome()).isEqualByComparingTo("90000");
        assertThat(closing(start).getExpectedCash()).isEqualByComparingTo("135000");
    }

    @Test
    void aCountWithADifferenceNeedsANoteAndGoesToTheAdminForReview() {
        LocalDate start = startCash();
        at(start.atTime(9, 0));
        pay(PaymentMethod.CASH);
        at(start.atTime(22, 0));
        closingService.closeDueDays();
        Long id = closing(start).getId();
        BigDecimal expected = countService.preview(id, reception).expectedAdjustedCash();

        assertThatThrownBy(() -> countService.count(id, new CountRequest(new BigDecimal("130000"), " ", expected), reception))
                .isInstanceOf(BusinessException.class).hasMessageContaining("nota");
        // Si entró efectivo mientras el diálogo estaba abierto, no se registra con un esperado viejo.
        assertThatThrownBy(() -> countService.count(id, new CountRequest(new BigDecimal("130000"), "Faltan",
                expected.subtract(BigDecimal.ONE)), reception)).isInstanceOf(ConflictException.class);

        CashClosingResponse r = countService.count(id, new CountRequest(new BigDecimal("130000"), "Faltan 10.000", expected),
                reception);
        assertThat(r.status()).isEqualTo(CashClosingStatus.PENDING_REVIEW);
        assertThat(r.difference()).isEqualByComparingTo("-10000");
        assertThat(cashService.today(admin).pendingReviews()).isEqualTo(1);
        assertThat(cashService.today(reception).pendingReviews()).isNull();
        // Recepción ya no ve ese día.
        assertThatThrownBy(() -> countService.preview(id, reception)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> countService.count(id, new CountRequest(new BigDecimal("140000"), null, expected), reception))
                .isInstanceOf(ResourceNotFoundException.class);

        CashClosingResponse reviewed = countService.review(id, "Se habló con recepción", admin.getId());
        assertThat(reviewed.status()).isEqualTo(CashClosingStatus.REVIEWED);
        assertThat(reviewed.reviewedBy()).isEqualTo("Admin Prueba");
        assertThatThrownBy(() -> countService.review(id, "Otra vez", admin.getId())).isInstanceOf(ConflictException.class);

        CashClosingResponse reopened = countService.reopen(id, "Valor mal digitado", admin.getId());
        assertThat(reopened.status()).isEqualTo(CashClosingStatus.PENDING_COUNT);
        assertThat(reopened.countedCash()).isNull();
        assertThat(reopened.expectedCash()).isEqualByComparingTo("140000");
        assertThat(countService.count(id, new CountRequest(new BigDecimal("140000"), null, expected), reception).status())
                .isEqualTo(CashClosingStatus.COUNTED);
        assertThat(audits("CASH_CLOSING", "REVIEW")).isGreaterThanOrEqualTo(1);
        assertThat(audits("CASH_CLOSING", "REOPEN_COUNT")).isGreaterThanOrEqualTo(1);
    }

    @Test
    void withdrawalsLowerTheExpectedCashCannotExceedItAndOnlyTheAdminVoidsThemBeforeTheCut() {
        LocalDate start = startCash();
        at(start.atTime(9, 0));
        WithdrawalResponse napkins = withdraw("5000");
        assertThat(napkins.cashDate()).isEqualTo(start);
        assertThatThrownBy(() -> withdraw("45001")).isInstanceOf(BusinessException.class)
                .hasMessageContaining("supera el efectivo disponible");

        String requestId = UUID.randomUUID().toString();
        WithdrawalRequest soap = new WithdrawalRequest(requestId, new BigDecimal("3000"), WithdrawalCategory.CLEANING, "Jabón");
        WithdrawalResponse first = cashService.registerWithdrawal(soap, reception);
        assertThat(cashService.registerWithdrawal(soap, reception).id()).isEqualTo(first.id()); // doble clic
        assertThat(cashService.today(reception).summary().expectedCash()).isEqualByComparingTo("42000");

        at(start.atTime(15, 0));
        WithdrawalResponse voided = cashService.voidWithdrawal(first.id(), "Se registró por error", admin.getId());
        assertThat(voided.voided()).isTrue();
        assertThatThrownBy(() -> cashService.voidWithdrawal(first.id(), "Otra vez", admin.getId()))
                .isInstanceOf(ConflictException.class);
        assertThat(cashService.today(reception).summary().expectedCash()).isEqualByComparingTo("45000");

        // Después del corte (aunque el cierre todavía no haya corrido) ya no se puede anular.
        at(start.atTime(22, 0, 1));
        assertThatThrownBy(() -> cashService.voidWithdrawal(napkins.id(), "Tarde", admin.getId()))
                .isInstanceOf(BusinessException.class).hasMessageContaining("antes del corte");
        CashClosing day = closing(start);
        assertThat(day.getCashWithdrawals()).isEqualByComparingTo("5000");
        assertThat(day.getExpectedCash()).isEqualByComparingTo("45000");
        assertThat(cashService.listWithdrawals(start, start, null)).hasSize(2);
        assertThat(audits("CASH_WITHDRAWAL", "CREATE")).isGreaterThanOrEqualTo(2);
        assertThat(audits("CASH_WITHDRAWAL", "VOID")).isGreaterThanOrEqualTo(1);
    }

    @Test
    void theStartDateCannotBeInThePastAndLocksAfterTheFirstClosing() {
        LocalDate start = startCash();
        CashSettingsRequest past = new CashSettingsRequest(new BigDecimal("50000"), start.minusDays(1),
                EnumSet.allOf(DayOfWeek.class), null);
        assertThatThrownBy(() -> cashService.updateSettings(past, admin.getId())).hasMessageContaining("pasado");

        at(start.atTime(22, 0));
        closingService.closeDueDays();
        CashSettingsRequest later = new CashSettingsRequest(new BigDecimal("60000"), start.plusDays(5),
                EnumSet.allOf(DayOfWeek.class), null);
        assertThatThrownBy(() -> cashService.updateSettings(later, admin.getId())).hasMessageContaining("ya hay cierres");

        // La base sí cambia, pero solo para los días que todavía no se cierran.
        cashService.updateSettings(new CashSettingsRequest(new BigDecimal("60000"), start,
                EnumSet.of(DayOfWeek.MONDAY), null), admin.getId());
        at(start.plusDays(1).atTime(22, 0));
        closingService.closeDueDays();
        assertThat(closing(start).getOpeningBase()).isEqualByComparingTo("50000");
        CashClosing next = closing(start.plusDays(1));
        assertThat(next.getOpeningBase()).isEqualByComparingTo("60000");
        assertThat(next.isWasOpenDay()).isEqualTo(start.plusDays(1).getDayOfWeek() == DayOfWeek.MONDAY
                && !ColombianHolidays.isHoliday(start.plusDays(1)));
        assertThat(audits("CASH_SETTINGS", "UPDATE")).isGreaterThanOrEqualTo(2);
    }

    @Test
    void theMonthlyReportAddsUpTheDailyClosingsByMethod() {
        LocalDate start = startCash();
        at(start.atTime(9, 0));
        pay(PaymentMethod.CASH);
        pay(PaymentMethod.NEQUI);
        withdraw("4000");
        at(start.plusDays(1).atTime(9, 0));
        pay(PaymentMethod.CASH);
        pay(PaymentMethod.DAVIPLATA);
        at(start.plusDays(1).atTime(22, 0));
        closingService.closeDueDays();

        MonthlyReportResponse r = reportService.monthly(start.toString().substring(0, 7));
        List<CashClosing> inMonth = closingRepository().findByCashDateBetweenOrderByCashDateAsc(
                start.withDayOfMonth(1), start.withDayOfMonth(start.lengthOfMonth()));
        assertThat(r.closedDays()).isEqualTo(inMonth.size());
        assertThat(r.cashIncome()).isEqualByComparingTo(inMonth.stream().map(CashClosing::getCashIncome)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        assertThat(r.withdrawalsByCategory()).singleElement()
                .satisfies(c -> assertThat(c.amount()).isEqualByComparingTo("4000"));
        assertThat(r.methods()).extracting(CashSummary.MethodTotal::method)
                .contains(PaymentMethod.CASH, PaymentMethod.NEQUI, PaymentMethod.DAVIPLATA);
        assertThat(r.pendingCounts()).isPositive();
    }

    // ---- días de atención ----

    /** La caja empieza un día y se pone al día hasta incluir el primer día cerrado (domingo o festivo). */
    private LocalDate firstClosedDayFrom(LocalDate start) {
        LocalDate d = start;
        while (d.getDayOfWeek() != DayOfWeek.SUNDAY && !ColombianHolidays.isHoliday(d)) {
            d = d.plusDays(1);
        }
        return d;
    }

    private void mondayToSaturday(LocalDate start) {
        cashService.updateSettings(new CashSettingsRequest(new BigDecimal("50000"), start,
                EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY,
                        DayOfWeek.FRIDAY, DayOfWeek.SATURDAY), true), admin.getId());
    }

    @Test
    void aClosedDayWithoutCashMovementsIsShownClosedAndNeedsNoCount() {
        LocalDate start = startCash();
        mondayToSaturday(start);
        LocalDate closedDay = firstClosedDayFrom(start.plusDays(1));

        at(closedDay.atTime(10, 0));
        CashTodayResponse today = cashService.today(reception);
        assertThat(today.openDay()).isFalse();
        assertThat(today.closedReason()).isIn("Domingo", "Festivo: " + ColombianHolidays.name(closedDay).orElse(""));

        at(closedDay.atTime(22, 0));
        closingService.closeDueDays();
        CashClosing c = closing(closedDay);
        assertThat(c.isWasOpenDay()).isFalse();
        assertThat(c.getStatus()).isEqualTo(CashClosingStatus.NO_ACTIVITY);
        assertThat(closing(closedDay.minusDays(1)).isWasOpenDay())
                .isEqualTo(closedDay.minusDays(1).getDayOfWeek() != DayOfWeek.SUNDAY
                        && !ColombianHolidays.isHoliday(closedDay.minusDays(1)));
    }

    @Test
    void aClosedDayWithCashMovementsIsTreatedAsANormalDay() {
        LocalDate start = startCash();
        mondayToSaturday(start);
        LocalDate closedDay = firstClosedDayFrom(start.plusDays(1));
        at(closedDay.atTime(11, 0));
        pay(PaymentMethod.CASH);
        at(closedDay.atTime(22, 0));
        closingService.closeDueDays();

        CashClosing c = closing(closedDay);
        assertThat(c.isWasOpenDay()).isFalse();
        assertThat(c.getStatus()).isEqualTo(CashClosingStatus.PENDING_COUNT);
        assertThat(c.getExpectedCash()).isEqualByComparingTo("140000");
    }

    @Test
    void theAdminOpensOrClosesADateButSavedClosingsNeverChange() {
        LocalDate start = startCash();
        mondayToSaturday(start);
        LocalDate closedDay = firstClosedDayFrom(start.plusDays(1));
        LocalDate normalDay = closedDay.plusDays(1);
        while (normalDay.getDayOfWeek() == DayOfWeek.SUNDAY || ColombianHolidays.isHoliday(normalDay)) {
            normalDay = normalDay.plusDays(1);
        }
        gymCalendarService.set(closedDay, true, "Horario especial", admin.getId());
        gymCalendarService.set(normalDay, false, "Mantenimiento", admin.getId());

        at(normalDay.atTime(22, 0));
        closingService.closeDueDays();
        assertThat(closing(closedDay).isWasOpenDay()).isTrue();
        assertThat(closing(normalDay).isWasOpenDay()).isFalse();

        // Ya cerrados: no se pueden cambiar ni quitar sus excepciones.
        LocalDate locked = normalDay;
        assertThatThrownBy(() -> gymCalendarService.set(locked, true, null, admin.getId()))
                .isInstanceOf(BusinessException.class).hasMessageContaining("cierres guardados no cambian");
        assertThatThrownBy(() -> gymCalendarService.clear(locked, admin.getId())).isInstanceOf(BusinessException.class);
        assertThat(closing(normalDay).isWasOpenDay()).isFalse();
        assertThat(gymCalendarService.year(normalDay.getYear()).days())
                .anySatisfy(d -> {
                    assertThat(d.date()).isEqualTo(locked);
                    assertThat(d.locked()).isTrue();
                    assertThat(d.closedReason()).isEqualTo("Cerrado por el administrador: Mantenimiento");
                });
        assertThat(audits("GYM_CALENDAR", "CREATE")).isGreaterThanOrEqualTo(2);
    }

    @Test
    void theYearCalendarListsTheColombianHolidaysAsClosed() {
        var calendar = gymCalendarService.year(2026);
        assertThat(calendar.days()).hasSize(19).allSatisfy(d -> {
            assertThat(d.holiday()).isNotNull();
            assertThat(d.open()).isFalse();
        });
        assertThat(calendar.closedOnHolidays()).isTrue();
    }

    private Long stampOf(Long paymentId) {
        return jdbc.queryForObject("select cash_closing_id from payments where id = ?", Long.class, paymentId);
    }
}
