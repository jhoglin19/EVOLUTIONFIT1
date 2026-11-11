package com.evolutionfit.modules.payments;

import java.util.function.Supplier;

import com.evolutionfit.common.exception.ConflictException;
import com.evolutionfit.modules.payments.dto.EnrollmentRequest;
import com.evolutionfit.modules.payments.dto.PaymentRequest;
import com.evolutionfit.modules.payments.dto.PaymentResponse;
import com.evolutionfit.security.AuthUserDetails;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Cobros idempotentes. No abre transacción a propósito: si dos solicitudes con el mismo identificador
 * llegan a la vez, la segunda choca con la primera (membresía ya pagada, documento ya registrado o
 * request_id repetido) y su transacción se revierte. Entonces, ya fuera de esa transacción, se devuelve
 * el pago que registró la primera en lugar de un error.
 */
@Service
public class PaymentCommands {

    private final PaymentService paymentService;
    private final EnrollmentService enrollmentService;

    public PaymentCommands(PaymentService paymentService, EnrollmentService enrollmentService) {
        this.paymentService = paymentService;
        this.enrollmentService = enrollmentService;
    }

    public PaymentResponse enroll(EnrollmentRequest request, AuthUserDetails user) {
        return idempotent(request.payment().requestId(), () -> enrollmentService.enroll(request, user));
    }

    public PaymentResponse pay(Long membershipId, PaymentRequest request, AuthUserDetails user) {
        return idempotent(request.requestId(), () -> paymentService.pay(membershipId, request, user));
    }

    private PaymentResponse idempotent(String requestId, Supplier<PaymentResponse> action) {
        try {
            return action.get();
        } catch (ConflictException | DataIntegrityViolationException e) {
            return paymentService.findByRequestId(requestId).orElseThrow(() -> e);
        }
    }
}
