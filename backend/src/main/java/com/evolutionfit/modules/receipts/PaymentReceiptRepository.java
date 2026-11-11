package com.evolutionfit.modules.receipts;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentReceiptRepository extends JpaRepository<PaymentReceipt, Long> {

    Optional<PaymentReceipt> findByPaymentId(Long paymentId);

    List<PaymentReceipt> findByPaymentIdIn(Collection<Long> paymentIds);
}
