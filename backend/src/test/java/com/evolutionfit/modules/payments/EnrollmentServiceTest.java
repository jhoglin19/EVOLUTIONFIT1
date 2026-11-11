package com.evolutionfit.modules.payments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import com.evolutionfit.common.exception.BusinessException;
import com.evolutionfit.modules.clients.ClientService;
import com.evolutionfit.modules.clients.dto.ClientRequest;
import com.evolutionfit.modules.clients.dto.ClientResponse;
import com.evolutionfit.modules.memberships.MembershipService;
import com.evolutionfit.modules.memberships.dto.CreateMembershipRequest;
import com.evolutionfit.modules.memberships.dto.MembershipResponse;
import com.evolutionfit.modules.payments.dto.EnrollmentRequest;
import com.evolutionfit.modules.payments.dto.PaymentRequest;
import com.evolutionfit.modules.payments.dto.PaymentResponse;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class EnrollmentServiceTest {

    private final ClientService clientService = mock(ClientService.class);
    private final MembershipService membershipService = mock(MembershipService.class);
    private final PaymentService paymentService = mock(PaymentService.class);
    private final EnrollmentService service = new EnrollmentService(clientService, membershipService, paymentService);
    private final PaymentRequest payment = PaymentServiceTest.request(PaymentMethod.CASH, null, "90000", null, null);
    private final ClientRequest newClient = new ClientRequest("CC", "1001", "Ana", "Pérez", null, null, null, null, null);

    @Test
    void newClientMembershipAndPaymentInOrder() {
        ClientResponse client = mock(ClientResponse.class);
        when(client.id()).thenReturn(5L);
        when(clientService.create(newClient)).thenReturn(client);
        MembershipResponse membership = mock(MembershipResponse.class);
        when(membership.id()).thenReturn(50L);
        when(membershipService.create(any(), eq(8L))).thenReturn(membership);
        PaymentResponse paid = mock(PaymentResponse.class);
        when(paymentService.findByRequestId(any())).thenReturn(Optional.empty());
        when(paymentService.pay(50L, payment, PaymentServiceTest.RECEPTION)).thenReturn(paid);

        assertThat(service.enroll(new EnrollmentRequest(null, newClient, 2L, null, null, payment),
                PaymentServiceTest.RECEPTION)).isSameAs(paid);

        InOrder order = inOrder(clientService, membershipService, paymentService);
        order.verify(clientService).create(newClient);
        order.verify(membershipService).create(new CreateMembershipRequest(5L, 2L, null, null), 8L);
        order.verify(paymentService).pay(50L, payment, PaymentServiceTest.RECEPTION);
    }

    @Test
    void existingClientRenews() {
        MembershipResponse membership = mock(MembershipResponse.class);
        when(membership.id()).thenReturn(51L);
        when(membershipService.create(any(), eq(8L))).thenReturn(membership);
        when(paymentService.findByRequestId(any())).thenReturn(Optional.empty());

        service.enroll(new EnrollmentRequest(3L, null, 2L, null, null, payment), PaymentServiceTest.RECEPTION);

        verifyNoInteractions(clientService);
    }

    @Test
    void exactlyOneOfExistingOrNewClient() {
        when(paymentService.findByRequestId(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.enroll(new EnrollmentRequest(null, null, 2L, null, null, payment),
                PaymentServiceTest.RECEPTION)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.enroll(new EnrollmentRequest(3L, newClient, 2L, null, null, payment),
                PaymentServiceTest.RECEPTION)).isInstanceOf(BusinessException.class);
    }

    @Test
    void aRepeatedEnrollmentDoesNotCreateAnything() {
        PaymentResponse paid = mock(PaymentResponse.class);
        when(paymentService.findByRequestId(payment.requestId())).thenReturn(Optional.of(paid));

        assertThat(service.enroll(new EnrollmentRequest(null, newClient, 2L, null, null, payment),
                PaymentServiceTest.RECEPTION)).isSameAs(paid);
        verifyNoInteractions(clientService, membershipService);
    }
}
