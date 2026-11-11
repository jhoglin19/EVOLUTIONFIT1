package com.evolutionfit.modules.cash;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import org.junit.jupiter.api.Test;

class CashCalendarTest {

    private final CashCalendar calendar = new CashCalendar(LocalTime.of(22, 0));
    private final LocalDate day = LocalDate.of(2026, 11, 10);

    @Test
    void justBeforeTheCutCountsForTheSameDayAndAtTheCutForTheNextOne() {
        assertThat(calendar.cashDayOf(day.atTime(21, 59, 59, 999_000_000))).isEqualTo(day);
        assertThat(calendar.cashDayOf(day.atTime(22, 0))).isEqualTo(day.plusDays(1));
        assertThat(calendar.cashDayOf(day.atTime(22, 10))).isEqualTo(day.plusDays(1));
        assertThat(calendar.cashDayOf(day.atTime(0, 5))).isEqualTo(day);
    }

    @Test
    void lastDueDayIsTodayOnlyFromTheCutOn() {
        assertThat(calendar.lastDueDay(day.atTime(21, 59, 59))).isEqualTo(day.minusDays(1));
        assertThat(calendar.lastDueDay(day.atTime(22, 0))).isEqualTo(day);
        assertThat(calendar.lastDueDay(day.atTime(23, 59))).isEqualTo(day);
    }

    @Test
    void cashStartsAtTheCutOfTheDayBeforeTheStartDate() {
        assertThat(calendar.cashStart(day)).isEqualTo(LocalDateTime.of(2026, 11, 9, 22, 0));
        assertThat(calendar.cutoff(day)).isEqualTo(LocalDateTime.of(2026, 11, 10, 22, 0));
    }

    @Test
    void closingTimeIsConfigurable() {
        CashCalendar nine = new CashCalendar(new CashProperties("21:00", true));
        assertThat(nine.cashDayOf(day.atTime(21, 30))).isEqualTo(day.plusDays(1));
        assertThat(new CashProperties(null, null).closingLocalTime()).isEqualTo(LocalTime.of(22, 0));
    }
}
