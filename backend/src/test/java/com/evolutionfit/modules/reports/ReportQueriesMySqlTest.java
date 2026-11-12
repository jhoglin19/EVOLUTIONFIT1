package com.evolutionfit.modules.reports;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.evolutionfit.modules.cash.CashSummary.MethodTotal;
import com.evolutionfit.modules.cash.MutableClock;
import com.evolutionfit.modules.dashboard.DashboardService;
import com.evolutionfit.modules.dashboard.dto.DashboardResponse;
import com.evolutionfit.modules.memberships.ExpirationAlertKind;
import com.evolutionfit.modules.memberships.MembershipAlertService;
import com.evolutionfit.modules.memberships.MembershipService;
import com.evolutionfit.modules.memberships.dto.ExpirationAlertResponse;
import com.evolutionfit.modules.memberships.dto.MembershipResponse;
import com.evolutionfit.modules.payments.PaymentMethod;
import com.evolutionfit.modules.reports.dto.EnrollmentCounts;
import com.evolutionfit.modules.reports.dto.ExpirationsReportResponse;
import com.evolutionfit.modules.reports.dto.IncomeReportResponse;
import com.evolutionfit.modules.reports.dto.NewClientsReportResponse;
import com.evolutionfit.modules.reports.dto.PlanSalesReportResponse;
import com.evolutionfit.security.AuthUserDetails;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Panel, alertas y reportes contra MySQL 8.4 real (Flyway aplica V1–V7), con un reloj fijo. Cada prueba
 * empieza con las tablas de clientes, membresías y pagos vacías (es una base desechable). Datos ficticios.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "JWT_SECRET=clave-ficticia-solo-para-pruebas-de-reportes-0123456789",
        "BOOTSTRAP_ADMIN_PASSWORD=",
        "app.cash.scheduler-enabled=false"
})
@Import(ReportQueriesMySqlTest.ClockConfig.class)
class ReportQueriesMySqlTest {

    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    /** Martes 10 de noviembre de 2026, 3 p.m. */
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);
    private static final AtomicInteger DOCUMENTS = new AtomicInteger(800000);

    @TestConfiguration
    static class ClockConfig {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock();
        }
    }

    @Autowired
    private MutableClock clock;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ReportService reportService;
    @Autowired
    private ReportRepository reportRepository;
    @Autowired
    private DashboardService dashboardService;
    @Autowired
    private MembershipAlertService alertService;
    @Autowired
    private MembershipService membershipService;

    private final AuthUserDetails reception = new AuthUserDetails(60L, "recepcion_rep", "x", "RECEPTIONIST", true);
    private long monthly;
    private long daily;
    private long annual;

    @BeforeEach
    void reset() {
        clock.set(TODAY.atTime(15, 0));
        jdbc.update("delete from payment_receipts");
        jdbc.update("delete from payments");
        jdbc.update("delete from membership_freezes");
        jdbc.update("delete from memberships");
        jdbc.update("delete from clients");
        jdbc.update("delete from plans");
        jdbc.update("insert ignore into roles(id,name) values (1,'ADMIN'),(2,'RECEPTIONIST')");
        jdbc.update("insert ignore into users(id,role_id,username,password_hash,first_name,last_name) values "
                + "(60,2,'recepcion_rep','x','Recepción','Prueba'),(61,1,'admin_rep','x','Admin','Prueba')");
        monthly = plan("Mensual", 30, "90000");
        daily = plan("Día", 1, "10000");
        annual = plan("Anual", 365, "800000");
    }

    // ---- nuevos frente a renovaciones ----

    @Test
    void aMigratedClientWhoRenewsIsARenewalNotANewClient() {
        long migratedClient = client(true);
        // Migrada registrada el 1 de noviembre; vence hoy.
        membership(migratedClient, monthly, "2026-10-12", "2026-11-10", "ACTIVE", true, null, at("2026-11-01 09:00"));
        // Hoy paga su primera renovación en el sistema.
        long renewal = membership(migratedClient, monthly, "2026-11-11", "2026-12-10", "SCHEDULED", false,
                at("2026-11-10 10:00"), at("2026-11-10 09:55"));
        payment(renewal, "90000", "0", PaymentMethod.CASH, "COMPLETED", at("2026-11-10 10:00"), null);

        long newClient = client(true);
        long first = membership(newClient, monthly, "2026-11-10", "2026-12-09", "ACTIVE", false,
                at("2026-11-10 11:00"), at("2026-11-10 11:00"));
        payment(first, "90000", "0", PaymentMethod.NEQUI, "COMPLETED", at("2026-11-10 11:00"), null);

        EnrollmentCounts today = reportRepository.enrollments(DateRange.day(TODAY));
        assertThat(today.newClients()).isEqualTo(1);
        assertThat(today.renewals()).isEqualTo(1);
        assertThat(today.migrated()).isZero(); // la migrada se registró el 1 de noviembre

        NewClientsReportResponse month = reportService.newClients(LocalDate.of(2026, 11, 1), TODAY);
        assertThat(month.newClients()).isEqualTo(1);
        assertThat(month.migratedClients()).isEqualTo(1);
        assertThat(month.rows()).extracting(NewClientsReportResponse.Row::clientId, NewClientsReportResponse.Row::type)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(migratedClient, NewClientsReportResponse.Type.MIGRATED),
                        org.assertj.core.groups.Tuple.tuple(newClient, NewClientsReportResponse.Type.NEW));
        assertThat(month.rows().get(1).paidAmount()).isEqualByComparingTo("90000");

        // En el periodo de la migración (solo el 1 de noviembre) aparece como migrado.
        NewClientsReportResponse nov1 = reportService.newClients(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 1));
        assertThat(nov1.migratedClients()).isEqualTo(1);
        assertThat(nov1.newClients()).isZero();

        PlanSalesReportResponse plans = reportService.planSales(TODAY, TODAY);
        assertThat(plans.rows()).singleElement().satisfies(r -> {
            assertThat(r.sold()).isEqualTo(2);
            assertThat(r.newClients()).isEqualTo(1);
            assertThat(r.renewals()).isEqualTo(1);
        });
    }

    @Test
    void aSecondPaidMembershipIsARenewalAndAVoidedFirstOneDoesNotCount() {
        long c = client(true);
        long first = membership(c, monthly, "2026-10-01", "2026-10-30", "EXPIRED", false,
                at("2026-10-01 08:00"), at("2026-10-01 08:00"));
        payment(first, "90000", "0", PaymentMethod.CASH, "COMPLETED", at("2026-10-01 08:00"), null);
        long second = membership(c, monthly, "2026-11-10", "2026-12-09", "ACTIVE", false,
                at("2026-11-10 08:00"), at("2026-11-10 08:00"));
        payment(second, "90000", "0", PaymentMethod.CASH, "COMPLETED", at("2026-11-10 08:00"), null);

        // Otro cliente: su primer pago se anuló (membresía anulada) y hoy volvió a pagar: es nuevo hoy.
        long d = client(true);
        long voided = cancelledMembership(d, monthly, at("2026-11-02 08:00"));
        payment(voided, "90000", "0", PaymentMethod.CASH, "VOIDED", at("2026-11-02 08:00"), at("2026-11-02 09:00"));
        long again = membership(d, monthly, "2026-11-10", "2026-12-09", "ACTIVE", false,
                at("2026-11-10 09:00"), at("2026-11-10 09:00"));
        payment(again, "90000", "0", PaymentMethod.CASH, "COMPLETED", at("2026-11-10 09:00"), null);

        EnrollmentCounts today = reportRepository.enrollments(DateRange.day(TODAY));
        assertThat(today.newClients()).isEqualTo(1);
        assertThat(today.renewals()).isEqualTo(1);
        assertThat(reportService.newClients(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)).newClients())
                .isEqualTo(1);
        assertThat(reportService.newClients(LocalDate.of(2026, 11, 1), TODAY).rows())
                .extracting(NewClientsReportResponse.Row::clientId).containsExactly(d);
    }

    // ---- ingresos ----

    @Test
    void incomeUsesTheCashRuleWithCalendarDayBoundaries() {
        long c = client(true);
        // 9 de nov a las 23:59:59: cuenta el 9 (en Caja, después de las 10 p.m., sería del día 10).
        long late = membership(c, daily, "2026-11-09", "2026-11-09", "EXPIRED", false,
                LocalDateTime.of(2026, 11, 9, 23, 59, 59), at("2026-11-09 23:59"));
        payment(late, "10000", "0", PaymentMethod.CASH, "COMPLETED", LocalDateTime.of(2026, 11, 9, 23, 59, 59), null);
        // 10 de nov a las 00:00:00: cuenta el 10.
        long midnight = membership(c, daily, "2026-11-10", "2026-11-10", "ACTIVE", false,
                at("2026-11-10 00:00"), at("2026-11-10 00:00"));
        payment(midnight, "10000", "0", PaymentMethod.NEQUI, "COMPLETED", at("2026-11-10 00:00"), null);
        // Pagado el 8 y anulado el 10: recibido el 8, anulado el 10.
        long voided = cancelledMembership(client(true), monthly, at("2026-11-08 10:00"));
        payment(voided, "90000", "0", PaymentMethod.CASH, "VOIDED", at("2026-11-08 10:00"), at("2026-11-10 12:00"));

        IncomeReportResponse r = reportService.income(LocalDate.of(2026, 11, 8), TODAY, IncomeGrouping.DAY);
        assertThat(r.periods()).hasSize(3);
        IncomeReportResponse.Period d8 = r.periods().get(0);
        IncomeReportResponse.Period d9 = r.periods().get(1);
        IncomeReportResponse.Period d10 = r.periods().get(2);
        assertThat(d8.receivedAmount()).isEqualByComparingTo("90000");
        assertThat(d8.netAmount()).isEqualByComparingTo("90000");
        assertThat(d9.receivedAmount()).isEqualByComparingTo("10000");
        assertThat(d9.cashNet()).isEqualByComparingTo("10000");
        assertThat(d10.receivedAmount()).isEqualByComparingTo("10000");
        assertThat(d10.voidedAmount()).isEqualByComparingTo("90000");
        assertThat(d10.netAmount()).isEqualByComparingTo("-80000");
        assertThat(d10.cashNet()).isEqualByComparingTo("-90000");
        assertThat(d10.otherNet()).isEqualByComparingTo("10000");

        MethodTotal cash = r.methods().stream().filter(m -> m.method() == PaymentMethod.CASH).findFirst().orElseThrow();
        assertThat(cash.receivedCount()).isEqualTo(2);
        assertThat(cash.receivedAmount()).isEqualByComparingTo("100000");
        assertThat(cash.voidedAmount()).isEqualByComparingTo("90000");

        // Solo el 10: lo pagado el 8 no entra, pero su anulación sí (igual que en Caja).
        IncomeReportResponse only10 = reportService.income(TODAY, TODAY, IncomeGrouping.DAY);
        assertThat(only10.periods().get(0).netAmount()).isEqualByComparingTo("-80000");

        // Agrupado por mes: un solo periodo recortado al rango.
        IncomeReportResponse month = reportService.income(LocalDate.of(2026, 11, 8), TODAY, IncomeGrouping.MONTH);
        assertThat(month.periods()).singleElement().satisfies(p -> {
            assertThat(p.start()).isEqualTo(LocalDate.of(2026, 11, 8));
            assertThat(p.end()).isEqualTo(TODAY);
            assertThat(p.netAmount()).isEqualByComparingTo("20000");
        });
    }

    // ---- alertas ----

    @Test
    void expiringAlertsSkipPaidRenewalsAndInactiveClientsAndMatchTheMembershipList() {
        long plain = client(true);
        long mPlain = membership(plain, monthly, "2026-10-14", "2026-11-12", "ACTIVE", false, at("2026-10-14 08:00"),
                at("2026-10-14 08:00"));
        long renewed = client(true);
        long mRenewed = membership(renewed, monthly, "2026-10-12", "2026-11-10", "ACTIVE", false, at("2026-10-12 08:00"),
                at("2026-10-12 08:00"));
        membership(renewed, monthly, "2026-11-11", "2026-12-10", "SCHEDULED", false, at("2026-11-09 08:00"),
                at("2026-11-09 08:00"));
        long pending = client(true);
        membership(pending, monthly, "2026-10-16", "2026-11-15", "ACTIVE", false, at("2026-10-16 08:00"),
                at("2026-10-16 08:00"));
        long pendingRenewal = pendingMembership(pending, monthly);
        long inactive = client(false);
        membership(inactive, monthly, "2026-10-13", "2026-11-11", "ACTIVE", false, at("2026-10-13 08:00"),
                at("2026-10-13 08:00"));
        long frozen = client(true);
        long mFrozen = membership(frozen, monthly, "2026-10-20", "2026-11-14", "FROZEN", false, at("2026-10-20 08:00"),
                at("2026-10-20 08:00"));
        jdbc.update("insert into membership_freezes(membership_id,start_date,end_date,applied_days,reason,status)"
                + " values (?, '2026-11-09', '2026-11-11', 3, 'Viaje', 'ACTIVE')", mFrozen);
        // Fuera de la ventana (vence en 6 días).
        membership(client(true), monthly, "2026-10-17", "2026-11-16", "ACTIVE", false, at("2026-10-17 08:00"),
                at("2026-10-17 08:00"));

        List<ExpirationAlertResponse> alerts = alertService.list(ExpirationAlertKind.EXPIRING, null, 0, 50).getContent();
        assertThat(alerts).extracting(ExpirationAlertResponse::clientId).containsExactly(plain, frozen, pending);
        assertThat(alerts.get(0).daysToEnd()).isEqualTo(2);
        assertThat(alerts.get(2).pendingRenewalId()).isEqualTo(pendingRenewal);
        assertThat(alerts.get(0).pendingRenewalId()).isNull();
        assertThat(alerts.get(1).status().name()).isEqualTo("FROZEN");

        // El mismo criterio que "Por vencer" en Membresías, que además muestra a los clientes inactivos.
        List<Long> fromList = membershipService.list(null, true, null, 0, 50).getContent().stream()
                .map(MembershipResponse::clientId).toList();
        assertThat(fromList).containsExactlyInAnyOrder(plain, frozen, pending, inactive);
        assertThat(fromList).doesNotContain(renewed);
        assertThat(mRenewed).isPositive();
        assertThat(mPlain).isPositive();

        assertThat(alertService.counts().expiring()).isEqualTo(3);
        assertThat(alertService.inForceCount(true)).isEqualTo(1);
        // plain, renewed (vence hoy), pending, inactive y el de la semana que viene; la programada no cuenta.
        assertThat(alertService.inForceCount(false)).isEqualTo(5);
    }

    @Test
    void recentlyExpiredAlertsCoverFifteenDaysWithoutRenewalAndOnlyActiveClients() {
        long recent = client(true);
        membership(recent, monthly, "2026-10-08", "2026-11-07", "EXPIRED", false, at("2026-10-08 08:00"),
                at("2026-10-08 08:00"));
        long yesterday = client(true);
        membership(yesterday, monthly, "2026-10-11", "2026-11-09", "EXPIRED", false, at("2026-10-11 08:00"),
                at("2026-10-11 08:00"));
        long renewedAfter = client(true);
        membership(renewedAfter, monthly, "2026-10-06", "2026-11-05", "EXPIRED", false, at("2026-10-06 08:00"),
                at("2026-10-06 08:00"));
        membership(renewedAfter, monthly, "2026-11-08", "2026-12-07", "ACTIVE", false, at("2026-11-08 08:00"),
                at("2026-11-08 08:00"));
        long old = client(true);
        membership(old, monthly, "2026-09-25", "2026-10-25", "EXPIRED", false, at("2026-09-25 08:00"),
                at("2026-09-25 08:00")); // venció hace 16 días
        long edge = client(true);
        membership(edge, monthly, "2026-09-27", "2026-10-26", "EXPIRED", false, at("2026-09-27 08:00"),
                at("2026-09-27 08:00")); // venció hace 15 días: todavía aparece
        long inactive = client(false);
        membership(inactive, monthly, "2026-10-09", "2026-11-08", "EXPIRED", false, at("2026-10-09 08:00"),
                at("2026-10-09 08:00"));

        List<ExpirationAlertResponse> alerts = alertService.list(ExpirationAlertKind.EXPIRED, null, 0, 50).getContent();
        assertThat(alerts).extracting(ExpirationAlertResponse::clientId).containsExactly(yesterday, recent, edge);
        assertThat(alerts.get(0).daysToEnd()).isEqualTo(-1);
        // Búsqueda por documento, como en Membresías.
        assertThat(alertService.list(ExpirationAlertKind.EXPIRED, documentOf(recent), 0, 50).getContent())
                .extracting(ExpirationAlertResponse::clientId).containsExactly(recent);

        // El reporte del ADMIN sí incluye al cliente inactivo y marca quién renovó.
        ExpirationsReportResponse report = reportService.expirations(LocalDate.of(2026, 11, 1), TODAY);
        assertThat(report.rows()).extracting(ExpirationsReportResponse.Row::clientId)
                .containsExactly(renewedAfter, recent, inactive, yesterday);
        assertThat(report.expired()).isEqualTo(4);
        assertThat(report.renewed()).isEqualTo(1);
        assertThat(report.renewalRate()).isEqualByComparingTo("25.0");
        assertThat(report.rows().get(0).renewedAt()).isEqualTo(at("2026-11-08 08:00"));
        assertThat(report.rows().get(2).clientActive()).isFalse();
    }

    // ---- panel ----

    @Test
    void theDashboardShowsTodaysPaymentsWithoutTotalsAndCountsVoidedApart() {
        long c = client(true);
        long m1 = membership(c, monthly, "2026-11-10", "2026-12-09", "ACTIVE", false, at("2026-11-10 08:00"),
                at("2026-11-10 08:00"));
        payment(m1, "90000", "0", PaymentMethod.CASH, "COMPLETED", at("2026-11-10 08:00"), null);
        long m2 = cancelledMembership(client(true), daily, at("2026-11-10 09:00"));
        payment(m2, "10000", "0", PaymentMethod.CASH, "VOIDED", at("2026-11-10 09:00"), at("2026-11-10 09:30"));
        long yesterday = membership(client(true), daily, "2026-11-09", "2026-11-09", "EXPIRED", false,
                at("2026-11-09 23:30"), at("2026-11-09 23:30"));
        payment(yesterday, "10000", "0", PaymentMethod.CASH, "COMPLETED", at("2026-11-09 23:30"), null);
        pendingMembership(client(true), annual);

        DashboardResponse d = dashboardService.today(reception);
        assertThat(d.today()).isEqualTo(TODAY);
        assertThat(d.paymentsCount()).isEqualTo(1);
        assertThat(d.voidedPaymentsCount()).isEqualTo(1);
        assertThat(d.payments()).extracting(DashboardResponse.TodayPayment::id).hasSize(2);
        assertThat(d.enrollments().newClients()).isEqualTo(1);
        assertThat(d.enrollments().pendingPayment()).isEqualTo(1);
        assertThat(d.cash().started()).isFalse();
        assertThat(d.cash().pendingReviews()).isNull(); // solo para el ADMIN
        assertThat(d.recentlyExpiredCount()).isEqualTo(1); // el plan de un día de ayer

        var admin = dashboardService.admin();
        MethodTotal cash = admin.todayMethods().get(0);
        assertThat(cash.method()).isEqualTo(PaymentMethod.CASH);
        assertThat(cash.receivedAmount()).isEqualByComparingTo("100000");
        assertThat(cash.voidedAmount()).isEqualByComparingTo("10000");
        assertThat(admin.monthVoidedCount()).isEqualTo(1);
    }

    // ---- planes, descuentos, anulados y exportación ----

    @Test
    void planRankingDiscountsVoidsAndCsvExport() {
        for (int i = 0; i < 3; i++) {
            long m = membership(client(true), daily, "2026-11-05", "2026-11-05", "EXPIRED", false,
                    at("2026-11-05 0" + (i + 1) + ":00"), at("2026-11-05 08:00"));
            payment(m, "10000", "0", PaymentMethod.CASH, "COMPLETED", at("2026-11-05 0" + (i + 1) + ":00"), null);
        }
        long discounted = membership(client(true), annual, "2026-11-06", "2027-11-05", "ACTIVE", false,
                at("2026-11-06 10:00"), at("2026-11-06 10:00"));
        jdbc.update("insert into payments(membership_id,amount,list_price,discount_amount,discount_reason,discounted_by,"
                + "payment_method,status,paid_at,received_by) values (?,700000,800000,100000,'Convenio; empresa',61,"
                + "'CARD','COMPLETED','2026-11-06 10:00:00',60)", discounted);
        long voided = cancelledMembership(client(true), monthly, at("2026-11-07 10:00"));
        long voidedPayment = payment(voided, "90000", "0", PaymentMethod.CASH, "VOIDED", at("2026-11-07 10:00"),
                at("2026-11-07 11:00"));
        jdbc.update("update payments set voided_by = 61, void_reason = '=cmd|calc' where id = ?", voidedPayment);

        PlanSalesReportResponse plans = reportService.planSales(LocalDate.of(2026, 11, 1), TODAY);
        assertThat(plans.rows()).extracting(PlanSalesReportResponse.Row::planId).containsExactly(daily, annual);
        assertThat(plans.rows().get(1).revenue()).isEqualByComparingTo("700000");
        assertThat(plans.rows().get(1).discounts()).isEqualByComparingTo("100000");
        assertThat(plans.totalSold()).isEqualTo(4); // la anulada no cuenta

        var discounts = reportService.discounts(LocalDate.of(2026, 11, 1), TODAY);
        assertThat(discounts.count()).isEqualTo(1);
        assertThat(discounts.discountAmount()).isEqualByComparingTo("100000");
        assertThat(discounts.rows().get(0).discountedBy()).isEqualTo("Admin Prueba");

        var voids = reportService.voidedPayments(LocalDate.of(2026, 11, 1), TODAY);
        assertThat(voids.count()).isEqualTo(1);
        assertThat(voids.rows().get(0).voidedBy()).isEqualTo("Admin Prueba");

        long audits = jdbc.queryForObject("select count(*) from audit_logs where entity_type = 'REPORT'", Long.class);
        ReportService.Export export = reportService.export("voided-payments", LocalDate.of(2026, 11, 1), TODAY, null, 61L);
        String csv = new String(export.content(), StandardCharsets.UTF_8);
        assertThat(csv).startsWith("﻿Comprobante;Fecha del pago;");
        assertThat(csv).contains("'=cmd|calc"); // el texto no se ejecuta como fórmula en Excel
        assertThat(export.filename()).isEqualTo("voided-payments_2026-11-01_2026-11-10.csv");
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where entity_type = 'REPORT'", Long.class))
                .isEqualTo(audits + 1);

        String discountsCsv = new String(reportService.export("discounts", LocalDate.of(2026, 11, 1), TODAY, null, 61L)
                .content(), StandardCharsets.UTF_8);
        assertThat(discountsCsv).contains("\"Convenio; empresa\"").contains(";800000;100000;700000;");
    }

    // ---- datos ficticios ----

    private long plan(String name, int days, String price) {
        return insert("insert into plans(name,duration_days,price) values (?,?,?)", name, days, new BigDecimal(price));
    }

    private long client(boolean active) {
        String doc = String.valueOf(DOCUMENTS.incrementAndGet());
        return insert("insert into clients(document_type,document_number,first_name,last_name,phone,is_active)"
                + " values ('CC',?,'Cliente','Ficticio','3001234567',?)", doc, active);
    }

    private String documentOf(long clientId) {
        return jdbc.queryForObject("select document_number from clients where id = ?", String.class, clientId);
    }

    private long membership(long clientId, long planId, String start, String end, String status, boolean migrated,
                            LocalDateTime activatedAt, LocalDateTime createdAt) {
        String name = jdbc.queryForObject("select name from plans where id = ?", String.class, planId);
        return insert("insert into memberships(client_id,plan_id,plan_name,plan_duration_days,start_date,end_date,"
                        + "base_end_date,status,price_at_purchase,is_migrated,migration_note,activated_at,created_at)"
                        + " values (?,?,?,30,?,?,?,?,90000,?,?,?,?)",
                clientId, planId, name, start, end, end, status, migrated, migrated ? "Sistema anterior" : null,
                activatedAt == null ? null : Timestamp.valueOf(activatedAt), Timestamp.valueOf(createdAt));
    }

    private long cancelledMembership(long clientId, long planId, LocalDateTime activatedAt) {
        long id = membership(clientId, planId, activatedAt.toLocalDate().toString(),
                activatedAt.toLocalDate().plusDays(29).toString(), "ACTIVE", false, activatedAt, activatedAt);
        jdbc.update("update memberships set status = 'CANCELLED', cancellation_reason = 'Pago anulado' where id = ?", id);
        return id;
    }

    private long pendingMembership(long clientId, long planId) {
        return membership(clientId, planId, "2026-12-01", "2026-12-30", "PENDING", false, null, at("2026-11-10 07:00"));
    }

    private long payment(long membershipId, String amount, String discount, PaymentMethod method, String status,
                         LocalDateTime paidAt, LocalDateTime voidedAt) {
        BigDecimal total = new BigDecimal(amount);
        return insert("insert into payments(membership_id,amount,list_price,discount_amount,payment_method,status,paid_at,"
                        + "voided_at,void_reason,received_by) values (?,?,?,?,?,?,?,?,?,60)",
                membershipId, total, total.add(new BigDecimal(discount)), new BigDecimal(discount), method.name(), status,
                Timestamp.valueOf(paidAt), voidedAt == null ? null : Timestamp.valueOf(voidedAt),
                voidedAt == null ? null : "Error de digitación");
    }

    private long insert(String sql, Object... args) {
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            return ps;
        }, key);
        return key.getKey().longValue();
    }

    private static LocalDateTime at(String text) {
        return LocalDateTime.parse(text.replace(' ', 'T'));
    }
}
