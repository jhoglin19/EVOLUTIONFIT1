package com.evolutionfit.modules.receipts;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface ReceiptSequenceRepository extends JpaRepository<ReceiptSequence, Short> {

    /**
     * SELECT ... FOR UPDATE: los pagos simultáneos toman número de uno en uno. Como el incremento va en
     * la misma transacción del pago, si el pago falla el número vuelve atrás y no quedan huecos.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ReceiptSequence s where s.id = 1")
    Optional<ReceiptSequence> findForUpdate();
}
