package com.evolutionfit.modules.memberships;

import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import com.evolutionfit.common.exception.BusinessException;
import com.evolutionfit.common.exception.ConflictException;
import com.evolutionfit.common.exception.ResourceNotFoundException;
import com.evolutionfit.modules.audit.AuditService;
import com.evolutionfit.modules.clients.Client;
import com.evolutionfit.modules.clients.ClientRepository;
import com.evolutionfit.modules.memberships.MembershipSupport.FreezeSnapshot;
import com.evolutionfit.modules.memberships.MembershipSupport.MembershipSnapshot;
import com.evolutionfit.modules.memberships.dto.CreateMembershipRequest;
import com.evolutionfit.modules.memberships.dto.CurrentMembershipResponse;
import com.evolutionfit.modules.memberships.dto.MembershipPreviewResponse;
import com.evolutionfit.modules.memberships.dto.MembershipResponse;
import com.evolutionfit.modules.memberships.dto.MigratedMembershipRequest;
import com.evolutionfit.modules.plans.Plan;
import com.evolutionfit.modules.plans.PlanRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Membresías (docs/REGLAS_NEGOCIO.md, sección 1). Las escrituras usan READ COMMITTED y bloquean al
 * cliente antes de leer sus membresías: tras esperar el bloqueo se leen los datos ya confirmados por
 * la otra solicitud.
 */
@Service
public class MembershipService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final String NEXT_EXISTS = "El cliente ya tiene una membresía siguiente (pendiente de pago "
            + "o programada). Solo se permite una";

    private final MembershipRepository membershipRepository;
    private final ClientRepository clientRepository;
    private final PlanRepository planRepository;
    private final MembershipSupport support;
    private final MembershipProperties properties;

    public MembershipService(MembershipRepository membershipRepository, ClientRepository clientRepository,
                             PlanRepository planRepository, MembershipSupport support,
                             MembershipProperties properties) {
        this.membershipRepository = membershipRepository;
        this.clientRepository = clientRepository;
        this.planRepository = planRepository;
        this.support = support;
        this.properties = properties;
    }

    // ---- consultas ----

    @Transactional(readOnly = true)
    public Page<MembershipResponse> list(MembershipStatus state, boolean expiringSoon, String query,
                                         int page, int size) {
        LocalDate today = support.today();
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Sort sort = expiringSoon
                ? Sort.by("endDate").ascending().and(Sort.by("id").ascending())
                : Sort.by("startDate").descending().and(Sort.by("id").descending());
        return membershipRepository.findAll(
                        MembershipSpecifications.filter(state, expiringSoon, query, today,
                                properties.expiryWarningDays()),
                        PageRequest.of(safePage, safeSize, sort))
                .map(m -> MembershipResponse.from(m, today));
    }

    @Transactional(readOnly = true)
    public MembershipResponse get(Long id) {
        return MembershipResponse.from(findOrThrow(id), support.today());
    }

    @Transactional(readOnly = true)
    public List<MembershipResponse> history(Long clientId) {
        requireClientExists(clientId);
        LocalDate today = support.today();
        return support.clientMemberships(clientId).stream().map(m -> MembershipResponse.from(m, today)).toList();
    }

    @Transactional(readOnly = true)
    public CurrentMembershipResponse current(Long clientId) {
        requireClientExists(clientId);
        LocalDate today = support.today();
        List<Membership> memberships = support.clientMemberships(clientId);
        return new CurrentMembershipResponse(
                MembershipRules.current(memberships, today).map(m -> MembershipResponse.from(m, today)).orElse(null),
                MembershipRules.next(memberships, today).map(m -> MembershipResponse.from(m, today)).orElse(null));
    }

    /** Fechas que tendría la membresía si se registrara ahora. No guarda nada. */
    @Transactional(readOnly = true)
    public MembershipPreviewResponse preview(Long clientId, Long planId, LocalDate startDate) {
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new ResourceNotFoundException("Cliente no encontrado"));
        requireActiveClient(client);
        Plan plan = sellablePlan(planId);
        LocalDate today = support.today();
        List<Membership> memberships = support.clientMemberships(client.getId());
        LocalDate start = startFor(memberships, startDate, today);
        Optional<Membership> current = MembershipRules.current(memberships, today);
        return new MembershipPreviewResponse(plan.getName(), plan.getPrice(), start,
                MembershipDates.endDate(start, plan.getDurationDays()), current.isPresent(),
                current.map(Membership::getEndDate).orElse(null));
    }

    // ---- escrituras ----

    /**
     * Membresía nueva o renovación. Queda PENDIENTE de pago; sus fechas son provisionales hasta que el
     * pago total la active (fase 5). Si el cliente tiene una vigente, empieza el día siguiente al vencimiento.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public MembershipResponse create(CreateMembershipRequest request, Long currentUserId) {
        Client client = support.lockClient(request.clientId());
        requireActiveClient(client);
        Plan plan = sellablePlan(request.planId());
        LocalDate today = support.today();
        List<Membership> memberships = support.clientMemberships(client.getId());
        LocalDate start = startFor(memberships, request.startDate(), today);

        Membership m = new Membership();
        m.setClient(client);
        m.setPlan(plan);
        m.setPlanName(plan.getName());
        m.setPlanDurationDays(plan.getDurationDays());
        m.setPriceAtPurchase(plan.getPrice());
        m.setStartDate(start);
        m.setBaseEndDate(MembershipDates.endDate(start, plan.getDurationDays()));
        m.setEndDate(m.getBaseEndDate());
        m.setStatus(MembershipStatus.PENDING);
        m.setNotes(blankToNull(request.notes()));
        m.setCreatedBy(currentUserId);
        Membership saved = membershipRepository.save(m);
        support.audit(MembershipSupport.AUDIT_MEMBERSHIP, saved.getId(), AuditService.ACTION_CREATE,
                null, MembershipSnapshot.of(saved), currentUserId);
        return MembershipResponse.from(saved, today);
    }

    /**
     * Membresía traída del sistema anterior (solo ADMIN, lo exige el controlador): puede empezar en el
     * pasado, conserva el vencimiento real y no exige pago. Solo se migran membresías vigentes.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public MembershipResponse createMigrated(MigratedMembershipRequest request, Long currentUserId) {
        Client client = support.lockClient(request.clientId());
        requireActiveClient(client);
        Plan plan = planRepository.findById(request.planId())
                .orElseThrow(() -> new ResourceNotFoundException("Plan no encontrado"));
        LocalDate today = support.today();

        if (request.startDate().isAfter(today)) {
            throw new BusinessException("La fecha de inicio de una membresía migrada no puede ser futura");
        }
        if (request.endDate().isBefore(request.startDate())) {
            throw new BusinessException("El vencimiento no puede ser anterior a la fecha de inicio");
        }
        if (request.endDate().isBefore(today)) {
            throw new BusinessException("Solo se migran membresías vigentes: el vencimiento no puede ser anterior a hoy");
        }
        boolean hasCurrentOrNext = support.clientMemberships(client.getId()).stream()
                .anyMatch(m -> MembershipRules.isInForce(m, today) || MembershipRules.isNext(m, today));
        if (hasCurrentOrNext) {
            throw new ConflictException("El cliente ya tiene una membresía vigente, programada o pendiente de pago");
        }

        Membership m = new Membership();
        m.setClient(client);
        m.setPlan(plan);
        m.setPlanName(plan.getName());
        m.setPlanDurationDays(plan.getDurationDays());
        m.setPriceAtPurchase((request.price() != null ? request.price() : plan.getPrice())
                .setScale(2, RoundingMode.HALF_UP));
        m.setStartDate(request.startDate());
        m.setBaseEndDate(request.endDate());
        m.setEndDate(request.endDate());
        m.setMigrated(true);
        m.setMigrationNote(request.migrationNote().trim());
        m.setFreezeUsedInPreviousSystem(request.freezeUsedInPreviousSystem());
        m.setNotes(blankToNull(request.notes()));
        m.setCreatedBy(currentUserId);
        // Confirmada desde el inicio (no pasa por PENDING): el estado real lo da el cálculo por fechas.
        m.setStatus(MembershipStatus.ACTIVE);
        m.setStatus(MembershipRules.resolve(m, today));
        Membership saved = membershipRepository.save(m);
        support.audit(MembershipSupport.AUDIT_MEMBERSHIP, saved.getId(), AuditService.ACTION_CREATE,
                null, MembershipSnapshot.of(saved), currentUserId);
        return MembershipResponse.from(saved, today);
    }

    /**
     * Anulación (solo ADMIN, motivo obligatorio). No borra la membresía. Si era la vigente y el cliente
     * tiene una siguiente, esa pasa a empezar hoy. Cambiar de plan = anular y registrar otra.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public MembershipResponse cancel(Long id, String reason, Long currentUserId) {
        Long clientId = membershipRepository.findClientIdById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Membresía no encontrada"));
        support.lockClient(clientId);
        Membership m = findOrThrow(id);
        if (m.getStatus() == MembershipStatus.CANCELLED) {
            throw new ConflictException("La membresía ya está anulada");
        }
        LocalDate today = support.today();
        boolean wasInForce = MembershipRules.isInForce(m, today);

        MembershipSnapshot before = MembershipSnapshot.of(m);
        cancelOpenFreezes(m, reason.trim(), today, currentUserId);
        m.setStatus(MembershipStatus.CANCELLED);
        m.setCancelledAt(support.now());
        m.setCancelledBy(currentUserId);
        m.setCancellationReason(reason.trim());
        support.recalculate(m, today);
        Membership saved = membershipRepository.save(m);
        support.audit(MembershipSupport.AUDIT_MEMBERSHIP, id, AuditService.ACTION_CANCEL,
                before, MembershipSnapshot.of(saved), currentUserId);

        if (wasInForce) {
            support.rescheduleNext(saved, today, currentUserId);
        }
        return MembershipResponse.from(saved, today);
    }

    /**
     * Copia el estado calculado en la columna status. Lo llama la tarea diaria y el arranque; las
     * decisiones nunca dependen de esta copia.
     */
    @Transactional
    public int syncStoredStatuses() {
        LocalDate today = support.today();
        int changed = 0;
        for (Membership m : membershipRepository.findByStatusIn(
                EnumSet.of(MembershipStatus.SCHEDULED, MembershipStatus.ACTIVE, MembershipStatus.FROZEN))) {
            MembershipStatus resolved = MembershipRules.resolve(m, today);
            if (resolved != m.getStatus()) {
                m.setStatus(resolved);
                changed++;
            }
            for (MembershipFreeze f : m.getFreezes()) {
                FreezeStatus freezeStatus = MembershipRules.resolve(f, today);
                if (freezeStatus != f.getStatus()) {
                    f.setStatus(freezeStatus);
                    changed++;
                }
            }
        }
        return changed;
    }

    // ---- helpers ----

    /**
     * Al anular la membresía, sus congelaciones programadas o en curso se cancelan con la misma regla que
     * una cancelación manual: sin empezar suman 0 días; en curso, solo los ya transcurridos. Las
     * terminadas o ya canceladas no se tocan.
     */
    private void cancelOpenFreezes(Membership m, String reason, LocalDate today, Long currentUserId) {
        String freezeReason = "Membresía anulada: " + reason;
        if (freezeReason.length() > 255) {
            freezeReason = freezeReason.substring(0, 255);
        }
        for (MembershipFreeze f : m.getFreezes()) {
            if (f.isCancelled() || today.isAfter(f.getEndDate())) {
                continue;
            }
            FreezeSnapshot before = FreezeSnapshot.of(f);
            f.setAppliedDays(MembershipDates.appliedDaysOnCancel(f.getStartDate(), f.getEndDate(), today));
            f.setStatus(FreezeStatus.CANCELLED);
            f.setCancelledAt(support.now());
            f.setCancelledBy(currentUserId);
            f.setCancellationReason(freezeReason);
            support.recalculate(m, today);
            support.audit(MembershipSupport.AUDIT_FREEZE, f.getId(), AuditService.ACTION_CANCEL,
                    before, FreezeSnapshot.of(f), currentUserId);
        }
    }

    private LocalDate startFor(List<Membership> memberships, LocalDate requestedStart, LocalDate today) {
        if (MembershipRules.next(memberships, today).isPresent()) {
            throw new ConflictException(NEXT_EXISTS);
        }
        LocalDate currentEnd = MembershipRules.current(memberships, today).map(Membership::getEndDate).orElse(null);
        return MembershipDates.newMembershipStart(today, requestedStart, currentEnd);
    }

    private Plan sellablePlan(Long planId) {
        Plan plan = planRepository.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Plan no encontrado"));
        if (!plan.isActive()) {
            throw new BusinessException("El plan está inactivo y no se puede vender");
        }
        return plan;
    }

    private static void requireActiveClient(Client client) {
        if (!client.isActive()) {
            throw new BusinessException("El cliente está inactivo. Actívalo antes de registrarle una membresía");
        }
    }

    private void requireClientExists(Long clientId) {
        if (!clientRepository.existsById(clientId)) {
            throw new ResourceNotFoundException("Cliente no encontrado");
        }
    }

    private Membership findOrThrow(Long id) {
        return membershipRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Membresía no encontrada"));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
