package com.evolutionfit.modules.memberships;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import com.evolutionfit.common.exception.BusinessException;

/**
 * Cálculo de fechas de membresías y congelaciones (docs/REGLAS_NEGOCIO.md, secciones 1 y 2).
 * Todas las fechas son días calendario e incluyen el primer y el último día.
 */
public final class MembershipDates {

    private MembershipDates() {
    }

    /** Vencimiento = inicio + duración − 1. Un plan de 1 día empieza y vence el mismo día. */
    public static LocalDate endDate(LocalDate start, int durationDays) {
        if (durationDays < 1) {
            throw new IllegalArgumentException("La duración debe ser de al menos 1 día");
        }
        return start.plusDays(durationDays - 1L);
    }

    /**
     * Inicio de una membresía nueva o de una renovación.
     *
     * @param today          fecha de hoy (del pago)
     * @param requestedStart fecha de inicio pedida (opcional, solo futura)
     * @param currentEndDate vencimiento de la membresía vigente del cliente, o null si no tiene
     */
    public static LocalDate newMembershipStart(LocalDate today, LocalDate requestedStart, LocalDate currentEndDate) {
        if (currentEndDate != null && !currentEndDate.isBefore(today)) {
            // Renovación antes de vencer: queda programada para el día siguiente al vencimiento.
            LocalDate next = currentEndDate.plusDays(1);
            if (requestedStart != null && !requestedStart.equals(next)) {
                throw new BusinessException("El cliente tiene una membresía vigente hasta el " + currentEndDate
                        + "; la nueva solo puede empezar el " + next);
            }
            return next;
        }
        if (requestedStart == null) {
            return today;
        }
        if (requestedStart.isBefore(today)) {
            throw new BusinessException("La fecha de inicio no puede ser anterior a hoy. "
                    + "Solo las membresías migradas del sistema anterior pueden empezar en el pasado");
        }
        return requestedStart;
    }

    /** Último día de una congelación de {@code days} días calendario seguidos. */
    public static LocalDate freezeEndDate(LocalDate start, int days) {
        if (days < 1) {
            throw new IllegalArgumentException("La congelación debe ser de al menos 1 día");
        }
        return start.plusDays(days - 1L);
    }

    /**
     * Días que se suman al vencimiento cuando se cancela una congelación.
     * Si no ha empezado, 0. Si está en curso, solo los días ya transcurridos (hasta ayer):
     * el cliente puede entrar desde el día de la cancelación.
     */
    public static int appliedDaysOnCancel(LocalDate freezeStart, LocalDate freezeEnd, LocalDate today) {
        if (today.isAfter(freezeEnd)) {
            throw new BusinessException("La congelación ya terminó; no se puede cancelar");
        }
        if (!today.isAfter(freezeStart)) {
            return 0;
        }
        return (int) ChronoUnit.DAYS.between(freezeStart, today);
    }

    /** Vencimiento efectivo = vencimiento base + días congelados aplicados. */
    public static LocalDate effectiveEndDate(LocalDate baseEndDate, int appliedFreezeDays) {
        return baseEndDate.plusDays(appliedFreezeDays);
    }
}
