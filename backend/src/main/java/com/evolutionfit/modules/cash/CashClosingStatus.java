package com.evolutionfit.modules.cash;

/** Estado de un cierre diario. Solo los arqueos con diferencia quedan por revisar. */
public enum CashClosingStatus {
    /** Hubo movimientos en efectivo y falta el arqueo. */
    PENDING_COUNT,
    /** Sin movimientos en efectivo: el arqueo no es obligatorio. */
    NO_ACTIVITY,
    /** Arqueo sin diferencia. */
    COUNTED,
    /** Arqueo con diferencia: falta la revisión del ADMIN. */
    PENDING_REVIEW,
    /** Diferencia revisada por el ADMIN. */
    REVIEWED;

    public boolean isCounted() {
        return this == COUNTED || this == PENDING_REVIEW || this == REVIEWED;
    }
}
