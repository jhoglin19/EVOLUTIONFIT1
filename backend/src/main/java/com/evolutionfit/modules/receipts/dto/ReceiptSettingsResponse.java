package com.evolutionfit.modules.receipts.dto;

import java.time.LocalDateTime;

/**
 * @param nextReceiptNumber   número que llevará el próximo comprobante (con prefijo)
 * @param initialNumberLocked ya se emitió al menos un comprobante: el número inicial no se puede cambiar
 */
public record ReceiptSettingsResponse(String tradeName,
                                      String legalName,
                                      String taxId,
                                      String commercialRegistration,
                                      String address,
                                      String city,
                                      String phone,
                                      String email,
                                      String receiptPrefix,
                                      long nextNumber,
                                      String nextReceiptNumber,
                                      boolean initialNumberLocked,
                                      boolean hasLogo,
                                      int logoSizeBytes,
                                      int maxLogoSizeBytes,
                                      LocalDateTime updatedAt) {
}
