package com.evolutionfit.modules.cash.dto;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

/**
 * Calendario de atención de un año: festivos de Colombia y excepciones del ADMIN.
 * locked: ese día ya tiene cierre de caja y no se puede cambiar.
 */
public record CalendarResponse(int year, List<DayOfWeek> openDays, boolean closedOnHolidays, List<Day> days) {

    public record Day(LocalDate date, boolean open, String closedReason, String holiday, boolean defaultOpen,
                      boolean overridden, String note, boolean locked) {
    }
}
