package com.evolutionfit.modules.receipts;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** @param paperSize tamaño del PDF (por defecto carta) */
@ConfigurationProperties(prefix = "app.receipts")
public record ReceiptProperties(PaperSize paperSize) {

    public ReceiptProperties {
        paperSize = paperSize == null ? PaperSize.LETTER : paperSize;
    }
}
