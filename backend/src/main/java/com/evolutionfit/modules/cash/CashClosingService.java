package com.evolutionfit.modules.cash;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.evolutionfit.modules.audit.AuditService;

import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Cierre diario automático a las 10 p.m. de Bogotá.
 * <ul>
 *   <li>Se pone al día: cierra, en orden, cada día vencido desde el último cierre (o desde la fecha de
 *       inicio), uno por transacción. Si se cae a mitad, sigue donde quedó.</li>
 *   <li>Idempotente: cada cierre toma el candado de cash_settings y vuelve a leer el último cierre;
 *       cash_date es único como última defensa. Correrlo dos veces, o en dos hilos, da lo mismo.</li>
 *   <li>Los periodos quedan pegados: cada uno empieza donde terminó el anterior.</li>
 *   <li>Se crea un cierre para todos los días, también sin movimientos o con el gimnasio cerrado.</li>
 * </ul>
 */
@Slf4j
@Service
public class CashClosingService {

    static final String AUDIT_ENTITY = "CASH_CLOSING";
    static final String ACTION_CLOSE = "CLOSE";
    /** Un cierre hecho más tarde que esto después del corte se marca como "puesto al día". */
    static final Duration ON_TIME_TOLERANCE = Duration.ofMinutes(15);

    private final CashSettingsRepository settingsRepository;
    private final CashClosingRepository closingRepository;
    private final CashLedgerRepository ledgerRepository;
    private final CashCalendar calendar;
    private final AuditService auditService;
    private final Clock clock;
    private final TransactionTemplate tx;

    public CashClosingService(CashSettingsRepository settingsRepository, CashClosingRepository closingRepository,
                              CashLedgerRepository ledgerRepository, CashCalendar calendar, AuditService auditService,
                              Clock clock, PlatformTransactionManager transactionManager) {
        this.settingsRepository = settingsRepository;
        this.closingRepository = closingRepository;
        this.ledgerRepository = ledgerRepository;
        this.calendar = calendar;
        this.auditService = auditService;
        this.clock = clock;
        this.tx = new TransactionTemplate(transactionManager);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    /** Cierra todos los días vencidos. Devuelve cuántos cerró (0 si ya estaba al día o la caja no ha empezado). */
    public int closeDueDays() {
        int closed = 0;
        while (Boolean.TRUE.equals(tx.execute(status -> closeNextDueDay()))) {
            closed++;
        }
        return closed;
    }

    private boolean closeNextDueDay() {
        CashSettings settings = settingsRepository.findForUpdate()
                .orElseThrow(() -> new IllegalStateException("Falta la fila de cash_settings (V5)"));
        if (settings.getStartDate() == null) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        Optional<CashClosing> last = closingRepository.findTopByOrderByCashDateDesc();
        LocalDate day = last.map(c -> c.getCashDate().plusDays(1)).orElse(settings.getStartDate());
        if (day.isAfter(calendar.lastDueDay(now))) {
            return false;
        }
        LocalDateTime cashStart = calendar.cashStart(settings.getStartDate());
        LocalDateTime cutoff = calendar.cutoff(day);

        CashClosing c = new CashClosing();
        c.setCashDate(day);
        c.setPeriodStart(day);
        c.setPeriodEnd(day);
        c.setWindowStart(last.map(CashClosing::getWindowEnd).orElse(cashStart));
        c.setWindowEnd(cutoff);
        c.setClosingMode(now.isAfter(cutoff.plus(ON_TIME_TOLERANCE)) ? ClosingMode.CATCH_UP : ClosingMode.ON_TIME);
        c.setWasOpenDay(settings.isOpenOn(day));
        c.setClosedAt(now);
        apply(c, new CashSummary(settings.getOpeningBase(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                settings.getOpeningBase(), false, List.of()));
        CashClosing saved = closingRepository.saveAndFlush(c);

        ledgerRepository.stamp(saved.getId(), cashStart, cutoff);
        CashSummary summary = CashCalculator.summarize(settings.getOpeningBase(), ledgerRepository.forClosing(saved.getId()));
        apply(saved, summary);
        closingRepository.saveAndFlush(saved);
        ledgerRepository.insertTotals(saved.getId(), summary.methods());

        auditService.record(AUDIT_ENTITY, saved.getId(), ACTION_CLOSE, null, snapshot(saved), null);
        log.info("Caja cerrada: {} ({}), efectivo esperado {}", day, saved.getClosingMode(), saved.getExpectedCash());
        return true;
    }

    private static void apply(CashClosing c, CashSummary s) {
        c.setOpeningBase(s.openingBase());
        c.setCashIncome(s.cashIncome());
        c.setCashVoided(s.cashVoided());
        c.setCashWithdrawals(s.cashWithdrawals());
        c.setExpectedCash(s.expectedCash());
        c.setHasCashActivity(s.hasCashActivity());
        c.setStatus(s.hasCashActivity() ? CashClosingStatus.PENDING_COUNT : CashClosingStatus.NO_ACTIVITY);
        c.setTotalIncome(s.cashIncome());
        c.setTotalExpenses(s.cashWithdrawals());
        c.setTotalAdjustments(s.cashVoided());
        c.setBalance(s.expectedCash());
    }

    static Map<String, Object> snapshot(CashClosing c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cashDate", c.getCashDate().toString());
        m.put("windowStart", c.getWindowStart().toString());
        m.put("windowEnd", c.getWindowEnd().toString());
        m.put("status", c.getStatus().name());
        m.put("closingMode", c.getClosingMode().name());
        m.put("wasOpenDay", c.isWasOpenDay());
        m.put("openingBase", c.getOpeningBase());
        m.put("cashIncome", c.getCashIncome());
        m.put("cashVoided", c.getCashVoided());
        m.put("cashWithdrawals", c.getCashWithdrawals());
        m.put("expectedCash", c.getExpectedCash());
        if (c.getCountedCash() != null) {
            m.put("postCutIncome", c.getPostCutIncome());
            m.put("postCutOutflow", c.getPostCutOutflow());
            m.put("adjustedExpectedCash", c.getAdjustedExpectedCash());
            m.put("countedCash", c.getCountedCash());
            m.put("difference", c.getDifference());
            m.put("countNote", c.getCountNote());
            m.put("countedBy", c.getCountedBy());
        }
        if (c.getReviewedAt() != null) {
            m.put("reviewNote", c.getReviewNote());
            m.put("reviewedBy", c.getReviewedBy());
        }
        return m;
    }
}
