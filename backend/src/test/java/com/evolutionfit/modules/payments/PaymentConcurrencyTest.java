package com.evolutionfit.modules.payments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.evolutionfit.common.exception.ConflictException;
import com.evolutionfit.modules.clients.Client;
import com.evolutionfit.modules.clients.ClientRepository;
import com.evolutionfit.modules.clients.dto.ClientRequest;
import com.evolutionfit.modules.memberships.MembershipRepository;
import com.evolutionfit.modules.memberships.MembershipService;
import com.evolutionfit.modules.memberships.MembershipStatus;
import com.evolutionfit.modules.memberships.dto.CreateMembershipRequest;
import com.evolutionfit.modules.payments.dto.EnrollmentRequest;
import com.evolutionfit.modules.payments.dto.PaymentRequest;
import com.evolutionfit.modules.payments.dto.PaymentResponse;
import com.evolutionfit.modules.plans.Plan;
import com.evolutionfit.modules.plans.PlanRepository;
import com.evolutionfit.modules.receipts.ReceiptService;
import com.evolutionfit.security.AuthUserDetails;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Pagos contra MySQL 8.4 real (Flyway aplica V1–V4): doble clic, solicitudes simultáneas, consecutivo
 * sin huecos aunque un pago falle, y anulación. Datos de prueba ficticios.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "JWT_SECRET=clave-ficticia-solo-para-pruebas-de-concurrencia-0123456789",
        "BOOTSTRAP_ADMIN_PASSWORD="
})
class PaymentConcurrencyTest {

    private static final int REQUESTS = 6;

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private PaymentCommands commands;
    @Autowired
    private PaymentService paymentService;
    @Autowired
    private MembershipService membershipService;
    @Autowired
    private MembershipRepository membershipRepository;
    @Autowired
    private ClientRepository clientRepository;
    @Autowired
    private PlanRepository planRepository;
    @Autowired
    private ReceiptService receiptService;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private TransactionTemplate tx;

    private AuthUserDetails reception;
    private Long planId;
    private static final AtomicInteger DOCUMENTS = new AtomicInteger(800000);

    @BeforeEach
    void setUp() {
        jdbc.update("insert ignore into roles(id,name) values (1,'ADMIN'),(2,'RECEPTIONIST')");
        jdbc.update("insert ignore into users(id,role_id,username,password_hash,first_name,last_name) "
                + "values (50,2,'recepcion_prueba','x','Recepción','Prueba')");
        reception = new AuthUserDetails(50L, "recepcion_prueba", "x", "RECEPTIONIST", true);
        planId = planRepository.findAll().stream().findFirst().orElseGet(() -> {
            Plan p = new Plan();
            p.setName("Mensual");
            p.setDurationDays(30);
            p.setPrice(new BigDecimal("90000"));
            return planRepository.save(p);
        }).getId();
    }

    private Client newClient() {
        Client c = new Client();
        c.setDocumentType("CC");
        c.setDocumentNumber(String.valueOf(DOCUMENTS.incrementAndGet()));
        c.setFirstName("Cliente");
        c.setLastName("Ficticio");
        return clientRepository.save(c);
    }

    private Long pendingMembership() {
        return membershipService.create(new CreateMembershipRequest(newClient().getId(), planId, null, null), 50L).id();
    }

    private static PaymentRequest cash(String requestId) {
        return new PaymentRequest(requestId, PaymentMethod.CASH, null, null, new BigDecimal("90000"), null, null);
    }

    /** Lanza todas las tareas a la vez; devuelve los resultados y cuenta los conflictos. */
    private <T> List<T> simultaneously(int n, Callable<T> task, AtomicInteger conflicts) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch ready = new CountDownLatch(n);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<T>> futures = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                return task.call();
            }));
        }
        ready.await();
        go.countDown();
        List<T> results = new ArrayList<>();
        for (Future<T> f : futures) {
            try {
                results.add(f.get(60, TimeUnit.SECONDS));
            } catch (ExecutionException e) {
                assertThat(e.getCause()).isInstanceOf(ConflictException.class);
                conflicts.incrementAndGet();
            }
        }
        pool.shutdown();
        return results;
    }

    private void assertReceiptNumbersHaveNoGaps() {
        var row = jdbc.queryForMap("select count(*) c, coalesce(min(sequence_number),1) mn, "
                + "coalesce(max(sequence_number),0) mx, count(distinct sequence_number) d from payment_receipts");
        long count = ((Number) row.get("c")).longValue();
        assertThat(((Number) row.get("mn")).longValue()).isEqualTo(1);
        assertThat(((Number) row.get("mx")).longValue()).isEqualTo(count);
        assertThat(((Number) row.get("d")).longValue()).isEqualTo(count);
        long next = jdbc.queryForObject("select next_value from receipt_sequence", Long.class);
        assertThat(next).isEqualTo(count + 1);
    }

    @Test
    void simultaneousChargesOfTheSameMembershipCreateOnlyOnePayment() throws Exception {
        Long membershipId = pendingMembership();
        AtomicInteger conflicts = new AtomicInteger();

        List<PaymentResponse> ok = simultaneously(REQUESTS,
                () -> commands.pay(membershipId, cash(UUID.randomUUID().toString()), reception), conflicts);

        assertThat(ok).hasSize(1);
        assertThat(conflicts.get()).isEqualTo(REQUESTS - 1);
        assertThat(jdbc.queryForObject("select count(*) from payments where membership_id = ?", Long.class, membershipId))
                .isEqualTo(1);
        assertReceiptNumbersHaveNoGaps();
    }

    @Test
    void doubleClickOnTheEnrollmentWizardCreatesOneClientOnePaymentAndReturnsTheSameToAll() throws Exception {
        String document = String.valueOf(DOCUMENTS.incrementAndGet());
        EnrollmentRequest request = new EnrollmentRequest(null,
                new ClientRequest("CC", document, "Cliente", "Ficticio", null, null, null, null, null),
                planId, null, null, cash(UUID.randomUUID().toString()));
        AtomicInteger conflicts = new AtomicInteger();

        List<PaymentResponse> results = simultaneously(REQUESTS, () -> commands.enroll(request, reception), conflicts);

        assertThat(conflicts.get()).isZero();
        assertThat(results).hasSize(REQUESTS);
        assertThat(results.stream().map(PaymentResponse::id).distinct()).hasSize(1);
        assertThat(results.get(0).membershipStatus()).isEqualTo(MembershipStatus.ACTIVE);
        assertThat(jdbc.queryForObject("select count(*) from clients where document_number = ?", Long.class, document))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                select count(*) from payments p join memberships m on m.id = p.membership_id
                join clients c on c.id = m.client_id where c.document_number = ?""", Long.class, document)).isEqualTo(1);
        assertReceiptNumbersHaveNoGaps();
    }

    @Test
    void simultaneousChargesOfDifferentClientsGetConsecutiveNumbersWithoutGaps() throws Exception {
        List<Long> memberships = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            memberships.add(pendingMembership());
        }
        AtomicInteger index = new AtomicInteger();

        List<PaymentResponse> results = simultaneously(10, () -> commands.pay(memberships.get(index.getAndIncrement()),
                cash(UUID.randomUUID().toString()), reception), new AtomicInteger());

        assertThat(results).hasSize(10);
        assertThat(results.stream().map(PaymentResponse::receiptNumber).distinct()).hasSize(10);
        assertReceiptNumbersHaveNoGaps();
    }

    @Test
    void aPaymentThatFailsAfterTakingANumberDoesNotLeaveAGap() {
        Long membershipId = pendingMembership();
        long before = jdbc.queryForObject("select next_value from receipt_sequence", Long.class);

        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            paymentService.pay(membershipId, cash(UUID.randomUUID().toString()), reception);
            throw new IllegalStateException("falla simulada después de emitir el comprobante");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(jdbc.queryForObject("select next_value from receipt_sequence", Long.class)).isEqualTo(before);
        assertThat(membershipRepository.findById(membershipId).orElseThrow().getStatus()).isEqualTo(MembershipStatus.PENDING);
        PaymentResponse paid = commands.pay(membershipId, cash(UUID.randomUUID().toString()), reception);
        assertThat(paid.receiptNumber()).isEqualTo(String.format("EF-%06d", before));
        assertReceiptNumbersHaveNoGaps();
    }

    @Test
    void voidingKeepsThePaymentAndItsNumberCancelsTheMembershipAndMarksThePdf() throws Exception {
        Long membershipId = pendingMembership();
        PaymentResponse paid = commands.pay(membershipId, cash(UUID.randomUUID().toString()), reception);

        PaymentResponse voided = paymentService.voidPayment(paid.id(), "Error de digitación", 50L);

        assertThat(voided.status()).isEqualTo(PaymentStatus.VOIDED);
        assertThat(voided.receiptNumber()).isEqualTo(paid.receiptNumber());
        assertThat(voided.membershipStatus()).isEqualTo(MembershipStatus.CANCELLED);
        assertThat(jdbc.queryForObject("select cancellation_reason from memberships where id = ?", String.class, membershipId))
                .isEqualTo("Error de digitación");
        String text = new PdfTextExtractor(new PdfReader(paymentService.receiptPdf(paid.id()).content())).getTextFromPage(1);
        assertThat(text).contains("ANULADO", "Error de digitación", paid.receiptNumber());
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where entity_type = 'PAYMENT' and action = 'VOID' "
                + "and entity_id = ?", Long.class, paid.id())).isEqualTo(1);
        assertThatThrownBy(() -> commands.pay(membershipId, cash(UUID.randomUUID().toString()), reception))
                .hasMessageContaining("anulada");
    }
}
