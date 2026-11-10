package com.evolutionfit.modules.memberships;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import com.evolutionfit.modules.clients.Client;
import com.evolutionfit.modules.plans.Plan;

/** Datos de prueba. "Hoy" es el lunes 9 de noviembre de 2026 en Bogotá. */
final class MembershipFixtures {

    static final LocalDate TODAY = LocalDate.of(2026, 11, 9);
    static final Clock CLOCK = Clock.fixed(TODAY.atTime(10, 0).atZone(ZoneId.of("America/Bogota")).toInstant(),
            ZoneId.of("America/Bogota"));

    private MembershipFixtures() {
    }

    static LocalDate day(int month, int dayOfMonth) {
        int year = month >= 11 ? 2026 : 2027;
        return LocalDate.of(year, month, dayOfMonth);
    }

    static Client client(long id, boolean active) {
        Client c = new Client();
        c.setId(id);
        c.setDocumentType("CC");
        c.setDocumentNumber("100" + id);
        c.setFirstName("Ana");
        c.setLastName("Pérez");
        c.setActive(active);
        return c;
    }

    static Plan plan(long id, String name, int days, String price, boolean active) {
        Plan p = new Plan();
        p.setId(id);
        p.setName(name);
        p.setDurationDays(days);
        p.setPrice(new BigDecimal(price));
        p.setActive(active);
        return p;
    }

    static Plan monthly() {
        return plan(2L, "Mensual", 30, "90000.00", true);
    }

    /** Membresía confirmada (pagada o migrada) de 30 días sin congelaciones. */
    static Membership confirmed(long id, Client client, LocalDate start) {
        Membership m = new Membership();
        m.setId(id);
        m.setClient(client);
        m.setPlan(monthly());
        m.setPlanName("Mensual");
        m.setPlanDurationDays(30);
        m.setPriceAtPurchase(new BigDecimal("90000.00"));
        m.setStartDate(start);
        m.setBaseEndDate(MembershipDates.endDate(start, 30));
        m.setEndDate(m.getBaseEndDate());
        m.setStatus(MembershipStatus.ACTIVE);
        m.setStatus(MembershipRules.resolve(m, TODAY));
        return m;
    }

    static Membership pending(long id, Client client, LocalDate start) {
        Membership m = confirmed(id, client, start);
        m.setStatus(MembershipStatus.PENDING);
        return m;
    }

    static MembershipFreeze freeze(long id, Membership m, LocalDate start, int days) {
        MembershipFreeze f = new MembershipFreeze();
        f.setId(id);
        f.setMembership(m);
        f.setStartDate(start);
        f.setEndDate(MembershipDates.freezeEndDate(start, days));
        f.setAppliedDays(days);
        f.setReason("Viaje");
        f.setStatus(MembershipRules.resolve(f, TODAY));
        m.getFreezes().add(f);
        m.setEndDate(MembershipDates.effectiveEndDate(m.getBaseEndDate(), MembershipRules.appliedFreezeDays(m)));
        return f;
    }

    static void cancelFreeze(MembershipFreeze f, LocalDate cancelledOn, int appliedDays) {
        f.setStatus(FreezeStatus.CANCELLED);
        f.setCancelledAt(LocalDateTime.of(cancelledOn, java.time.LocalTime.NOON));
        f.setCancellationReason("Error");
        f.setAppliedDays(appliedDays);
        Membership m = f.getMembership();
        m.setEndDate(MembershipDates.effectiveEndDate(m.getBaseEndDate(), MembershipRules.appliedFreezeDays(m)));
    }
}
