package com.evolutionfit.modules.cash;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import com.evolutionfit.modules.cash.dto.CashSettingsRequest;
import com.evolutionfit.modules.cash.dto.WithdrawalRequest;
import com.evolutionfit.modules.cash.dto.WithdrawalResponse;
import com.evolutionfit.modules.clients.Client;
import com.evolutionfit.modules.clients.ClientRepository;
import com.evolutionfit.modules.memberships.MembershipService;
import com.evolutionfit.modules.memberships.dto.CreateMembershipRequest;
import com.evolutionfit.modules.payments.PaymentCommands;
import com.evolutionfit.modules.payments.PaymentMethod;
import com.evolutionfit.modules.payments.PaymentService;
import com.evolutionfit.modules.payments.dto.PaymentRequest;
import com.evolutionfit.modules.payments.dto.PaymentResponse;
import com.evolutionfit.modules.plans.Plan;
import com.evolutionfit.modules.plans.PlanRepository;
import com.evolutionfit.security.AuthUserDetails;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base de las pruebas de caja contra MySQL 8.4 real (Flyway aplica V1–V5), con un reloj que se mueve a mano
 * y sin el cierre programado (lo dispara cada prueba). Cada prueba empieza la caja en fechas que ninguna
 * otra usa, así los pagos de otras pruebas quedan antes del inicio de la caja. Datos ficticios.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "JWT_SECRET=clave-ficticia-solo-para-pruebas-de-caja-0123456789abcdef",
        "BOOTSTRAP_ADMIN_PASSWORD=",
        "app.cash.scheduler-enabled=false"
})
@Import(CashMySqlTestSupport.ClockConfig.class)
abstract class CashMySqlTestSupport {

    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    static {
        MYSQL.start(); // compartido por las clases de caja (mismo contexto de Spring)
    }

    protected static final BigDecimal MONTHLY = new BigDecimal("90000");
    private static final AtomicInteger PERIOD = new AtomicInteger();
    private static final AtomicInteger DOCUMENTS = new AtomicInteger(700000);

    @TestConfiguration
    static class ClockConfig {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock();
        }
    }

    @Autowired
    protected MutableClock clock;
    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    protected CashService cashService;
    @Autowired
    protected CashClosingService closingService;
    @Autowired
    protected CashCountService countService;
    @Autowired
    protected CashReportService reportService;
    @Autowired
    protected PaymentCommands paymentCommands;
    @Autowired
    protected PaymentService paymentService;
    @Autowired
    protected MembershipService membershipService;
    @Autowired
    protected ClientRepository clientRepository;
    @Autowired
    protected PlanRepository planRepository;

    protected final AuthUserDetails reception = new AuthUserDetails(50L, "recepcion_caja", "x", "RECEPTIONIST", true);
    protected final AuthUserDetails admin = new AuthUserDetails(51L, "admin_caja", "x", "ADMIN", true);
    protected Long planId;

    @BeforeEach
    void resetCash() {
        jdbc.update("insert ignore into roles(id,name) values (1,'ADMIN'),(2,'RECEPTIONIST')");
        jdbc.update("insert ignore into users(id,role_id,username,password_hash,first_name,last_name) values "
                + "(50,2,'recepcion_caja','x','Recepción','Prueba'),(51,1,'admin_caja','x','Admin','Prueba')");
        // Base de pruebas: se puede borrar la caja (nunca se hace en la base real).
        jdbc.update("update payments set cash_closing_id = null, void_cash_closing_id = null");
        jdbc.update("delete from cash_movements where movement_type = 'EXPENSE'");
        jdbc.update("delete from cash_closing_totals");
        jdbc.update("delete from cash_closings");
        jdbc.update("delete from gym_calendar_overrides");
        jdbc.update("update cash_settings set start_date = null, opening_base = 50000, "
                + "open_days = 'MON,TUE,WED,THU,FRI,SAT,SUN', closed_on_holidays = 1");
        planId = planRepository.findAll().stream().filter(p -> p.getPrice().compareTo(MONTHLY) == 0).findFirst()
                .orElseGet(() -> {
                    Plan p = new Plan();
                    p.setName("Mensual");
                    p.setDurationDays(30);
                    p.setPrice(MONTHLY);
                    return planRepository.save(p);
                }).getId();
    }

    /** Una fecha de inicio que no usa ninguna otra prueba, siempre posterior a las anteriores. */
    protected LocalDate newStartDate() {
        return LocalDate.of(2031, 1, 10).plusMonths(PERIOD.incrementAndGet()); // siempre el día 10: no cruza de mes
    }

    /** El ADMIN fija la fecha de inicio a las 8 a.m. de ese día. */
    protected LocalDate startCash() {
        LocalDate start = newStartDate();
        clock.set(start.atTime(8, 0));
        cashService.updateSettings(new CashSettingsRequest(new BigDecimal("50000"), start,
                EnumSet.allOf(DayOfWeek.class), null), admin.getId());
        return start;
    }

    protected void at(LocalDateTime moment) {
        clock.set(moment);
    }

    protected PaymentResponse pay(PaymentMethod method) {
        return pay(pendingMembership(), method);
    }

    protected PaymentResponse pay(Long membershipId, PaymentMethod method) {
        String reference = PaymentMethod.REFERENCE_REQUIRED.contains(method) ? "REF-" + membershipId : null;
        return paymentCommands.pay(membershipId, new PaymentRequest(UUID.randomUUID().toString(), method, reference,
                null, MONTHLY, null, null), reception);
    }

    protected Long pendingMembership() {
        Client c = new Client();
        c.setDocumentType("CC");
        c.setDocumentNumber(String.valueOf(DOCUMENTS.incrementAndGet()));
        c.setFirstName("Cliente");
        c.setLastName("Ficticio");
        Long clientId = clientRepository.save(c).getId();
        return membershipService.create(new CreateMembershipRequest(clientId, planId, null, null), 50L).id();
    }

    protected WithdrawalResponse withdraw(String amount) {
        return cashService.registerWithdrawal(new WithdrawalRequest(UUID.randomUUID().toString(), new BigDecimal(amount),
                WithdrawalCategory.SUPPLIES, "Servilletas"), reception);
    }

    protected CashClosing closing(LocalDate day) {
        return closingRepository().findByCashDate(day).orElseThrow();
    }

    @Autowired
    private CashClosingRepository closingRepositoryBean;

    protected CashClosingRepository closingRepository() {
        return closingRepositoryBean;
    }

    protected long audits(String entity, String action) {
        return jdbc.queryForObject("select count(*) from audit_logs where entity_type = ? and action = ?", Long.class,
                entity, action);
    }
}
