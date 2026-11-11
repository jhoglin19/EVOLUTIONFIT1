package com.evolutionfit.modules.memberships;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.evolutionfit.modules.clients.Client;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

import org.springframework.data.jpa.domain.Specification;

/**
 * Filtros de listados calculados con fechas. De la columna status solo se usan los estados que fija
 * un usuario (PENDING y CANCELLED); los que dependen del día (programada, activa, congelada,
 * vencida, por vencer) se deducen de start_date, end_date y las congelaciones.
 */
final class MembershipSpecifications {

    private MembershipSpecifications() {
    }

    static Specification<Membership> filter(MembershipStatus state, boolean expiringSoon, String query,
                                            LocalDate today, int warningDays) {
        return (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (state != null) {
                predicates.add(byState(state, root, cq, cb, today));
            }
            if (expiringSoon) {
                predicates.add(expiringSoon(root, cq, cb, today, warningDays));
            }
            if (query != null && !query.isBlank()) {
                predicates.add(matchesClient(root, cb, query.trim()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static Predicate confirmed(Root<Membership> root, CriteriaBuilder cb) {
        return root.get("status").in(MembershipStatus.PENDING, MembershipStatus.CANCELLED).not();
    }

    private static Predicate inForce(Root<Membership> root, CriteriaBuilder cb, LocalDate today) {
        return cb.and(confirmed(root, cb),
                cb.lessThanOrEqualTo(root.get("startDate"), today),
                cb.greaterThanOrEqualTo(root.get("endDate"), today));
    }

    /** Una congelación no cancelada que cubre hoy (las canceladas solo cubren días ya pasados). */
    private static Predicate frozenToday(Root<Membership> root, jakarta.persistence.criteria.CriteriaQuery<?> cq,
                                         CriteriaBuilder cb, LocalDate today) {
        Subquery<Long> sub = cq.subquery(Long.class);
        Root<MembershipFreeze> f = sub.from(MembershipFreeze.class);
        sub.select(f.get("id")).where(
                cb.equal(f.get("membership"), root),
                cb.notEqual(f.get("status"), FreezeStatus.CANCELLED),
                cb.lessThanOrEqualTo(f.get("startDate"), today),
                cb.greaterThanOrEqualTo(f.get("endDate"), today));
        return cb.exists(sub);
    }

    private static Predicate byState(MembershipStatus state, Root<Membership> root,
                                     jakarta.persistence.criteria.CriteriaQuery<?> cq,
                                     CriteriaBuilder cb, LocalDate today) {
        return switch (state) {
            case PENDING, CANCELLED -> cb.equal(root.get("status"), state);
            case SCHEDULED -> cb.and(confirmed(root, cb), cb.greaterThan(root.get("startDate"), today));
            case EXPIRED -> cb.and(confirmed(root, cb), cb.lessThan(root.get("endDate"), today));
            case FROZEN -> cb.and(inForce(root, cb, today), frozenToday(root, cq, cb, today));
            case ACTIVE -> cb.and(inForce(root, cb, today), frozenToday(root, cq, cb, today).not());
        };
    }

    /**
     * Vigentes que vencen entre hoy y hoy + N días y cuyo cliente no tiene una renovación ya pagada
     * (programada). Una renovación pendiente de pago no quita el aviso.
     */
    private static Predicate expiringSoon(Root<Membership> root, jakarta.persistence.criteria.CriteriaQuery<?> cq,
                                          CriteriaBuilder cb, LocalDate today, int warningDays) {
        return cb.and(inForce(root, cb, today),
                cb.lessThanOrEqualTo(root.get("endDate"), today.plusDays(warningDays)),
                cb.exists(renewal(root, cq, cb)).not());
    }

    /**
     * Alerta "por vencer" (docs/REGLAS_NEGOCIO.md, sección 11): el mismo filtro del listado de membresías,
     * solo para clientes activos.
     */
    static Specification<Membership> expiringAlert(LocalDate today, int warningDays, String query) {
        return (root, cq, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(expiringSoon(root, cq, cb, today, warningDays));
            p.add(cb.isTrue(root.get("client").get("active")));
            if (query != null && !query.isBlank()) {
                p.add(matchesClient(root, cb, query.trim()));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
    }

    /**
     * Alerta "vencida reciente": confirmada, venció entre hoy − días y ayer, y el cliente no tiene otra
     * confirmada que empiece después de ese vencimiento (no renovó). Una renovación pendiente de pago no
     * quita la alerta. Solo clientes activos.
     */
    static Specification<Membership> recentlyExpiredAlert(LocalDate today, int days, String query) {
        return (root, cq, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(confirmed(root, cb));
            p.add(cb.between(root.get("endDate"), today.minusDays(days), today.minusDays(1)));
            p.add(cb.exists(renewal(root, cq, cb)).not());
            p.add(cb.isTrue(root.get("client").get("active")));
            if (query != null && !query.isBlank()) {
                p.add(matchesClient(root, cb, query.trim()));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
    }

    /** Vigentes hoy (activas o congeladas), para contar clientes con membresía en el panel. */
    static Specification<Membership> inForceToday(LocalDate today, boolean frozen) {
        return (root, cq, cb) -> frozen
                ? cb.and(inForce(root, cb, today), frozenToday(root, cq, cb, today))
                : cb.and(inForce(root, cb, today), frozenToday(root, cq, cb, today).not());
    }

    /** Otra membresía confirmada del mismo cliente que empieza después del vencimiento de esta. */
    private static Subquery<Long> renewal(Root<Membership> root, jakarta.persistence.criteria.CriteriaQuery<?> cq,
                                          CriteriaBuilder cb) {
        Subquery<Long> renewal = cq.subquery(Long.class);
        Root<Membership> other = renewal.from(Membership.class);
        renewal.select(other.get("id")).where(
                cb.equal(other.get("client"), root.get("client")),
                other.get("status").in(MembershipStatus.PENDING, MembershipStatus.CANCELLED).not(),
                cb.greaterThan(other.get("startDate"), root.get("endDate")));
        return renewal;
    }

    private static Predicate matchesClient(Root<Membership> root, CriteriaBuilder cb, String query) {
        Join<Membership, Client> client = root.join("client");
        String like = "%" + query.toLowerCase(Locale.ROOT) + "%";
        return cb.or(
                cb.like(client.get("documentNumber"), "%" + query + "%"),
                cb.like(cb.lower(cb.concat(cb.concat(client.get("firstName"), " "), client.get("lastName"))), like),
                cb.like(cb.lower(cb.concat(cb.concat(client.get("lastName"), " "), client.get("firstName"))), like));
    }
}
