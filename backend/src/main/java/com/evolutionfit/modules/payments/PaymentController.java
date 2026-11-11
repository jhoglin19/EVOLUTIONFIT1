package com.evolutionfit.modules.payments;

import java.time.LocalDate;
import java.util.List;

import com.evolutionfit.common.PageResponse;
import com.evolutionfit.modules.memberships.dto.ReasonRequest;
import com.evolutionfit.modules.payments.dto.EnrollmentRequest;
import com.evolutionfit.modules.payments.dto.PaymentRequest;
import com.evolutionfit.modules.payments.dto.PaymentResponse;
import com.evolutionfit.modules.receipts.ReceiptService.ReceiptPdf;
import com.evolutionfit.security.AuthUserDetails;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
@Tag(name = "Pagos", description = "Cobro de membresías, inscripción, comprobantes (anular: solo ADMIN)")
public class PaymentController {

    private final PaymentService paymentService;
    private final PaymentCommands commands;

    public PaymentController(PaymentService paymentService, PaymentCommands commands) {
        this.paymentService = paymentService;
        this.commands = commands;
    }

    /** Asistente: cliente nuevo o existente + plan + pago total, en una transacción. */
    @PostMapping("/enrollments")
    public ResponseEntity<PaymentResponse> enroll(@Valid @RequestBody EnrollmentRequest request,
                                                  @AuthenticationPrincipal AuthUserDetails current) {
        return created(commands.enroll(request, current));
    }

    /** Cobrar una membresía pendiente existente. */
    @PostMapping("/memberships/{id}/payments")
    public ResponseEntity<PaymentResponse> pay(@PathVariable Long id, @Valid @RequestBody PaymentRequest request,
                                               @AuthenticationPrincipal AuthUserDetails current) {
        return created(commands.pay(id, request, current));
    }

    @GetMapping("/payments")
    public PageResponse<PaymentResponse> list(@RequestParam(required = false)
                                              @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                              @RequestParam(required = false)
                                              @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                              @RequestParam(required = false) PaymentMethod method,
                                              @RequestParam(required = false) PaymentStatus status,
                                              @RequestParam(required = false) String q,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return PageResponse.from(paymentService.list(from, to, method, status, q, page, size));
    }

    @GetMapping("/payments/{id}")
    public PaymentResponse get(@PathVariable Long id) {
        return paymentService.get(id);
    }

    @GetMapping("/clients/{clientId}/payments")
    public List<PaymentResponse> clientPayments(@PathVariable Long clientId) {
        return paymentService.clientPayments(clientId);
    }

    @GetMapping(value = "/payments/{id}/receipt.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> receipt(@PathVariable Long id) {
        ReceiptPdf pdf = paymentService.receiptPdf(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + pdf.receiptNumber() + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf.content());
    }

    @PostMapping("/payments/{id}/void")
    @PreAuthorize("hasRole('ADMIN')")
    public PaymentResponse voidPayment(@PathVariable Long id, @Valid @RequestBody ReasonRequest request,
                                       @AuthenticationPrincipal AuthUserDetails current) {
        return paymentService.voidPayment(id, request.reason(), current.getId());
    }

    // ---- helpers ----

    private static ResponseEntity<PaymentResponse> created(PaymentResponse body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }
}
