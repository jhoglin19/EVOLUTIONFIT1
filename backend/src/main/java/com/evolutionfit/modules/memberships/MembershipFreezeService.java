package com.evolutionfit.modules.memberships;

import java.time.LocalDate;
import java.util.Map;

import com.evolutionfit.common.exception.BusinessException;
import com.evolutionfit.common.exception.ConflictException;
import com.evolutionfit.common.exception.ResourceNotFoundException;
import com.evolutionfit.modules.audit.AuditService;
import com.evolutionfit.modules.memberships.MembershipSupport.FreezeSnapshot;
import com.evolutionfit.modules.memberships.dto.CreateFreezeRequest;
import com.evolutionfit.modules.memberships.dto.ExtendFreezeRequest;
import com.evolutionfit.modules.memberships.dto.MembershipResponse;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Congelaciones (docs/REGLAS_NEGOCIO.md, sección 2): motivo obligatorio, máximo 30 días calendario
 * seguidos, una por membresía, los días se suman al vencimiento y la membresía siguiente se corre.
 * Recepción registra; solo ADMIN amplía o cancela (lo exige el controlador).
 */
@Service
public class MembershipFreezeService {

    private final MembershipRepository membershipRepository;
    private final MembershipFreezeRepository freezeRepository;
    private final MembershipSupport support;
    private final MembershipProperties properties;

    public MembershipFreezeService(MembershipRepository membershipRepository,
                                   MembershipFreezeRepository freezeRepository,
                                   MembershipSupport support, MembershipProperties properties) {
        this.membershipRepository = membershipRepository;
        this.freezeRepository = freezeRepository;
        this.support = support;
        this.properties = properties;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public MembershipResponse create(Long membershipId, CreateFreezeRequest request, Long currentUserId) {
        Long clientId = membershipRepository.findClientIdById(membershipId)
                .orElseThrow(() -> new ResourceNotFoundException("Membresía no encontrada"));
        support.lockClient(clientId);
        Membership m = membershipRepository.findById(membershipId)
                .orElseThrow(() -> new ResourceNotFoundException("Membresía no encontrada"));
        LocalDate today = support.today();

        requireNotCancelled(m);
        if (m.getStatus() == MembershipStatus.PENDING) {
            throw new BusinessException("La membresía está pendiente de pago; solo se congelan membresías pagadas o migradas");
        }
        if (!MembershipRules.isInForce(m, today)) {
            throw new BusinessException("Solo se puede congelar la membresía vigente");
        }
        if (m.isFreezeUsedInPreviousSystem()) {
            throw new ConflictException("El cliente ya usó la congelación de esta membresía en el sistema anterior");
        }
        if (MembershipRules.freezeUsed(m)) {
            throw new ConflictException("Esta membresía ya tuvo su congelación (se permite una por membresía)");
        }
        requireMaxDays(request.days());
        if (request.startDate().isBefore(today)) {
            throw new BusinessException("La congelación no puede empezar en una fecha pasada");
        }
        if (request.startDate().isAfter(m.getEndDate())) {
            throw new BusinessException("La congelación debe empezar a más tardar el día del vencimiento ("
                    + m.getEndDate() + ")");
        }

        LocalDate endBefore = m.getEndDate();
        MembershipFreeze f = new MembershipFreeze();
        f.setMembership(m);
        f.setStartDate(request.startDate());
        f.setEndDate(MembershipDates.freezeEndDate(request.startDate(), request.days()));
        f.setAppliedDays(request.days());
        f.setReason(request.reason().trim());
        f.setCreatedBy(currentUserId);
        f.setStatus(MembershipRules.resolve(f, today));
        m.getFreezes().add(f);

        support.recalculate(m, today);
        MembershipFreeze saved = freezeRepository.save(f);
        membershipRepository.save(m);
        support.audit(MembershipSupport.AUDIT_FREEZE, saved.getId(), AuditService.ACTION_CREATE,
                Map.of("membershipEndDate", endBefore), FreezeSnapshot.of(saved), currentUserId);
        support.rescheduleNext(m, m.getEndDate().plusDays(1), currentUserId);
        return MembershipResponse.from(m, today);
    }

    /** {@code totalDays} es la nueva duración total, mayor que la actual y sin pasar el máximo. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public MembershipResponse extend(Long freezeId, ExtendFreezeRequest request, Long currentUserId) {
        MembershipFreeze f = lockAndLoad(freezeId);
        Membership m = f.getMembership();
        LocalDate today = support.today();

        requireNotCancelled(m);
        if (f.isCancelled()) {
            throw new BusinessException("La congelación está cancelada; no se puede ampliar");
        }
        if (today.isAfter(f.getEndDate())) {
            throw new BusinessException("La congelación ya terminó; no se puede ampliar");
        }
        if (request.totalDays() <= f.totalDays()) {
            throw new BusinessException("El nuevo total debe ser mayor que los días actuales (" + f.totalDays() + ")");
        }
        requireMaxDays(request.totalDays());

        FreezeSnapshot before = FreezeSnapshot.of(f);
        f.setEndDate(MembershipDates.freezeEndDate(f.getStartDate(), request.totalDays()));
        f.setAppliedDays(request.totalDays());
        support.recalculate(m, today);
        freezeRepository.save(f);
        membershipRepository.save(m);
        support.audit(MembershipSupport.AUDIT_FREEZE, f.getId(), AuditService.ACTION_EXTEND,
                before, Map.of("freeze", FreezeSnapshot.of(f), "reason", request.reason().trim()), currentUserId);
        support.rescheduleNext(m, m.getEndDate().plusDays(1), currentUserId);
        return MembershipResponse.from(m, today);
    }

    /**
     * Cancelación (solo ADMIN, motivo obligatorio). Antes de empezar no suma días ni cuenta como la
     * congelación de la membresía. En curso, suma solo los días ya transcurridos y sí cuenta.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public MembershipResponse cancel(Long freezeId, String reason, Long currentUserId) {
        MembershipFreeze f = lockAndLoad(freezeId);
        Membership m = f.getMembership();
        LocalDate today = support.today();

        requireNotCancelled(m);
        if (f.isCancelled()) {
            throw new ConflictException("La congelación ya está cancelada");
        }
        int applied = MembershipDates.appliedDaysOnCancel(f.getStartDate(), f.getEndDate(), today);

        FreezeSnapshot before = FreezeSnapshot.of(f);
        f.setAppliedDays(applied);
        f.setStatus(FreezeStatus.CANCELLED);
        f.setCancelledAt(support.now());
        f.setCancelledBy(currentUserId);
        f.setCancellationReason(reason.trim());
        support.recalculate(m, today);
        freezeRepository.save(f);
        membershipRepository.save(m);
        support.audit(MembershipSupport.AUDIT_FREEZE, f.getId(), AuditService.ACTION_CANCEL,
                before, FreezeSnapshot.of(f), currentUserId);
        support.rescheduleNext(m, m.getEndDate().plusDays(1), currentUserId);
        return MembershipResponse.from(m, today);
    }

    // ---- helpers ----

    private MembershipFreeze lockAndLoad(Long freezeId) {
        Long clientId = freezeRepository.findClientIdById(freezeId)
                .orElseThrow(() -> new ResourceNotFoundException("Congelación no encontrada"));
        support.lockClient(clientId);
        return freezeRepository.findById(freezeId)
                .orElseThrow(() -> new ResourceNotFoundException("Congelación no encontrada"));
    }

    private void requireMaxDays(int days) {
        if (days > properties.maxFreezeDays()) {
            throw new BusinessException("La congelación no puede superar " + properties.maxFreezeDays() + " días");
        }
    }

    private static void requireNotCancelled(Membership m) {
        if (m.getStatus() == MembershipStatus.CANCELLED) {
            throw new BusinessException("La membresía está anulada");
        }
    }
}
