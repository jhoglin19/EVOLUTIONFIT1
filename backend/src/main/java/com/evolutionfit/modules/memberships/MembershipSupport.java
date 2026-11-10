package com.evolutionfit.modules.memberships;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.evolutionfit.common.exception.ResourceNotFoundException;
import com.evolutionfit.modules.audit.AuditService;
import com.evolutionfit.modules.clients.Client;
import com.evolutionfit.modules.clients.ClientRepository;

import org.springframework.stereotype.Component;

/**
 * Operaciones compartidas por los servicios de membresías y congelaciones.
 * Se usa siempre dentro de una transacción abierta por esos servicios.
 */
@Component
class MembershipSupport {

    static final String AUDIT_MEMBERSHIP = "MEMBERSHIP";
    static final String AUDIT_FREEZE = "MEMBERSHIP_FREEZE";

    private final ClientRepository clientRepository;
    private final MembershipRepository membershipRepository;
    private final AuditService auditService;
    private final Clock clock;

    MembershipSupport(ClientRepository clientRepository, MembershipRepository membershipRepository,
                      AuditService auditService, Clock clock) {
        this.clientRepository = clientRepository;
        this.membershipRepository = membershipRepository;
        this.auditService = auditService;
        this.clock = clock;
    }

    LocalDate today() {
        return LocalDate.now(clock);
    }

    LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    /**
     * Bloquea al cliente (SELECT ... FOR UPDATE) hasta que termine la transacción. Debe ser la primera
     * lectura de toda operación que cambie sus membresías: así la regla "una sola activa y una sola
     * siguiente" se comprueba sin que otra solicitud simultánea pueda colarse.
     */
    Client lockClient(Long clientId) {
        return clientRepository.findByIdForUpdate(clientId)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente no encontrado"));
    }

    List<Membership> clientMemberships(Long clientId) {
        return membershipRepository.findByClientIdOrderByStartDateDescIdDesc(clientId);
    }

    /** Vencimiento = base + días congelados aplicados, y estado guardado al día. */
    void recalculate(Membership m, LocalDate today) {
        m.setEndDate(MembershipDates.effectiveEndDate(m.getBaseEndDate(), MembershipRules.appliedFreezeDays(m)));
        m.setStatus(MembershipRules.resolve(m, today));
        m.getFreezes().forEach(f -> f.setStatus(MembershipRules.resolve(f, today)));
    }

    /**
     * Mueve la membresía siguiente del cliente (pendiente de pago o programada) para que empiece en
     * {@code newStart}, conservando su duración. Se usa cuando cambia el vencimiento de la vigente
     * (congelación) o cuando la vigente se anula (la siguiente pasa a empezar hoy).
     */
    void rescheduleNext(Membership current, LocalDate newStart, Long userId) {
        LocalDate today = today();
        Optional<Membership> next = clientMemberships(current.getClient().getId()).stream()
                .filter(m -> !m.getId().equals(current.getId()))
                .filter(m -> MembershipRules.isNext(m, today))
                .findFirst();
        if (next.isEmpty() || next.get().getStartDate().equals(newStart)) {
            return;
        }
        Membership m = next.get();
        MembershipSnapshot before = MembershipSnapshot.of(m);
        m.setStartDate(newStart);
        m.setBaseEndDate(MembershipDates.endDate(newStart, m.getPlanDurationDays()));
        recalculate(m, today);
        membershipRepository.save(m);
        auditService.record(AUDIT_MEMBERSHIP, m.getId(), AuditService.ACTION_RESCHEDULE,
                before, MembershipSnapshot.of(m), userId);
    }

    void audit(String entity, Long id, String action, Object before, Object after, Long userId) {
        auditService.record(entity, id, action, before, after, userId);
    }

    /** Lo que queda en audit_logs de una membresía. */
    record MembershipSnapshot(Long clientId, String planName, LocalDate startDate, LocalDate endDate,
                              LocalDate baseEndDate, MembershipStatus status, BigDecimal priceAtPurchase,
                              boolean migrated, String migrationNote, boolean freezeUsedInPreviousSystem,
                              String cancellationReason) {

        static MembershipSnapshot of(Membership m) {
            return new MembershipSnapshot(m.getClient().getId(), m.getPlanName(), m.getStartDate(),
                    m.getEndDate(), m.getBaseEndDate(), m.getStatus(), m.getPriceAtPurchase(), m.isMigrated(),
                    m.getMigrationNote(), m.isFreezeUsedInPreviousSystem(), m.getCancellationReason());
        }
    }

    /** Lo que queda en audit_logs de una congelación, con el vencimiento resultante de la membresía. */
    record FreezeSnapshot(Long membershipId, LocalDate startDate, LocalDate endDate, int appliedDays,
                          FreezeStatus status, String reason, String cancellationReason,
                          LocalDate membershipEndDate) {

        static FreezeSnapshot of(MembershipFreeze f) {
            return new FreezeSnapshot(f.getMembership().getId(), f.getStartDate(), f.getEndDate(),
                    f.getAppliedDays(), f.getStatus(), f.getReason(), f.getCancellationReason(),
                    f.getMembership().getEndDate());
        }
    }
}
