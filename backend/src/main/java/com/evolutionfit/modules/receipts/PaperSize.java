package com.evolutionfit.modules.receipts;

/**
 * Tamaño del comprobante (app.receipts.paper-size). Por defecto A5 vertical: no hay impresora y el PDF se
 * envía por WhatsApp, así que se lee en el celular. LETTER queda disponible si algún día se imprime.
 */
public enum PaperSize {
    A5,
    LETTER
}
