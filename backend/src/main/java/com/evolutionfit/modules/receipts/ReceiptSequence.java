package com.evolutionfit.modules.receipts;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Siguiente número de comprobante. Una sola fila (id = 1). */
@Entity
@Table(name = "receipt_sequence")
@Getter
@Setter
@NoArgsConstructor
public class ReceiptSequence {

    @Id
    private Short id;

    @Column(name = "next_value", nullable = false)
    private long nextValue;
}
