package com.evolutionfit.modules.receipts;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** @param paperSize tamaño del PDF (por defecto A5 vertical) */
@ConfigurationProperties(prefix = "app.receipts")
public record ReceiptProperties(PaperSize paperSize) {

    public ReceiptProperties {
        paperSize = paperSize == null ? PaperSize.A5 : paperSize;
    }
}
