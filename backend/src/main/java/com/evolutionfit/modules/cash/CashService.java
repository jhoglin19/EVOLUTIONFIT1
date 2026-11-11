package com.evolutionfit.modules.cash;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;

import com.evolutionfit.common.exception.BusinessException;
import com.evolutionfit.common.exception.ConflictException;
import com.evolutionfit.common.exception.ResourceNotFoundException;
import com.evolutionfit.modules.audit.AuditService;
import com.evolutionfit.modules.cash.CashLedger.WithdrawalRow;
import com.evolutionfit.modules.cash.dto.CashSettingsRequest;
import com.evolutionfit.modules.cash.dto.CashSettingsResponse;
import com.evolutionfit.modules.cash.dto.CashTodayResponse;
import com.evolutionfit.modules.cash.dto.WithdrawalRequest;
import com.evolutionfit.modules.cash.dto.WithdrawalResponse;
import com.evolutionfit.security.AuthUserDetails;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Caja del día, retiros y configuración. Antes de leer o registrar algo se cierran los días vencidos,
 * así "el día en curso" nunca mezcla movimientos de un día que ya debió cerrarse.
 */
@Service
public class CashService {

    static final String WITHDRAWAL_ENTITY = "CASH_WITHDRAWAL";
    static final String SETTINGS_ENTITY = "CASH_SETTINGS";

    private final CashSettingsRepository settingsRepository;
    private final CashClosingRepository closingRepository;
    private final CashMovementRepository movementRepository;
    private final CashLedgerRepository ledgerRepository;
    private final CashClosingService closingService;
    private final CashCalendar calendar;
    private final AuditService auditService;
    private final Clock clock;
    private final TransactionTemplate tx;
    private final TransactionTemplate readTx;

    public CashService(CashSettingsRepository settingsRepository, CashClosingRepository closingRepository,
                       CashMovementRepository movementRepository, CashLedgerRepository ledgerRepository,
                       CashClosingService closingService, CashCalendar calendar, AuditService auditService,
                       Clock clock, PlatformTransactionManager transactionManager) {
        this.settingsRepository = settingsRepository;
        this.closingRepository = closingRepository;
        this.movementRepository = movementRepository;
        this.ledgerRepository = ledgerRepository;
        this.closingService = closingService;
        this.calendar = calendar;
        this.auditService = auditService;
        this.clock = clock;
        this.tx = new TransactionTemplate(transactionManager);
        this.tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.readTx = new TransactionTemplate(transactionManager);
        this.readTx.setReadOnly(true);
    }

    // ---- día en curso ----

    public CashTodayResponse today(AuthUserDetails user) {
        closingService.closeDueDays();
        return readTx.execute(status -> {
            CashSettings settings = settings();
            long pendingCounts = closingRepository.countByStatus(CashClosingStatus.PENDING_COUNT);
            Long pendingReviews = isAdmin(user) ? closingRepository.countByStatus(CashClosingStatus.PENDING_REVIEW) : null;
            LocalDateTime now = LocalDateTime.now(clock);
            if (!started(settings, now)) {
                return new CashTodayResponse(false, settings.getStartDate(), null, null, null, false, null, List.of(),
                        pendingCounts, pendingReviews);
            }
            LocalDateTime cashStart = calendar.cashStart(settings.getStartDate());
            Optional<CashClosing> last = closingRepository.findTopByOrderByCashDateDesc();
            LocalDate cashDate = max(calendar.cashDayOf(now), settings.getStartDate());
            CashLedger ledger = ledgerRepository.open(cashStart);
            return new CashTodayResponse(true, settings.getStartDate(), cashDate,
                    last.map(CashClosing::getWindowEnd).orElse(cashStart), calendar.cutoff(cashDate),
                    settings.isOpenOn(cashDate), CashCalculator.summarize(settings.getOpeningBase(), ledger),
                    CashCalculator.movements(ledger), pendingCounts, pendingReviews);
        });
    }

    // ---- retiros ----

    /** Recepción y ADMIN. No puede dejar el efectivo esperado del día en negativo. */
    public WithdrawalResponse registerWithdrawal(WithdrawalRequest request, AuthUserDetails user) {
        closingService.closeDueDays();
        return tx.execute(status -> {
            CashSettings settings = lockSettings(); // serializa con el cierre y con otros retiros
            Optional<CashMovement> existing = movementRepository.findByRequestId(request.requestId());
            if (existing.isPresent()) {
                return toResponse(existing.get()); // doble clic: el mismo retiro
            }
            LocalDateTime now = LocalDateTime.now(clock); // después del candado: si llegó tras el corte, es del día siguiente
            if (!started(settings, now)) {
                throw new BusinessException("La caja todavía no ha empezado: el ADMIN debe fijar la fecha de inicio");
            }
            BigDecimal available = CashCalculator.summarize(settings.getOpeningBase(),
                    ledgerRepository.open(calendar.cashStart(settings.getStartDate()))).expectedCash();
            if (request.amount().compareTo(available) > 0) {
                throw new BusinessException("El retiro supera el efectivo disponible en caja ($"
                        + available.stripTrailingZeros().toPlainString() + ")");
            }
            CashMovement m = new CashMovement();
            m.setUserId(user.getId());
            m.setCategory(request.category());
            m.setAmount(request.amount());
            m.setDescription(request.concept().trim());
            m.setRequestId(request.requestId());
            m.setOccurredAt(now);
            CashMovement saved = movementRepository.saveAndFlush(m);
            auditService.record(WITHDRAWAL_ENTITY, saved.getId(), AuditService.ACTION_CREATE, null, snapshot(saved),
                    user.getId());
            return toResponse(saved);
        });
    }

    /** Solo ADMIN, con motivo y solo antes del corte del día del retiro. */
    public WithdrawalResponse voidWithdrawal(Long id, String reason, Long adminId) {
        closingService.closeDueDays();
        return tx.execute(status -> {
            lockSettings();
            CashMovement m = movementRepository.findById(id)
                    .filter(w -> CashMovement.EXPENSE.equals(w.getMovementType()))
                    .orElseThrow(() -> new ResourceNotFoundException("Retiro no encontrado"));
            if (m.getVoidedAt() != null) {
                throw new ConflictException("El retiro ya está anulado");
            }
            LocalDateTime now = LocalDateTime.now(clock);
            if (m.getCashClosingId() != null || !now.isBefore(calendar.cutoff(calendar.cashDayOf(m.getOccurredAt())))) {
                throw new BusinessException("Solo se puede anular un retiro antes del corte de su día");
            }
            Map<String, Object> before = snapshot(m);
            m.setVoidedAt(now);
            m.setVoidedBy(adminId);
            m.setVoidReason(reason.trim());
            CashMovement saved = movementRepository.saveAndFlush(m);
            auditService.record(WITHDRAWAL_ENTITY, id, AuditService.ACTION_VOID, before, snapshot(saved), adminId);
            return toResponse(saved);
        });
    }

    /** Todos los retiros (ADMIN), por fecha de registro. */
    public List<WithdrawalResponse> listWithdrawals(LocalDate from, LocalDate to, WithdrawalCategory category) {
        LocalDate end = to == null ? LocalDate.now(clock) : to;
        LocalDate start = from == null ? end.withDayOfMonth(1) : from;
        if (start.isAfter(end)) {
            throw new BusinessException("La fecha inicial no puede ser posterior a la final");
        }
        return ledgerRepository.withdrawalsBetween(start.atStartOfDay(), end.plusDays(1).atStartOfDay(), category)
                .stream().sorted((a, b) -> b.occurredAt().compareTo(a.occurredAt())).map(this::toResponse).toList();
    }

    // ---- configuración (ADMIN) ----

    public CashSettingsResponse getSettings() {
        return readTx.execute(status -> toResponse(settings()));
    }

    public CashSettingsResponse updateSettings(CashSettingsRequest request, Long adminId) {
        CashSettingsResponse response = tx.execute(status -> {
            CashSettings s = lockSettings();
            Map<String, Object> before = snapshot(s);
            if (!Objects.equals(request.startDate(), s.getStartDate())) {
                if (closingRepository.count() > 0) {
                    throw new BusinessException("La fecha de inicio ya no se puede cambiar: ya hay cierres de caja");
                }
                if (request.startDate() != null && request.startDate().isBefore(LocalDate.now(clock))) {
                    throw new BusinessException("La fecha de inicio no puede estar en el pasado");
                }
                s.setStartDate(request.startDate());
            }
            s.setOpeningBase(request.openingBase());
            s.setOpenDaySet(new TreeSet<>(request.openDays()));
            s.setUpdatedBy(adminId);
            CashSettings saved = settingsRepository.saveAndFlush(s);
            auditService.record(SETTINGS_ENTITY, (long) CashSettings.SINGLE_ID, AuditService.ACTION_UPDATE, before,
                    snapshot(saved), adminId);
            return toResponse(saved);
        });
        closingService.closeDueDays(); // por si la fecha de inicio es hoy y ya pasó el corte
        return response;
    }

    // ---- helpers ----

    private boolean started(CashSettings s, LocalDateTime now) {
        return s.getStartDate() != null && !now.isBefore(calendar.cashStart(s.getStartDate()));
    }

    private CashSettings settings() {
        return settingsRepository.findById(CashSettings.SINGLE_ID)
                .orElseThrow(() -> new IllegalStateException("Falta la fila de cash_settings (V5)"));
    }

    private CashSettings lockSettings() {
        return settingsRepository.findForUpdate()
                .orElseThrow(() -> new IllegalStateException("Falta la fila de cash_settings (V5)"));
    }

    static boolean isAdmin(AuthUserDetails user) {
        return user != null && "ADMIN".equalsIgnoreCase(user.getRoleName());
    }

    private static LocalDate max(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }

    private CashSettingsResponse toResponse(CashSettings s) {
        return new CashSettingsResponse(s.getOpeningBase(), s.getStartDate(), closingRepository.count() > 0,
                List.copyOf(new TreeSet<DayOfWeek>(s.openDaySet())), calendar.closingTime());
    }

    private WithdrawalResponse toResponse(CashMovement m) {
        return new WithdrawalResponse(m.getId(), m.getAmount(), m.getCategory(), m.getCategory().label(),
                m.getDescription(), m.getOccurredAt(), calendar.cashDayOf(m.getOccurredAt()),
                ledgerRepository.userName(m.getUserId()), m.getVoidedAt() != null, m.getVoidedAt(), m.getVoidReason());
    }

    private WithdrawalResponse toResponse(WithdrawalRow w) {
        return new WithdrawalResponse(w.id(), w.amount(), w.category(), w.category().label(), w.concept(),
                w.occurredAt(), calendar.cashDayOf(w.occurredAt()), w.registeredBy(), w.voided(), w.voidedAt(),
                w.voidReason());
    }

    private static Map<String, Object> snapshot(CashMovement m) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("amount", m.getAmount());
        s.put("category", m.getCategory().name());
        s.put("concept", m.getDescription());
        s.put("occurredAt", m.getOccurredAt().toString());
        s.put("userId", m.getUserId());
        if (m.getVoidedAt() != null) {
            s.put("voidedAt", m.getVoidedAt().toString());
            s.put("voidReason", m.getVoidReason());
        }
        return s;
    }

    private static Map<String, Object> snapshot(CashSettings s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("openingBase", s.getOpeningBase());
        m.put("startDate", s.getStartDate() == null ? null : s.getStartDate().toString());
        m.put("openDays", s.getOpenDays());
        return m;
    }
}
