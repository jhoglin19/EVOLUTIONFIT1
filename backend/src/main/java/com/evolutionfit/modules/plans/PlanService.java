package com.evolutionfit.modules.plans;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.evolutionfit.common.exception.ConflictException;
import com.evolutionfit.common.exception.ResourceNotFoundException;
import com.evolutionfit.modules.audit.AuditService;
import com.evolutionfit.modules.plans.dto.PlanRequest;
import com.evolutionfit.modules.plans.dto.PlanResponse;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlanService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final String AUDIT_ENTITY = "PLAN";
    private static final String DUPLICATE_NAME = "Ya existe un plan con ese nombre";

    private final PlanRepository planRepository;
    private final AuditService auditService;

    public PlanService(PlanRepository planRepository, AuditService auditService) {
        this.planRepository = planRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public Page<PlanResponse> list(String query, Boolean active, int page, int size) {
        String term = query == null ? "" : query.trim();
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        // De menor a mayor duración: el orden natural para quien elige un plan.
        Sort sort = Sort.by("durationDays").ascending().and(Sort.by("name").ascending());
        return planRepository.search(term, active, PageRequest.of(safePage, safeSize, sort))
                .map(PlanResponse::from);
    }

    @Transactional(readOnly = true)
    public PlanResponse get(Long id) {
        return PlanResponse.from(findOrThrow(id));
    }

    @Transactional
    public PlanResponse create(PlanRequest request, Long currentUserId) {
        String name = request.name().trim();
        if (planRepository.existsByName(name)) {
            throw new ConflictException(DUPLICATE_NAME);
        }

        Plan plan = new Plan();
        plan.setActive(true);
        apply(plan, request, name);
        Plan saved = planRepository.save(plan);
        auditService.record(AUDIT_ENTITY, saved.getId(), AuditService.ACTION_CREATE,
                null, PlanSnapshot.of(saved), currentUserId);
        return PlanResponse.from(saved);
    }

    @Transactional
    public PlanResponse update(Long id, PlanRequest request, Long currentUserId) {
        Plan plan = findOrThrow(id);
        String name = request.name().trim();
        if (planRepository.existsByNameAndIdNot(name, id)) {
            throw new ConflictException(DUPLICATE_NAME);
        }

        PlanSnapshot before = PlanSnapshot.of(plan);
        apply(plan, request, name);
        Plan saved = planRepository.save(plan);
        PlanSnapshot after = PlanSnapshot.of(saved);
        if (!before.equals(after)) {
            auditService.record(AUDIT_ENTITY, id, AuditService.ACTION_UPDATE, before, after, currentUserId);
        }
        return PlanResponse.from(saved);
    }

    /** Desactivar un plan solo impide venderlo en adelante; las membresías existentes no cambian. */
    @Transactional
    public PlanResponse setActive(Long id, boolean active, Long currentUserId) {
        Plan plan = findOrThrow(id);
        if (plan.isActive() == active) {
            return PlanResponse.from(plan);
        }

        PlanSnapshot before = PlanSnapshot.of(plan);
        plan.setActive(active);
        Plan saved = planRepository.save(plan);
        auditService.record(AUDIT_ENTITY, id, AuditService.ACTION_STATUS_CHANGE,
                before, PlanSnapshot.of(saved), currentUserId);
        return PlanResponse.from(saved);
    }

    // ---- helpers ----

    private Plan findOrThrow(Long id) {
        return planRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Plan no encontrado"));
    }

    private static void apply(Plan plan, PlanRequest request, String name) {
        plan.setName(name);
        plan.setDescription(request.description() == null || request.description().isBlank()
                ? null : request.description().trim());
        plan.setDurationDays(request.durationDays());
        // Misma escala que la columna: así 90000 y 90000.00 no cuentan como un cambio de precio.
        plan.setPrice(request.price().setScale(2, RoundingMode.HALF_UP));
    }

    /** Lo que queda en audit_logs. Se excluyen las fechas para que comparar detecte solo cambios reales. */
    record PlanSnapshot(String name, String description, int durationDays, BigDecimal price, boolean active) {

        static PlanSnapshot of(Plan plan) {
            return new PlanSnapshot(plan.getName(), plan.getDescription(), plan.getDurationDays(),
                    plan.getPrice(), plan.isActive());
        }
    }
}
