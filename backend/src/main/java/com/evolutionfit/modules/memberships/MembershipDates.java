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

    /**
     * Inicio definitivo al pagar una membresía pendiente (docs/REGLAS_NEGOCIO.md, secciones 1 y 8):
     * <ol>
     *   <li>Si el cliente tiene una vigente, el día siguiente a su vencimiento (incluye días congelados).</li>
     *   <li>Si no, la fecha pedida al vender, si todavía es futura.</li>
     *   <li>Si no, el día del pago (también cuando la fecha pedida ya pasó).</li>
     * </ol>
     */
    public static LocalDate activationStart(LocalDate today, LocalDate requestedStart, LocalDate currentEndDate) {
        if (currentEndDate != null && !currentEndDate.isBefore(today)) {
            return currentEndDate.plusDays(1);
        }
        if (requestedStart != null && requestedStart.isAfter(today)) {
            return requestedStart;
        }
        return today;
    }

    /** La fecha pedida al vender ya pasó y el cliente no tiene vigente: se empieza el día del pago. */
    public static boolean requestedStartPassed(LocalDate today, LocalDate requestedStart, LocalDate currentEndDate) {
        boolean hasCurrent = currentEndDate != null && !currentEndDate.isBefore(today);
        return !hasCurrent && requestedStart != null && requestedStart.isBefore(today);
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
