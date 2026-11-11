package com.evolutionfit.modules.memberships;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.evolutionfit.modules.memberships.dto.AlertCountsResponse;
import com.evolutionfit.modules.memberships.dto.ExpirationAlertResponse;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Alertas de vencimiento dentro del sistema (docs/REGLAS_NEGOCIO.md, secciones 5.6 y 11). Se calculan con
 * las fechas, en hora de Bogotá; no se guardan ni tienen seguimiento. Un cliente sale de la alerta cuando
 * paga su renovación. Los clientes inactivos no aparecen.
 */
@Service
public class MembershipAlertService {

    private static final int MAX_PAGE_SIZE = 100;

    private final MembershipRepository membershipRepository;
    private final MembershipSupport support;
    private final MembershipProperties properties;

    MembershipAlertService(MembershipRepository membershipRepository, MembershipSupport support,
                           MembershipProperties properties) {
        this.membershipRepository = membershipRepository;
        this.support = support;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public Page<ExpirationAlertResponse> list(ExpirationAlertKind kind, String query, int page, int size) {
        LocalDate today = support.today();
        Sort sort = kind == ExpirationAlertKind.EXPIRING
                ? Sort.by("endDate").ascending().and(Sort.by("id").ascending())
                : Sort.by("endDate").descending().and(Sort.by("id").descending());
        Page<Membership> rows = membershipRepository.findAll(spec(kind, today, query),
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE), sort));
        Map<Long, Membership> pending = pendingRenewals(rows.getContent());
        return rows.map(m -> toResponse(m, pending.get(m.getClient().getId()), today));
    }

    @Transactional(readOnly = true)
    public AlertCountsResponse counts() {
        LocalDate today = support.today();
        return new AlertCountsResponse(
                membershipRepository.count(spec(ExpirationAlertKind.EXPIRING, today, null)),
                membershipRepository.count(spec(ExpirationAlertKind.EXPIRED, today, null)),
                properties.expiryWarningDays(), properties.recentlyExpiredDays());
    }

    /**
     * Membresías vigentes hoy, activas o congeladas. Como cada cliente tiene una sola vigente
     * (sección 1.1), es también el número de clientes.
     */
    @Transactional(readOnly = true)
    public long inForceCount(boolean frozen) {
        return membershipRepository.count(MembershipSpecifications.inForceToday(support.today(), frozen));
    }

    private Specification<Membership> spec(ExpirationAlertKind kind, LocalDate today, String query) {
        return kind == ExpirationAlertKind.EXPIRING
                ? MembershipSpecifications.expiringAlert(today, properties.expiryWarningDays(), query)
                : MembershipSpecifications.recentlyExpiredAlert(today, properties.recentlyExpiredDays(), query);
    }

    /** Una sola consulta por página. Cada cliente tiene como máximo una "siguiente" (sección 8.2). */
    private Map<Long, Membership> pendingRenewals(List<Membership> rows) {
        if (rows.isEmpty()) {
            return Map.of();
        }
        List<Long> clientIds = rows.stream().map(m -> m.getClient().getId()).distinct().toList();
        Map<Long, Membership> byClient = new LinkedHashMap<>();
        for (Membership p : membershipRepository.findByClientIdInAndStatusOrderByIdAsc(clientIds, MembershipStatus.PENDING)) {
            byClient.putIfAbsent(p.getClient().getId(), p);
        }
        return byClient;
    }

    private static ExpirationAlertResponse toResponse(Membership m, Membership pending, LocalDate today) {
        var c = m.getClient();
        return new ExpirationAlertResponse(m.getId(), c.getId(), c.getFirstName() + " " + c.getLastName(),
                c.getDocumentNumber(), c.getPhone(), m.getPlanName(), m.getStartDate(), m.getEndDate(),
                MembershipRules.resolve(m, today), ChronoUnit.DAYS.between(today, m.getEndDate()),
                pending == null ? null : pending.getId(), pending == null ? null : pending.getPlanName());
    }
}
