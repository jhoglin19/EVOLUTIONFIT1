package com.evolutionfit.modules.receipts;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Comprobante interno de un pago (no es factura DIAN). Su número no se reutiliza nunca. */
@Entity
@Table(name = "payment_receipts")
@Getter
@Setter
@NoArgsConstructor
public class PaymentReceipt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_id", nullable = false, unique = true)
    private Long paymentId;

    @Column(name = "sequence_number", nullable = false, unique = true)
    private long sequenceNumber;

    @Column(name = "receipt_number", nullable = false, unique = true, length = 30)
    private String receiptNumber;

    /** Todo lo impreso, en JSON ({@link ReceiptSnapshot}): una reimpresión sale igual. */
    @Column(name = "snapshot", nullable = false, columnDefinition = "json")
    private String snapshot;

    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt;

    @Column(name = "issued_by")
    private Long issuedBy;
}
