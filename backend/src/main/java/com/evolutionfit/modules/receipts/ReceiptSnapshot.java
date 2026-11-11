package com.evolutionfit.modules.receipts;

import java.time.LocalDateTime;

/** Lo que se imprime, congelado al emitir: número, fecha, encabezado del gimnasio y datos del pago. */
public record ReceiptSnapshot(String receiptNumber,
                              LocalDateTime issuedAt,
                              Header business,
                              ReceiptContent content) {

    /** Encabezado tal como estaba configurado al emitir. Los campos vacíos no se imprimen. */
    public record Header(String tradeName,
                         String legalName,
                         String taxId,
                         String commercialRegistration,
                         String address,
                         String city,
                         String phone,
                         String email) {

        static Header of(BusinessSettings s) {
            return new Header(s.getTradeName(), s.getLegalName(), s.getTaxId(), s.getCommercialRegistration(),
                    s.getAddress(), s.getCity(), s.getPhone(), s.getEmail());
        }
    }
}
