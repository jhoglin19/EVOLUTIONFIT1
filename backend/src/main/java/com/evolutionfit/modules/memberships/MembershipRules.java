package com.evolutionfit.modules.memberships;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Optional;

/**
 * Estado calculado de membresías y congelaciones. Las decisiones del sistema (una sola activa,
 * congelar, listados, y más adelante el acceso) se toman con estas funciones, no con la columna status.
 */
public final class MembershipRules {

    private MembershipRules() {
    }

    // ---- membresías ----

    /** Pagada o migrada (en la fase 4: todo lo que no esté pendiente de pago ni anulado). */
    public static boolean isConfirmed(Membership m) {
        return m.getStatus() != MembershipStatus.PENDING && m.getStatus() != MembershipStatus.CANCELLED;
    }

    /** Vigente hoy: confirmada y hoy está entre el inicio y el vencimiento (incluidos). Puede estar congelada. */
    public static boolean isInForce(Membership m, LocalDate today) {
        return isConfirmed(m) && !today.isBefore(m.getStartDate()) && !today.isAfter(m.getEndDate());
    }

    /** "Siguiente": pendiente de pago, o confirmada con inicio futuro (programada). */
    public static boolean isNext(Membership m, LocalDate today) {
        return m.getStatus() == MembershipStatus.PENDING
                || (isConfirmed(m) && m.getStartDate().isAfter(today));
    }

    public static boolean isFrozenOn(Membership m, LocalDate today) {
        return m.getFreezes().stream().anyMatch(f -> coversDay(f, today));
    }

    public static MembershipStatus resolve(Membership m, LocalDate today) {
        if (!isConfirmed(m)) {
            return m.getStatus();
        }
        if (today.isBefore(m.getStartDate())) {
            return MembershipStatus.SCHEDULED;
        }
        if (today.isAfter(m.getEndDate())) {
            return MembershipStatus.EXPIRED;
        }
        return isFrozenOn(m, today) ? MembershipStatus.FROZEN : MembershipStatus.ACTIVE;
    }

    public static Optional<Membership> current(Collection<Membership> memberships, LocalDate today) {
        return memberships.stream().filter(m -> isInForce(m, today)).findFirst();
    }

    public static Optional<Membership> next(Collection<Membership> memberships, LocalDate today) {
        return memberships.stream().filter(m -> isNext(m, today)).findFirst();
    }

    public static int appliedFreezeDays(Membership m) {
        return m.getFreezes().stream().mapToInt(MembershipFreeze::getAppliedDays).sum();
    }

    /** Ya usó su única congelación (aquí o en el sistema anterior). */
    public static boolean freezeUsed(Membership m) {
        return m.isFreezeUsedInPreviousSystem()
                || m.getFreezes().stream().anyMatch(MembershipFreeze::countsTowardLimit);
    }

    /** Se puede registrar una congelación: vigente y sin congelación usada. */
    public static boolean canFreeze(Membership m, LocalDate today) {
        return isInForce(m, today) && !freezeUsed(m);
    }

    // ---- congelaciones ----

    /** Un día está congelado si cae dentro de los días aplicados de una congelación. */
    public static boolean coversDay(MembershipFreeze f, LocalDate day) {
        if (f.getAppliedDays() < 1) {
            return false;
        }
        LocalDate lastApplied = f.getStartDate().plusDays(f.getAppliedDays() - 1L);
        return !day.isBefore(f.getStartDate()) && !day.isAfter(lastApplied);
    }

    public static FreezeStatus resolve(MembershipFreeze f, LocalDate today) {
        if (f.isCancelled()) {
            return FreezeStatus.CANCELLED;
        }
        if (today.isBefore(f.getStartDate())) {
            return FreezeStatus.PENDING;
        }
        return today.isAfter(f.getEndDate()) ? FreezeStatus.COMPLETED : FreezeStatus.ACTIVE;
    }
}
