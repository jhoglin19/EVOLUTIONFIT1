package com.evolutionfit.modules.receipts.dto;

/**
 * Lo único de la configuración del gimnasio que ve recepción: el nombre comercial, para el mensaje de
 * WhatsApp. Null si aún no se ha configurado.
 */
public record BusinessInfoResponse(String tradeName) {
}
