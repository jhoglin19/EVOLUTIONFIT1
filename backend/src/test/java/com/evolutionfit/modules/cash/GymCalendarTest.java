package com.evolutionfit.modules.cash;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.Test;

/** Lunes a sábado; domingos y festivos de Colombia cerrado; el ADMIN puede cambiar una fecha concreta. */
class GymCalendarTest {

    private static CashSettings mondayToSaturday() {
        CashSettings s = new CashSettings();
        s.setOpenDays("MON,TUE,WED,THU,FRI,SAT");
        s.setClosedOnHolidays(true);
        return s;
    }

    private static GymCalendarOverride override(LocalDate day, boolean open, String note) {
        GymCalendarOverride o = new GymCalendarOverride();
        o.setDay(day);
        o.setOpen(open);
        o.setNote(note);
        return o;
    }

    @Test
    void opensMondayToSaturdayAndClosesOnSundays() {
        assertThat(GymCalendar.resolve(LocalDate.of(2026, 11, 10), mondayToSaturday(), Optional.empty()).open()).isTrue();
        assertThat(GymCalendar.resolve(LocalDate.of(2026, 11, 14), mondayToSaturday(), Optional.empty()).open()).isTrue();
        GymCalendar.DayInfo sunday = GymCalendar.resolve(LocalDate.of(2026, 11, 15), mondayToSaturday(), Optional.empty());
        assertThat(sunday.open()).isFalse();
        assertThat(sunday.closedReason()).isEqualTo("Domingo");
    }

    @Test
    void closesOnColombianHolidaysEvenOnAMonday() {
        GymCalendar.DayInfo cartagena = GymCalendar.resolve(LocalDate.of(2026, 11, 16), mondayToSaturday(), Optional.empty());
        assertThat(cartagena.open()).isFalse();
        assertThat(cartagena.closedReason()).isEqualTo("Festivo: Independencia de Cartagena");
        assertThat(cartagena.holiday()).isEqualTo("Independencia de Cartagena");
        // Viernes Santo 2027 cae viernes: también cerrado.
        assertThat(GymCalendar.resolve(LocalDate.of(2027, 3, 26), mondayToSaturday(), Optional.empty()).open()).isFalse();
    }

    @Test
    void ifConfiguredHolidaysCanBeOpenDays() {
        CashSettings s = mondayToSaturday();
        s.setClosedOnHolidays(false);
        assertThat(GymCalendar.resolve(LocalDate.of(2026, 11, 16), s, Optional.empty()).open()).isTrue();
    }

    @Test
    void theAdminCanOpenAHolidayOrCloseANormalDay() {
        LocalDate holiday = LocalDate.of(2026, 12, 8);
        GymCalendar.DayInfo opened = GymCalendar.resolve(holiday, mondayToSaturday(),
                Optional.of(override(holiday, true, "Horario especial")));
        assertThat(opened.open()).isTrue();
        assertThat(opened.defaultOpen()).isFalse();
        assertThat(opened.overridden()).isTrue();
        assertThat(opened.holiday()).isEqualTo("Inmaculada Concepción");

        LocalDate tuesday = LocalDate.of(2026, 12, 15);
        GymCalendar.DayInfo closed = GymCalendar.resolve(tuesday, mondayToSaturday(),
                Optional.of(override(tuesday, false, "Mantenimiento")));
        assertThat(closed.open()).isFalse();
        assertThat(closed.closedReason()).isEqualTo("Cerrado por el administrador: Mantenimiento");
    }
}
