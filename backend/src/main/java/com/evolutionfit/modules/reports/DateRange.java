package com.evolutionfit.modules.reports;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import com.evolutionfit.common.exception.BusinessException;

/**
 * Rango de días calendario en hora de Bogotá, con los dos extremos incluidos. Las fechas y horas se
 * guardan en hora de Bogotá, así que el rango en la base es [desde 00:00, hasta + 1 día 00:00): una
 * comparación directa sobre la columna, que usa su índice (nunca DATE(columna) en el WHERE).
 */
public record DateRange(LocalDate from, LocalDate to) {

    public static DateRange of(LocalDate from, LocalDate to, int maxDays) {
        if (from == null || to == null) {
            throw new BusinessException("Indica la fecha inicial y la final");
        }
        if (to.isBefore(from)) {
            throw new BusinessException("La fecha final no puede ser anterior a la inicial");
        }
        DateRange range = new DateRange(from, to);
        if (range.days() > maxDays) {
            throw new BusinessException("El rango no puede superar " + maxDays + " días");
        }
        return range;
    }

    public static DateRange day(LocalDate day) {
        return new DateRange(day, day);
    }

    public LocalDateTime start() {
        return from.atStartOfDay();
    }

    /** Primer instante que ya no pertenece al rango. */
    public LocalDateTime endExclusive() {
        return to.plusDays(1).atStartOfDay();
    }

    public long days() {
        return ChronoUnit.DAYS.between(from, to) + 1;
    }
}
