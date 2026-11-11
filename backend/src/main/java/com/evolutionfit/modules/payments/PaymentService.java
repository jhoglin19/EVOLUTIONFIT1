package com.evolutionfit.modules.payments;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.evolutionfit.common.exception.BusinessException;
import com.evolutionfit.common.exception.ConflictException;
import com.evolutionfit.common.exception.ResourceNotFoundException;
import com.evolutionfit.modules.audit.AuditService;
import com.evolutionfit.modules.clients.Client;
import com.evolutionfit.modules.clients.ClientRepository;
import com.evolutionfit.modules.memberships.Membership;
import com.evolutionfit.modules.memberships.MembershipRules;
import com.evolutionfit.modules.memberships.MembershipService;
import com.evolutionfit.modules.memberships.MembershipStatus;
import com.evolutionfit.modules.payments.dto.PaymentRequest;
import com.evolutionfit.modules.payments.dto.PaymentResponse;
import com.evolutionfit.modules.receipts.ReceiptContent;
import com.evolutionfit.modules.receipts.ReceiptPdfGenerator.VoidMark;
import com.evolutionfit.modules.receipts.ReceiptService;
import com.evolutionfit.modules.receipts.ReceiptService.ReceiptPdf;
import com.evolutionfit.modules.users.UserRepository;
import com.evolutionfit.security.AuthUserDetails;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pagos (docs/REGLAS_NEGOCIO.md, sección 3): sin abonos, el pago total activa la membresía pendiente y
 * emite su comprobante; descuentos y anulaciones solo ADMIN con motivo; nada se borra.
 */
@Service
public class PaymentService {

    private static final int MAX_PAGE_SIZE = 100;
    static final String AUDIT_ENTITY = "PAYMENT";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final PaymentRepository paymentRepository;
    private final ClientRepository clientRepository;
    private final UserRepository userRepository;
    private final MembershipService membershipService;
    private final ReceiptService receiptService;
    private final AuditService auditService;
    private final Clock clock;

    public PaymentService(PaymentRepository paymentRepository, ClientRepository clientRepository,
                          UserRepository userRepository, MembershipService membershipService,
                          ReceiptService receiptService, AuditService auditService, Clock clock) {
        this.paymentRepository = paymentRepository;
        this.clientRepository = clientRepository;
        this.userRepository = userRepository;
        this.membershipService = membershipService;
        this.receiptService = receiptService;
        this.auditService = auditService;
        this.clock = clock;
    }

    // ---- cobrar ----

    /**
     * Pago total de una membresía pendiente. Si el mismo {@code requestId} ya se registró (doble clic o
     * reintento), devuelve ese pago sin crear otro.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PaymentResponse pay(Long membershipId, PaymentRequest request, AuthUserDetails user) {
        String requestId = request.requestId().toLowerCase(Locale.ROOT);
        Optional<Payment> existing = paymentRepository.findByRequestId(requestId);
        if (existing.isPresent()) {
            return sameRequest(existing.get(), membershipId);
        }

        // Bloquea al cliente: dos cobros simultáneos de la misma membresía se ejecutan en serie.
        Membership m = membershipService.lockPendingForPayment(membershipId);
        PaymentMethod method = request.method();
        String reference = clean(request.reference());
        if (PaymentMethod.REFERENCE_REQUIRED.contains(method) && reference == null) {
            throw new BusinessException("La referencia es obligatoria para pagos por " + method.label());
        }

        BigDecimal listPrice = scale(m.getPriceAtPurchase());
        BigDecimal discount = scale(request.discountAmount() == null ? BigDecimal.ZERO : request.discountAmount());
        String discountReason = clean(request.discountReason());
        if (discount.signum() > 0) {
            if (!isAdmin(user)) {
                throw new BusinessException(HttpStatus.FORBIDDEN, "Solo el administrador puede aplicar descuentos");
            }
            if (discountReason == null) {
                throw new BusinessException("El motivo del descuento es obligatorio");
            }
            if (discount.compareTo(listPrice) > 0) {
                throw new BusinessException("El descuento no puede superar el valor del plan");
            }
        } else {
            discountReason = null;
        }
        // Total $0 solo es posible con un descuento total de un ADMIN o con un plan de precio 0.
        BigDecimal amount = listPrice.subtract(discount);
        if (scale(request.expectedAmount()).compareTo(amount) != 0) {
            throw new ConflictException("El valor a cobrar cambió: ahora es " + amount.toPlainString()
                    + ". Revisa el resumen antes de confirmar");
        }

        membershipService.activate(m, user.getId());

        Payment p = new Payment();
        p.setMembership(m);
        p.setListPrice(listPrice);
        p.setDiscountAmount(discount);
        p.setDiscountReason(discountReason);
        p.setDiscountedBy(discount.signum() > 0 ? user.getId() : null);
        p.setAmount(amount);
        p.setMethod(method);
        p.setReference(reference);
        p.setNotes(clean(request.notes()));
        p.setRequestId(requestId);
        p.setStatus(PaymentStatus.COMPLETED);
        p.setPaidAt(LocalDateTime.now(clock)); // sin pagos con fecha atrasada
        p.setReceivedBy(user.getId());
        Payment saved = paymentRepository.saveAndFlush(p);

        String receiptNumber = receiptService.issue(saved.getId(), receiptContent(saved, m, user), user.getId())
                .getReceiptNumber();

        auditService.record(AUDIT_ENTITY, saved.getId(), AuditService.ACTION_CREATE, null,
                PaymentSnapshot.of(saved, receiptNumber), user.getId());
        if (discount.signum() > 0) {
            auditService.record(AUDIT_ENTITY, saved.getId(), AuditService.ACTION_DISCOUNT, null,
                    Map.of("listPrice", listPrice, "discountAmount", discount, "reason", discountReason,
                            "amount", amount), user.getId());
        }
        return toResponse(saved, receiptNumber);
    }

    /** Pago ya registrado con este identificador, o vacío. Se usa tras un conflicto por solicitud repetida. */
    @Transactional(readOnly = true)
    public Optional<PaymentResponse> findByRequestId(String requestId) {
        if (requestId == null) {
            return Optional.empty();
        }
        return paymentRepository.findByRequestId(requestId.toLowerCase(Locale.ROOT))
                .map(p -> toResponse(p, receiptNumber(p.getId())));
    }

    // ---- anular ----

    /**
     * Anulación (solo ADMIN, lo exige el controlador). El pago queda VOIDED y no se borra; la membresía se
     * anula con el mismo motivo (reglas de la fase 4: la siguiente empieza hoy y su congelación se cancela).
     * El comprobante conserva su número y se reimprime marcado como ANULADO.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PaymentResponse voidPayment(Long id, String reason, Long currentUserId) {
        Long clientId = paymentRepository.findClientIdById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pago no encontrado"));
        clientRepository.findByIdForUpdate(clientId);
        Payment p = findOrThrow(id);
        if (p.getStatus() == PaymentStatus.VOIDED) {
            throw new ConflictException("El pago ya está anulado");
        }
        if (p.getStatus() != PaymentStatus.COMPLETED) {
            throw new BusinessException("Solo se pueden anular pagos completados");
        }
        String receiptNumber = receiptNumber(p.getId());
        PaymentSnapshot before = PaymentSnapshot.of(p, receiptNumber);
        p.setStatus(PaymentStatus.VOIDED);
        p.setVoidedAt(LocalDateTime.now(clock));
        p.setVoidedBy(currentUserId);
        p.setVoidReason(reason.trim());
        Payment saved = paymentRepository.save(p);
        auditService.record(AUDIT_ENTITY, id, AuditService.ACTION_VOID, before,
                PaymentSnapshot.of(saved, receiptNumber), currentUserId);

        if (saved.getMembership().getStatus() != MembershipStatus.CANCELLED) {
            membershipService.cancel(saved.getMembership().getId(), reason, currentUserId);
        }
        return toResponse(saved, receiptNumber);
    }

    // ---- consultas ----

    @Transactional(readOnly = true)
    public Page<PaymentResponse> list(LocalDate from, LocalDate to, PaymentMethod method, PaymentStatus status,
                                      String query, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Page<Payment> result = paymentRepository.findAll(
                PaymentSpecifications.filter(from, to, method, status, query),
                PageRequest.of(safePage, safeSize, Sort.by("paidAt").descending().and(Sort.by("id").descending())));
        Map<Long, String> receipts = receiptService.numbersByPayment(result.map(Payment::getId).getContent());
        return result.map(p -> toResponse(p, receipts.get(p.getId())));
    }

    @Transactional(readOnly = true)
    public PaymentResponse get(Long id) {
        Payment p = findOrThrow(id);
        return toResponse(p, receiptNumber(id));
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> clientPayments(Long clientId) {
        if (!clientRepository.existsById(clientId)) {
            throw new ResourceNotFoundException("Cliente no encontrado");
        }
        List<Payment> payments = paymentRepository.findByMembershipClientIdOrderByPaidAtDescIdDesc(clientId);
        Map<Long, String> receipts = receiptService.numbersByPayment(payments.stream().map(Payment::getId).toList());
        return payments.stream().map(p -> toResponse(p, receipts.get(p.getId()))).toList();
    }

    @Transactional(readOnly = true)
    public ReceiptPdf receiptPdf(Long paymentId) {
        Payment p = findOrThrow(paymentId);
        VoidMark mark = p.getStatus() == PaymentStatus.VOIDED ? new VoidMark(p.getVoidedAt(), p.getVoidReason()) : null;
        return receiptService.pdf(paymentId, mark);
    }

    // ---- helpers ----

    private PaymentResponse sameRequest(Payment existing, Long membershipId) {
        if (membershipId != null && !existing.getMembership().getId().equals(membershipId)) {
            throw new ConflictException("Este identificador de solicitud ya se usó en otro pago");
        }
        return toResponse(existing, receiptNumber(existing.getId()));
    }

    private ReceiptContent receiptContent(Payment p, Membership m, AuthUserDetails user) {
        Client c = m.getClient();
        String concept = "Membresía plan " + m.getPlanName() + " (" + m.getPlanDurationDays()
                + (m.getPlanDurationDays() == 1 ? " día" : " días") + ") del " + m.getStartDate().format(DATE)
                + " al " + m.getEndDate().format(DATE);
        String receivedBy = userRepository.findById(user.getId())
                .map(u -> u.getFirstName() + " " + u.getLastName())
                .orElse(user.getUsername());
        return new ReceiptContent(c.getFirstName() + " " + c.getLastName(), c.getDocumentType(),
                c.getDocumentNumber(), concept, m.getPlanName(), m.getStartDate(), m.getEndDate(),
                p.getListPrice(), p.getDiscountAmount(), p.getDiscountReason(), p.getAmount(),
                p.getMethod().label(), p.getReference(), receivedBy, p.getNotes());
    }

    private String receiptNumber(Long paymentId) {
        return receiptService.findByPayment(paymentId).map(r -> r.getReceiptNumber()).orElse(null);
    }

    private PaymentResponse toResponse(Payment p, String receiptNumber) {
        Membership m = p.getMembership();
        Client c = m.getClient();
        return new PaymentResponse(p.getId(), m.getId(), c.getId(), c.getFirstName() + " " + c.getLastName(),
                c.getDocumentNumber(), c.getPhone(), m.getPlanName(), m.getStartDate(), m.getEndDate(),
                MembershipRules.resolve(m, LocalDate.now(clock)), p.getListPrice(), p.getDiscountAmount(),
                p.getDiscountReason(), p.getAmount(), p.getMethod(), p.getReference(), p.getNotes(),
                p.getStatus(), p.getPaidAt(), p.getVoidedAt(), p.getVoidReason(), receiptNumber);
    }

    private Payment findOrThrow(Long id) {
        return paymentRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Pago no encontrado"));
    }

    private static boolean isAdmin(AuthUserDetails user) {
        return "ADMIN".equalsIgnoreCase(user.getRoleName());
    }

    private static BigDecimal scale(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** Lo que queda en audit_logs de un pago. */
    record PaymentSnapshot(Long membershipId, BigDecimal listPrice, BigDecimal discountAmount, String discountReason,
                           BigDecimal amount, PaymentMethod method, String reference, PaymentStatus status,
                           String voidReason, String receiptNumber) {

        static PaymentSnapshot of(Payment p, String receiptNumber) {
            return new PaymentSnapshot(p.getMembership().getId(), p.getListPrice(), p.getDiscountAmount(),
                    p.getDiscountReason(), p.getAmount(), p.getMethod(), p.getReference(), p.getStatus(),
                    p.getVoidReason(), receiptNumber);
        }
    }
}
