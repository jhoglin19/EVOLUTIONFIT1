package com.evolutionfit.modules.payments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import com.evolutionfit.common.exception.ConflictException;
import com.evolutionfit.modules.payments.dto.PaymentRequest;
import com.evolutionfit.modules.payments.dto.PaymentResponse;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class PaymentCommandsTest {

    private final PaymentService paymentService = mock(PaymentService.class);
    private final PaymentCommands commands = new PaymentCommands(paymentService, mock(EnrollmentService.class));
    private final PaymentRequest request = PaymentServiceTest.request(PaymentMethod.CASH, null, "90000", null, null);
    private final PaymentResponse first = mock(PaymentResponse.class);

    @Test
    void aSimultaneousRepeatGetsThePaymentOfTheFirstRequest() {
        when(paymentService.pay(eq(10L), any(), any())).thenThrow(new ConflictException("La membresía ya está pagada"));
        when(paymentService.findByRequestId(request.requestId())).thenReturn(Optional.of(first));
        assertThat(commands.pay(10L, request, PaymentServiceTest.RECEPTION)).isSameAs(first);

        when(paymentService.pay(eq(10L), any(), any())).thenThrow(new DataIntegrityViolationException("uq_payments_request_id"));
        assertThat(commands.pay(10L, request, PaymentServiceTest.RECEPTION)).isSameAs(first);
    }

    @Test
    void aRealConflictIsStillReported() {
        ConflictException conflict = new ConflictException("La membresía ya está pagada");
        when(paymentService.pay(eq(10L), any(), any())).thenThrow(conflict);
        when(paymentService.findByRequestId(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> commands.pay(10L, request, PaymentServiceTest.RECEPTION)).isSameAs(conflict);
    }
}
