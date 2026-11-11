package com.evolutionfit.modules.dashboard;

import java.time.Clock;
import java.time.LocalDate;

import com.evolutionfit.modules.cash.CashCountService;
import com.evolutionfit.modules.cash.CashService;
import com.evolutionfit.modules.cash.dto.CashTodayResponse;
import com.evolutionfit.modules.dashboard.dto.AdminDashboardResponse;
import com.evolutionfit.modules.dashboard.dto.DashboardResponse;
import com.evolutionfit.modules.memberships.ExpirationAlertKind;
import com.evolutionfit.modules.memberships.MembershipAlertService;
import com.evolutionfit.modules.memberships.dto.AlertCountsResponse;
import com.evolutionfit.modules.payments.PaymentStatus;
import com.evolutionfit.modules.reports.DateRange;
import com.evolutionfit.modules.reports.ReportRepository;
import com.evolutionfit.security.AuthUserDetails;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Panel de inicio (docs/REGLAS_NEGOCIO.md, sección 11). "Hoy" es el día calendario de Bogotá; la tarjeta de
 * caja respeta el día de caja, con su corte de las 10 p.m.
 */
@Service
public class DashboardService {

    /** Filas de cada lista corta del panel; el resto está en Vencimientos y Pagos. */
    static final int LIST_SIZE = 5;
    /** Pagos del día que se listan (un día normal tiene menos). */
    static final int PAYMENTS_LIMIT = 50;

    private final MembershipAlertService alertService;
    private final ReportRepository reportRepository;
    private final DashboardRepository dashboardRepository;
    private final CashService cashService;
    private final CashCountService countService;
    private final Clock clock;

    public DashboardService(MembershipAlertService alertService, ReportRepository reportRepository,
                            DashboardRepository dashboardRepository, CashService cashService,
                            CashCountService countService, Clock clock) {
        this.alertService = alertService;
        this.reportRepository = reportRepository;
        this.dashboardRepository = dashboardRepository;
        this.cashService = cashService;
        this.countService = countService;
        this.clock = clock;
    }

    public DashboardResponse today(AuthUserDetails user) {
        // La caja primero: pone al día los cierres pendientes en su propia transacción.
        DashboardResponse.CashCard cash = cashCard(user);
        return data(cash);
    }

    /** Cada consulta abre su propia transacción de solo lectura (los servicios llamados la declaran). */
    private DashboardResponse data(DashboardResponse.CashCard cash) {
        LocalDate today = LocalDate.now(clock);
        DateRange day = DateRange.day(today);
        AlertCountsResponse counts = alertService.counts();
        return new DashboardResponse(today, counts.warningDays(), counts.recentlyExpiredDays(),
                alertService.inForceCount(false), alertService.inForceCount(true),
                counts.expiring(), alertService.list(ExpirationAlertKind.EXPIRING, null, 0, LIST_SIZE).getContent(),
                counts.recentlyExpired(), alertService.list(ExpirationAlertKind.EXPIRED, null, 0, LIST_SIZE).getContent(),
                reportRepository.enrollments(day),
                dashboardRepository.countPayments(day, PaymentStatus.COMPLETED),
                dashboardRepository.countPayments(day, PaymentStatus.VOIDED),
                dashboardRepository.payments(day, PAYMENTS_LIMIT), cash);
    }

    @Transactional(readOnly = true)
    public AdminDashboardResponse admin() {
        LocalDate today = LocalDate.now(clock);
        DateRange month = new DateRange(today.withDayOfMonth(1), today);
        var voided = reportRepository.voidedTotals(month);
        var discounts = reportRepository.discountTotals(month);
        return new AdminDashboardResponse(today, month.from(), reportRepository.methodTotals(DateRange.day(today)),
                reportRepository.methodTotals(month), voided.count(), voided.amount(), discounts.count(),
                discounts.amount());
    }

    private DashboardResponse.CashCard cashCard(AuthUserDetails user) {
        CashTodayResponse t = cashService.today(user);
        var pending = countService.pending().stream()
                .map(c -> new DashboardResponse.PendingCount(c.id(), c.cashDate())).toList();
        return new DashboardResponse.CashCard(t.started(), t.startDate(), t.cashDate(), t.cutoffAt(), t.openDay(),
                t.closedReason(), pending, t.pendingReviews());
    }
}
