package com.evolutionfit.modules.reports;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

/** Periodos del reporte de ingresos, recortados al rango pedido. Sin base de datos. */
public final class ReportPeriods {

    private ReportPeriods() {
    }

    /** Periodos consecutivos que cubren todo el rango, también los que no tuvieron pagos. */
    public static List<DateRange> split(DateRange range, IncomeGrouping grouping) {
        List<DateRange> periods = new ArrayList<>();
        LocalDate start = range.from();
        while (!start.isAfter(range.to())) {
            LocalDate end = min(naturalEnd(start, grouping), range.to());
            periods.add(new DateRange(start, end));
            start = end.plusDays(1);
        }
        return periods;
    }

    /** Inicio del periodo que contiene el día, recortado al inicio del rango. */
    public static LocalDate periodStart(LocalDate day, DateRange range, IncomeGrouping grouping) {
        LocalDate natural = switch (grouping) {
            case DAY -> day;
            case WEEK -> day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MONTH -> day.withDayOfMonth(1);
        };
        return natural.isBefore(range.from()) ? range.from() : natural;
    }

    private static LocalDate naturalEnd(LocalDate start, IncomeGrouping grouping) {
        return switch (grouping) {
            case DAY -> start;
            case WEEK -> start.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
            case MONTH -> start.with(TemporalAdjusters.lastDayOfMonth());
        };
    }

    private static LocalDate min(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }
}
