package com.evolutionfit.modules.payments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.evolutionfit.common.exception.BusinessException;
import com.evolutionfit.common.exception.ConflictException;
import com.evolutionfit.modules.audit.AuditService;
import com.evolutionfit.modules.clients.Client;
import com.evolutionfit.modules.clients.ClientRepository;
import com.evolutionfit.modules.memberships.Membership;
import com.evolutionfit.modules.memberships.MembershipService;
import com.evolutionfit.modules.memberships.MembershipStatus;
import com.evolutionfit.modules.payments.dto.PaymentRequest;
import com.evolutionfit.modules.payments.dto.PaymentResponse;
import com.evolutionfit.modules.plans.Plan;
import com.evolutionfit.modules.receipts.PaymentReceipt;
import com.evolutionfit.modules.receipts.ReceiptContent;
import com.evolutionfit.modules.receipts.ReceiptPdfGenerator.VoidMark;
import com.evolutionfit.modules.receipts.ReceiptService;
import com.evolutionfit.modules.users.UserRepository;
import com.evolutionfit.security.AuthUserDetails;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentServiceTest {

    static final LocalDate TODAY = LocalDate.of(2026, 11, 10);
    static final Clock CLOCK = Clock.fixed(TODAY.atTime(10, 0).atZone(ZoneId.of("America/Bogota")).toInstant(),
            ZoneId.of("America/Bogota"));
    static final AuthUserDetails ADMIN = new AuthUserDetails(7L, "admin", "x", "ADMIN", true);
    static final AuthUserDetails RECEPTION = new AuthUserDetails(8L, "recepcion", "x", "RECEPTIONIST", true);

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private ClientRepository clientRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private MembershipService membershipService;
    @Mock
    private ReceiptService receiptService;
    @Mock
    private AuditService auditService;

    private PaymentService service;
    private Membership membership;

    @BeforeEach
    void setUp() {
        service = new PaymentService(paymentRepository, clientRepository, userRepository, membershipService,
                receiptService, auditService, CLOCK);
        membership = pendingMembership(10L, "90000.00");
        when(membershipService.lockPendingForPayment(10L)).thenReturn(membership);
        // Simula la activación: el servicio de membresías fija fechas y estado.
        doAnswer(inv -> {
            Membership m = inv.getArgument(0);
            m.setStartDate(TODAY);
            m.setEndDate(TODAY.plusDays(29));
            m.setStatus(MembershipStatus.ACTIVE);
            return null;
        }).when(membershipService).activate(any(), anyLong());
        when(paymentRepository.findByRequestId(any())).thenReturn(Optional.empty());
        when(paymentRepository.saveAndFlush(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            p.setId(100L);
            return p;
        });
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        PaymentReceipt receipt = new PaymentReceipt();
        receipt.setReceiptNumber("EF-000001");
        when(receiptService.issue(anyLong(), any(), any())).thenReturn(receipt);
    }

    static Membership pendingMembership(long id, String price) {
        Client c = new Client();
        c.setId(1L);
        c.setDocumentType("CC");
        c.setDocumentNumber("1001");
        c.setFirstName("Ana");
        c.setLastName("Pérez");
        c.setPhone("300 000 0000");
        Plan plan = new Plan();
        plan.setId(2L);
        Membership m = new Membership();
        m.setId(id);
        m.setClient(c);
        m.setPlan(plan);
        m.setPlanName("Mensual");
        m.setPlanDurationDays(30);
        m.setPriceAtPurchase(new BigDecimal(price));
        m.setStartDate(TODAY);
        m.setBaseEndDate(TODAY.plusDays(29));
        m.setEndDate(TODAY.plusDays(29));
        m.setStatus(MembershipStatus.PENDING);
        return m;
    }

    static PaymentRequest request(PaymentMethod method, String reference, String expected, String discount,
                                  String reason) {
        return new PaymentRequest(UUID.randomUUID().toString(), method, reference, null, new BigDecimal(expected),
                discount == null ? null : new BigDecimal(discount), reason);
    }

    private Payment savedPayment() {
        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).saveAndFlush(captor.capture());
        return captor.getValue();
    }

    // ---- cobrar ----

    @Test
    void fullPaymentActivatesTheMembershipAndIssuesTheReceipt() {
        PaymentRequest req = new PaymentRequest(UUID.randomUUID().toString().toUpperCase(), PaymentMethod.CASH,
                " ", " Pagó con billete ", new BigDecimal("90000"), null, null);

        PaymentResponse response = service.pay(10L, req, RECEPTION);

        Payment p = savedPayment();
        assertThat(p.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(p.getAmount()).isEqualByComparingTo("90000");
        assertThat(p.getListPrice()).isEqualByComparingTo("90000");
        assertThat(p.getDiscountAmount()).isEqualByComparingTo("0");
        assertThat(p.getReference()).isNull();
        assertThat(p.getNotes()).isEqualTo("Pagó con billete");
        assertThat(p.getRequestId()).isEqualTo(req.requestId().toLowerCase());
        assertThat(p.getPaidAt()).isEqualTo(TODAY.atTime(10, 0));
        assertThat(p.getReceivedBy()).isEqualTo(8L);
        verify(membershipService).activate(membership, 8L);

        ArgumentCaptor<ReceiptContent> content = ArgumentCaptor.forClass(ReceiptContent.class);
        verify(receiptService).issue(eq(100L), content.capture(), eq(8L));
        assertThat(content.getValue().concept()).isEqualTo("Membresía plan Mensual (30 días) del 10/11/2026 al 09/12/2026");
        assertThat(content.getValue().paymentMethod()).isEqualTo("Efectivo");
        assertThat(content.getValue().clientName()).isEqualTo("Ana Pérez");

        assertThat(response.receiptNumber()).isEqualTo("EF-000001");
        assertThat(response.clientPhone()).isEqualTo("300 000 0000"); // para el enlace de WhatsApp
        assertThat(response.membershipStatus()).isEqualTo(MembershipStatus.ACTIVE);
        verify(auditService).record(eq("PAYMENT"), eq(100L), eq(AuditService.ACTION_CREATE), isNull(), any(), eq(8L));
        verify(auditService, never()).record(any(), any(), eq(AuditService.ACTION_DISCOUNT), any(), any(), any());
    }

    @ParameterizedTest
    @EnumSource(value = PaymentMethod.class, names = {"BANK_TRANSFER", "NEQUI", "DAVIPLATA"})
    void referenceIsMandatoryForTransfersNequiAndDaviplata(PaymentMethod method) {
        assertThatThrownBy(() -> service.pay(10L, request(method, "  ", "90000", null, null), RECEPTION))
                .isInstanceOf(BusinessException.class).hasMessageContaining("referencia es obligatoria");
        verify(membershipService, never()).activate(any(), anyLong());

        service.pay(10L, request(method, " 123456 ", "90000", null, null), RECEPTION);
        assertThat(savedPayment().getReference()).isEqualTo("123456");
    }

    @ParameterizedTest
    @EnumSource(value = PaymentMethod.class, names = {"CASH", "CARD", "OTHER"})
    void referenceIsOptionalForOtherMethods(PaymentMethod method) {
        service.pay(10L, request(method, null, "90000", null, null), RECEPTION);
        assertThat(savedPayment().getMethod()).isEqualTo(method);
    }

    @Test
    void receptionCannotApplyDiscounts() {
        assertThatThrownBy(() -> service.pay(10L, request(PaymentMethod.CASH, null, "80000", "10000", "Amigo"), RECEPTION))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN))
                .hasMessageContaining("Solo el administrador");
        verify(membershipService, never()).activate(any(), anyLong());
        verify(paymentRepository, never()).saveAndFlush(any());
    }

    @Test
    void adminDiscountNeedsAReasonAndCannotExceedThePrice() {
        assertThatThrownBy(() -> service.pay(10L, request(PaymentMethod.CASH, null, "80000", "10000", " "), ADMIN))
                .isInstanceOf(BusinessException.class).hasMessageContaining("motivo del descuento");
        assertThatThrownBy(() -> service.pay(10L, request(PaymentMethod.CASH, null, "0", "90000.01", "x"), ADMIN))
                .isInstanceOf(BusinessException.class).hasMessageContaining("no puede superar");
    }

    @Test
    void adminDiscountIsRecordedAndAudited() {
        service.pay(10L, request(PaymentMethod.CASH, null, "80000", "10000", " Convenio empresa "), ADMIN);

        Payment p = savedPayment();
        assertThat(p.getAmount()).isEqualByComparingTo("80000");
        assertThat(p.getDiscountAmount()).isEqualByComparingTo("10000");
        assertThat(p.getDiscountReason()).isEqualTo("Convenio empresa");
        assertThat(p.getDiscountedBy()).isEqualTo(7L);
        verify(auditService).record(eq("PAYMENT"), eq(100L), eq(AuditService.ACTION_DISCOUNT), isNull(),
                eq(Map.of("listPrice", new BigDecimal("90000.00"), "discountAmount", new BigDecimal("10000.00"),
                        "reason", "Convenio empresa", "amount", new BigDecimal("80000.00"))), eq(7L));
    }

    @Test
    void zeroTotalOnlyWithAFullAdminDiscountOrAZeroPricePlan() {
        service.pay(10L, request(PaymentMethod.CASH, null, "0", "90000", "Cortesía"), ADMIN);
        assertThat(savedPayment().getAmount()).isEqualByComparingTo("0");

        Membership free = pendingMembership(11L, "0");
        when(membershipService.lockPendingForPayment(11L)).thenReturn(free);
        service.pay(11L, request(PaymentMethod.OTHER, null, "0", null, null), RECEPTION);
        verify(receiptService).issue(anyLong(), any(), eq(8L));
    }

    @Test
    void theAmountShownMustMatchTheCalculatedOne() {
        assertThatThrownBy(() -> service.pay(10L, request(PaymentMethod.CASH, null, "85000", null, null), RECEPTION))
                .isInstanceOf(ConflictException.class).hasMessageContaining("cambió");
        verify(membershipService, never()).activate(any(), anyLong());
    }

    @Test
    void aRepeatedRequestReturnsTheSamePaymentWithoutChargingAgain() {
        Payment existing = new Payment();
        existing.setId(55L);
        existing.setMembership(membership);
        existing.setMethod(PaymentMethod.CASH);
        existing.setStatus(PaymentStatus.COMPLETED);
        existing.setAmount(new BigDecimal("90000"));
        PaymentRequest req = request(PaymentMethod.CASH, null, "90000", null, null);
        when(paymentRepository.findByRequestId(req.requestId())).thenReturn(Optional.of(existing));

        assertThat(service.pay(10L, req, RECEPTION).id()).isEqualTo(55L);
        verify(membershipService, never()).lockPendingForPayment(anyLong());
        verify(paymentRepository, never()).saveAndFlush(any());

        assertThatThrownBy(() -> service.pay(99L, req, RECEPTION))
                .isInstanceOf(ConflictException.class).hasMessageContaining("otro pago");
    }

    @Test
    void anAlreadyPaidMembershipCannotBePaidAgain() {
        when(membershipService.lockPendingForPayment(10L)).thenThrow(new ConflictException("La membresía ya está pagada"));
        assertThatThrownBy(() -> service.pay(10L, request(PaymentMethod.CASH, null, "90000", null, null), RECEPTION))
                .isInstanceOf(ConflictException.class);
        verify(paymentRepository, never()).saveAndFlush(any());
    }

    // ---- anular ----

    private Payment completed() {
        membership.setStatus(MembershipStatus.ACTIVE);
        Payment p = new Payment();
        p.setId(100L);
        p.setMembership(membership);
        p.setMethod(PaymentMethod.CASH);
        p.setStatus(PaymentStatus.COMPLETED);
        p.setListPrice(new BigDecimal("90000"));
        p.setAmount(new BigDecimal("90000"));
        when(paymentRepository.findClientIdById(100L)).thenReturn(Optional.of(1L));
        when(paymentRepository.findById(100L)).thenReturn(Optional.of(p));
        return p;
    }

    @Test
    void voidingKeepsThePaymentAndCancelsTheMembershipWithTheSameReason() {
        Payment p = completed();

        PaymentResponse response = service.voidPayment(100L, " Error de digitación ", 7L);

        assertThat(response.status()).isEqualTo(PaymentStatus.VOIDED);
        assertThat(p.getVoidReason()).isEqualTo("Error de digitación");
        assertThat(p.getVoidedBy()).isEqualTo(7L);
        assertThat(p.getVoidedAt()).isEqualTo(TODAY.atTime(10, 0));
        verify(clientRepository).findByIdForUpdate(1L);
        verify(membershipService).cancel(10L, " Error de digitación ", 7L);
        verify(paymentRepository, never()).delete(any(Payment.class));
        verify(auditService).record(eq("PAYMENT"), eq(100L), eq(AuditService.ACTION_VOID), any(), any(), eq(7L));
    }

    @Test
    void voidingTwiceIsAConflict() {
        completed().setStatus(PaymentStatus.VOIDED);
        assertThatThrownBy(() -> service.voidPayment(100L, "x", 7L)).isInstanceOf(ConflictException.class);
    }

    @Test
    void voidingWhenTheMembershipWasAlreadyCancelledDoesNotCancelItAgain() {
        completed();
        membership.setStatus(MembershipStatus.CANCELLED);
        service.voidPayment(100L, "x", 7L);
        verify(membershipService, never()).cancel(anyLong(), any(), any());
    }

    @Test
    void theReceiptOfAVoidedPaymentIsMarked() {
        Payment p = completed();
        service.voidPayment(100L, "Error", 7L);

        service.receiptPdf(100L);
        verify(receiptService).pdf(100L, new VoidMark(p.getVoidedAt(), "Error"));
    }
}
