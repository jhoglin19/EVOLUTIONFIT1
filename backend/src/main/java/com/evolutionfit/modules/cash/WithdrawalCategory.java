package com.evolutionfit.modules.cash;

/** Categorías de retiros de efectivo. Agregar una no requiere migración (la columna no tiene CHECK). */
public enum WithdrawalCategory {
    SUPPLIES("Insumos"),
    CLEANING("Aseo"),
    OTHER("Otros");

    private final String label;

    WithdrawalCategory(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
