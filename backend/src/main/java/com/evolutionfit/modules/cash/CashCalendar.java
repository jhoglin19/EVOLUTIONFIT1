package com.evolutionfit.modules.cash;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Jornadas de caja. Todas las horas son de Bogotá (las del reloj del gimnasio y las guardadas en la base).
 * La jornada del día D va desde el corte del día anterior hasta las 22:00 del día D (sin incluirla):
 * lo que pasa después del corte cuenta para el día siguiente.
 */
@Component
public class CashCalendar {

    private final LocalTime closingTime;

    @Autowired
    public CashCalendar(CashProperties properties) {
        this(properties.closingLocalTime());
    }

    public CashCalendar(LocalTime closingTime) {
        this.closingTime = closingTime;
    }

    public LocalTime closingTime() {
        return closingTime;
    }

    /** Momento del corte del día (las 22:00 de ese día). */
    public LocalDateTime cutoff(LocalDate day) {
        return day.atTime(closingTime);
    }

    /** Último día que ya debe estar cerrado: hoy si ya pasó el corte; si no, ayer. */
    public LocalDate lastDueDay(LocalDateTime now) {
        return now.toLocalTime().isBefore(closingTime) ? now.toLocalDate().minusDays(1) : now.toLocalDate();
    }

    /** Día de caja al que pertenece un momento: después del corte es el día siguiente. */
    public LocalDate cashDayOf(LocalDateTime moment) {
        return moment.toLocalTime().isBefore(closingTime) ? moment.toLocalDate() : moment.toLocalDate().plusDays(1);
    }

    /** Desde cuándo cuenta la caja: el corte del día anterior a la fecha de inicio. */
    public LocalDateTime cashStart(LocalDate startDate) {
        return cutoff(startDate.minusDays(1));
    }
}
