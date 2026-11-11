package com.evolutionfit.modules.memberships;

import static com.evolutionfit.modules.memberships.MembershipFixtures.TODAY;
import static com.evolutionfit.modules.memberships.MembershipFixtures.day;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import com.evolutionfit.common.exception.BusinessException;

import org.junit.jupiter.api.Test;

class MembershipDatesTest {

    @Test
    void endDateIncludesTheFirstDay() {
        // Coincide con la membresía existente: 2026-11-08 + 30 días = 2026-12-07.
        assertThat(MembershipDates.endDate(LocalDate.of(2026, 11, 8), 30)).isEqualTo(LocalDate.of(2026, 12, 7));
    }

    @Test
    void oneDayPlanStartsAndEndsTheSameDay() {
        assertThat(MembershipDates.endDate(TODAY, 1)).isEqualTo(TODAY);
    }

    @Test
    void endDateCrossesMonthYearAndLeapDay() {
        assertThat(MembershipDates.endDate(LocalDate.of(2026, 12, 15), 30)).isEqualTo(LocalDate.of(2027, 1, 13));
        assertThat(MembershipDates.endDate(LocalDate.of(2028, 2, 1), 30)).isEqualTo(LocalDate.of(2028, 3, 1));
        assertThat(MembershipDates.endDate(LocalDate.of(2026, 1, 31), 30)).isEqualTo(LocalDate.of(2026, 3, 1));
    }

    @Test
    void zeroDurationIsRejected() {
        assertThatThrownBy(() -> MembershipDates.endDate(TODAY, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void newMembershipWithoutCurrentStartsTheDayOfPayment() {
        assertThat(MembershipDates.newMembershipStart(TODAY, null, null)).isEqualTo(TODAY);
    }

    @Test
    void newMembershipAcceptsAnOptionalFutureStart() {
        assertThat(MembershipDates.newMembershipStart(TODAY, day(11, 20), null)).isEqualTo(day(11, 20));
        assertThat(MembershipDates.newMembershipStart(TODAY, TODAY, null)).isEqualTo(TODAY);
    }

    @Test
    void pastStartIsRejectedOutsideMigration() {
        assertThatThrownBy(() -> MembershipDates.newMembershipStart(TODAY, TODAY.minusDays(1), null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("migradas");
    }

    @Test
    void renewalBeforeExpiryIsScheduledForTheDayAfter() {
        assertThat(MembershipDates.newMembershipStart(TODAY, null, day(12, 7))).isEqualTo(day(12, 8));
        // Vence hoy: la renovación empieza mañana.
        assertThat(MembershipDates.newMembershipStart(TODAY, null, TODAY)).isEqualTo(TODAY.plusDays(1));
    }

    @Test
    void renewalBeforeExpiryRejectsADifferentStart() {
        assertThatThrownBy(() -> MembershipDates.newMembershipStart(TODAY, day(12, 20), day(12, 7)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("2026-12-08");
        assertThat(MembershipDates.newMembershipStart(TODAY, day(12, 8), day(12, 7))).isEqualTo(day(12, 8));
    }

    @Test
    void renewalAfterExpiryStartsTheDayOfPaymentWithoutGraceDays() {
        // Venció ayer: no hay días de gracia ni se encadena con la anterior.
        assertThat(MembershipDates.newMembershipStart(TODAY, null, TODAY.minusDays(1))).isEqualTo(TODAY);
    }

    @Test
    void freezeEndIsCalendarDaysInARow() {
        assertThat(MembershipDates.freezeEndDate(TODAY, 1)).isEqualTo(TODAY);
        assertThat(MembershipDates.freezeEndDate(TODAY, 30)).isEqualTo(day(12, 8));
        assertThatThrownBy(() -> MembershipDates.freezeEndDate(TODAY, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void frozenDaysAreAddedToTheExpiry() {
        assertThat(MembershipDates.effectiveEndDate(day(12, 7), 5)).isEqualTo(day(12, 12));
        assertThat(MembershipDates.effectiveEndDate(day(12, 7), 0)).isEqualTo(day(12, 7));
    }

    @Test
    void cancellingBeforeTheFreezeStartsAppliesNoDays() {
        assertThat(MembershipDates.appliedDaysOnCancel(day(11, 20), day(11, 29), TODAY)).isZero();
    }

    @Test
    void cancellingOnTheStartDayAppliesNoDays() {
        assertThat(MembershipDates.appliedDaysOnCancel(TODAY, TODAY.plusDays(9), TODAY)).isZero();
    }

    @Test
    void cancellingAnOngoingFreezeAppliesOnlyTheElapsedDays() {
        // Empezó el 5: el 5, 6, 7 y 8 quedan congelados; desde hoy (9) el cliente puede entrar.
        assertThat(MembershipDates.appliedDaysOnCancel(day(11, 5), day(11, 14), TODAY)).isEqualTo(4);
        // Último día de la congelación.
        assertThat(MembershipDates.appliedDaysOnCancel(day(11, 5), TODAY, TODAY)).isEqualTo(4);
    }

    @Test
    void aFinishedFreezeCannotBeCancelled() {
        assertThatThrownBy(() -> MembershipDates.appliedDaysOnCancel(day(11, 1), day(11, 8), TODAY))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ya terminó");
    }

    // ---- activación por pago ----

    @Test
    void paymentWithoutCurrentStartsTheDayOfPayment() {
        assertThat(MembershipDates.activationStart(TODAY, null, null)).isEqualTo(TODAY);
    }

    @Test
    void paymentWithACurrentMembershipStartsTheDayAfterItsExpiry() {
        assertThat(MembershipDates.activationStart(TODAY, null, day(12, 12))).isEqualTo(day(12, 13));
        // Aunque al vender se hubiera pedido otra fecha.
        assertThat(MembershipDates.activationStart(TODAY, day(11, 20), day(12, 12))).isEqualTo(day(12, 13));
        // La vigente vence hoy: empieza mañana.
        assertThat(MembershipDates.activationStart(TODAY, null, TODAY)).isEqualTo(TODAY.plusDays(1));
    }

    @Test
    void paymentRespectsARequestedStartThatIsStillInTheFuture() {
        assertThat(MembershipDates.activationStart(TODAY, day(11, 20), null)).isEqualTo(day(11, 20));
        assertThat(MembershipDates.requestedStartPassed(TODAY, day(11, 20), null)).isFalse();
    }

    @Test
    void aRequestedStartThatAlreadyPassedStartsTheDayOfPaymentAndWarns() {
        assertThat(MembershipDates.activationStart(TODAY, day(11, 5), null)).isEqualTo(TODAY);
        assertThat(MembershipDates.requestedStartPassed(TODAY, day(11, 5), null)).isTrue();
        // Con una vigente no hay aviso: manda el vencimiento de la vigente.
        assertThat(MembershipDates.requestedStartPassed(TODAY, day(11, 5), day(11, 30))).isFalse();
        assertThat(MembershipDates.requestedStartPassed(TODAY, TODAY, null)).isFalse();
    }

    @Test
    void paymentAfterExpiryHasNoGraceDays() {
        assertThat(MembershipDates.activationStart(TODAY, null, TODAY.minusDays(1))).isEqualTo(TODAY);
    }
}
