package com.evolutionfit.modules.payments;

/**
 * Sin abonos ni pasarela, un pago nace COMPLETED y solo puede pasar a VOIDED (anulado por un ADMIN).
 * PENDING y REFUNDED existen en el esquema (V1) pero no se usan: devoluciones no están definidas.
 */
public enum PaymentStatus {
    PENDING,
    COMPLETED,
    VOIDED,
    REFUNDED
}
