package com.evolutionfit.modules.payments;

import java.util.EnumSet;
import java.util.Set;

/** Métodos aceptados (docs/REGLAS_NEGOCIO.md, sección 3). No hay pasarela de pagos. */
public enum PaymentMethod {
    CASH("Efectivo"),
    BANK_TRANSFER("Transferencia bancaria"),
    NEQUI("Nequi"),
    DAVIPLATA("Daviplata"),
    CARD("Tarjeta"),
    OTHER("Otro");

    /** Métodos con referencia obligatoria. */
    public static final Set<PaymentMethod> REFERENCE_REQUIRED = EnumSet.of(BANK_TRANSFER, NEQUI, DAVIPLATA);

    private final String label;

    PaymentMethod(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
