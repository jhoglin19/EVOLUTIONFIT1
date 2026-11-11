package com.evolutionfit.modules.payments;

import java.util.Optional;

import com.evolutionfit.common.exception.BusinessException;
import com.evolutionfit.modules.clients.ClientService;
import com.evolutionfit.modules.memberships.MembershipService;
import com.evolutionfit.modules.memberships.dto.CreateMembershipRequest;
import com.evolutionfit.modules.memberships.dto.MembershipResponse;
import com.evolutionfit.modules.payments.dto.EnrollmentRequest;
import com.evolutionfit.modules.payments.dto.PaymentResponse;
import com.evolutionfit.security.AuthUserDetails;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Asistente de inscripción en una sola transacción: cliente (nuevo o existente), membresía, pago total,
 * activación y comprobante. Si cualquier paso falla, se revierte todo.
 */
@Service
public class EnrollmentService {

    private final ClientService clientService;
    private final MembershipService membershipService;
    private final PaymentService paymentService;

    public EnrollmentService(ClientService clientService, MembershipService membershipService,
                             PaymentService paymentService) {
        this.clientService = clientService;
        this.membershipService = membershipService;
        this.paymentService = paymentService;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PaymentResponse enroll(EnrollmentRequest request, AuthUserDetails user) {
        Optional<PaymentResponse> existing = paymentService.findByRequestId(request.payment().requestId());
        if (existing.isPresent()) {
            return existing.get();
        }
        if ((request.clientId() == null) == (request.client() == null)) {
            throw new BusinessException("Indica un cliente existente o los datos de un cliente nuevo");
        }
        Long clientId = request.clientId() != null
                ? request.clientId()
                : clientService.create(request.client()).id();
        MembershipResponse membership = membershipService.create(
                new CreateMembershipRequest(clientId, request.planId(), request.startDate(), request.notes()),
                user.getId());
        return paymentService.pay(membership.id(), request.payment(), user);
    }
}
