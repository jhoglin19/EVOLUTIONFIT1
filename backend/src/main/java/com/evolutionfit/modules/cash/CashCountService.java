package com.evolutionfit.modules.cash;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.evolutionfit.common.exception.BusinessException;
import com.evolutionfit.common.exception.ConflictException;
import com.evolutionfit.common.exception.ResourceNotFoundException;
import com.evolutionfit.modules.audit.AuditService;
import com.evolutionfit.modules.cash.CashCalculator.CountResult;
import com.evolutionfit.modules.cash.CashCalculator.PostCut;
import com.evolutionfit.modules.cash.dto.CashClosingResponse;
import com.evolutionfit.modules.cash.dto.CountPreviewResponse;
import com.evolutionfit.modules.cash.dto.CountRequest;
import com.evolutionfit.security.AuthUserDetails;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Arqueo, revisión y reapertura. El esperado congelado del cierre nunca cambia: el arqueo lo ajusta,
 * aparte, por el efectivo que entró o salió de la caja después del corte (por ejemplo, un cobro a las
 * 10:10 p.m. que ya está en la caja pero cuenta para el día siguiente).
 * Recepción solo ve y registra arqueos pendientes; el ADMIN también puede registrar el de un día sin
 * movimientos (no es obligatorio), revisar diferencias y reabrir un arqueo mal digitado.
 */
@Service
public class CashCountService {

    static final String ACTION_COUNT = "COUNT";
    static final String ACTION_REVIEW = "REVIEW";
    static final String ACTION_REOPEN = "REOPEN_COUNT";

    private final CashClosingRepository closingRepository;
    private final CashSettingsRepository settingsRepository;
    private final CashLedgerRepository ledgerRepository;
    private final CashCalendar calendar;
    private final CashClosingMapper mapper;
    private final AuditService auditService;
    private final Clock clock;
    private final TransactionTemplate tx;

    public CashCountService(CashClosingRepository closingRepository, CashSettingsRepository settingsRepository,
                            CashLedgerRepository ledgerRepository, CashCalendar calendar, CashClosingMapper mapper,
                            AuditService auditService, Clock clock, PlatformTransactionManager transactionManager) {
        this.closingRepository = closingRepository;
        this.settingsRepository = settingsRepository;
        this.ledgerRepository = ledgerRepository;
        this.calendar = calendar;
        this.mapper = mapper;
        this.auditService = auditService;
        this.clock = clock;
        this.tx = new TransactionTemplate(transactionManager);
        this.tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    /** Arqueos pendientes (los ve recepción). */
    public List<CashClosingResponse> pending() {
        return tx.execute(status -> closingRepository.findByStatusOrderByCashDateAsc(CashClosingStatus.PENDING_COUNT)
                .stream().map(mapper::toResponse).toList());
    }

    public CountPreviewResponse preview(Long closingId, AuthUserDetails user) {
        return tx.execute(status -> {
            CashClosing c = closingRepository.findById(closingId).orElseThrow(CashCountService::notFound);
            ensureCountable(c, user);
            PostCut postCut = postCut(c);
            CountResult r = CashCalculator.count(c.getExpectedCash(), postCut, c.getExpectedCash());
            return new CountPreviewResponse(mapper.toResponse(c), postCut.income(), postCut.outflow(),
                    r.adjustedExpectedCash(), postCut.movements());
        });
    }

    public CashClosingResponse count(Long closingId, CountRequest request, AuthUserDetails user) {
        return tx.execute(status -> {
            CashClosing c = closingRepository.findByIdForUpdate(closingId).orElseThrow(CashCountService::notFound);
            ensureCountable(c, user);
            PostCut postCut = postCut(c);
            CountResult r = CashCalculator.count(c.getExpectedCash(), postCut, request.countedCash());
            if (r.adjustedExpectedCash().compareTo(request.expectedAdjustedCash()) != 0) {
                throw new ConflictException("Hubo movimientos de efectivo después de abrir el arqueo. "
                        + "Revisa el efectivo esperado y vuelve a registrarlo.");
            }
            String note = clean(request.note());
            if (r.difference().signum() != 0 && note == null) {
                throw new BusinessException("La nota es obligatoria cuando hay diferencia");
            }
            c.setPostCutIncome(postCut.income());
            c.setPostCutOutflow(postCut.outflow());
            c.setAdjustedExpectedCash(r.adjustedExpectedCash());
            c.setCountedCash(request.countedCash());
            c.setDifference(r.difference());
            c.setPostCutDetail(mapper.writeMovements(postCut.movements()));
            c.setCountNote(note);
            c.setCountedBy(user.getId());
            c.setCountedAt(LocalDateTime.now(clock));
            c.setStatus(r.difference().signum() == 0 ? CashClosingStatus.COUNTED : CashClosingStatus.PENDING_REVIEW);
            CashClosing saved = closingRepository.saveAndFlush(c);
            auditService.record(CashClosingService.AUDIT_ENTITY, saved.getId(), ACTION_COUNT, null,
                    CashClosingService.snapshot(saved), user.getId());
            return mapper.toResponse(saved);
        });
    }

    /** Solo ADMIN: revisa un arqueo con diferencia, con nota obligatoria. */
    public CashClosingResponse review(Long closingId, String note, Long adminId) {
        return tx.execute(status -> {
            CashClosing c = closingRepository.findByIdForUpdate(closingId).orElseThrow(CashCountService::notFound);
            if (c.getStatus() != CashClosingStatus.PENDING_REVIEW) {
                throw new ConflictException("Ese cierre no tiene una diferencia pendiente de revisión");
            }
            Map<String, Object> before = CashClosingService.snapshot(c);
            c.setReviewNote(note.trim());
            c.setReviewedBy(adminId);
            c.setReviewedAt(LocalDateTime.now(clock));
            c.setStatus(CashClosingStatus.REVIEWED);
            CashClosing saved = closingRepository.saveAndFlush(c);
            auditService.record(CashClosingService.AUDIT_ENTITY, saved.getId(), ACTION_REVIEW, before,
                    CashClosingService.snapshot(saved), adminId);
            return mapper.toResponse(saved);
        });
    }

    /** Solo ADMIN: deja el arqueo sin registrar (por ejemplo, un valor mal digitado). El esperado no cambia. */
    public CashClosingResponse reopen(Long closingId, String reason, Long adminId) {
        return tx.execute(status -> {
            CashClosing c = closingRepository.findByIdForUpdate(closingId).orElseThrow(CashCountService::notFound);
            if (!c.getStatus().isCounted()) {
                throw new ConflictException("Ese cierre no tiene un arqueo registrado");
            }
            Map<String, Object> before = CashClosingService.snapshot(c);
            c.clearCount();
            c.setStatus(c.isHasCashActivity() ? CashClosingStatus.PENDING_COUNT : CashClosingStatus.NO_ACTIVITY);
            CashClosing saved = closingRepository.saveAndFlush(c);
            Map<String, Object> after = CashClosingService.snapshot(saved);
            after.put("reason", reason.trim());
            auditService.record(CashClosingService.AUDIT_ENTITY, saved.getId(), ACTION_REOPEN, before, after, adminId);
            return mapper.toResponse(saved);
        });
    }

    // ---- helpers ----

    private PostCut postCut(CashClosing c) {
        CashSettings settings = settingsRepository.findById(CashSettings.SINGLE_ID)
                .orElseThrow(() -> new IllegalStateException("Falta la fila de cash_settings (V5)"));
        return CashCalculator.postCut(ledgerRepository.after(c.getCashDate(), calendar.cashStart(settings.getStartDate())));
    }

    private static void ensureCountable(CashClosing c, AuthUserDetails user) {
        boolean admin = CashService.isAdmin(user);
        if (c.getStatus() == CashClosingStatus.PENDING_COUNT) {
            return;
        }
        if (!admin) {
            throw notFound(); // recepción solo ve sus arqueos pendientes
        }
        if (c.getStatus() != CashClosingStatus.NO_ACTIVITY) {
            throw new ConflictException("El arqueo de ese día ya está registrado");
        }
    }

    private static ResourceNotFoundException notFound() {
        return new ResourceNotFoundException("No hay un arqueo pendiente para ese cierre");
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
