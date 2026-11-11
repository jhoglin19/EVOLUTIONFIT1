package com.evolutionfit.modules.receipts;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;

import javax.imageio.ImageIO;

import com.evolutionfit.modules.receipts.ReceiptPdfGenerator.VoidMark;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;

import org.junit.jupiter.api.Test;

class ReceiptPdfGeneratorTest {

    private final ReceiptPdfGenerator generator = new ReceiptPdfGenerator();
    private final LocalDateTime issued = LocalDateTime.of(2026, 11, 10, 10, 0);

    private static String text(byte[] pdf) throws Exception {
        PdfReader reader = new PdfReader(pdf);
        try {
            return new PdfTextExtractor(reader).getTextFromPage(1);
        } finally {
            reader.close();
        }
    }

    private ReceiptSnapshot snapshot(String discount) {
        return new ReceiptSnapshot("EF-000001", issued,
                ReceiptSnapshot.Header.of(ReceiptFixtures.settings("EF-")), ReceiptFixtures.content(discount));
    }

    @Test
    void containsTheHeaderNumberClientDetailAndTheInternalDocumentNote() throws Exception {
        String text = text(generator.generate(snapshot("10000"), null, null, false, PaperSize.A5));

        assertThat(text).contains("Gimnasio Ficticio", "Empresa Ficticia S.A.S.", "NIT 000.000.000-0",
                "Matrícula mercantil 00000-00", "Calle Ficticia 1 # 2-3", "Ciudad Ficticia",
                "contacto@ejemplo.invalid");
        assertThat(text).contains("COMPROBANTE INTERNO DE PAGO", "No. EF-000001", "10/11/2026");
        assertThat(text).contains("Cliente Ficticio", "CC 0000000001", "Membresía plan Mensual");
        assertThat(text).contains("$ 90.000", "Descuento: Convenio ficticio", "- $ 10.000", "$ 80.000");
        assertThat(text).contains("Nequi", "REF-0001", "Usuario Ficticio");
        assertThat(text).contains("No es factura electrónica");
        assertThat(text).doesNotContain("ANULADO");
    }

    @Test
    void aVoidedReceiptShowsAnuladoAndTheReason() throws Exception {
        String text = text(generator.generate(snapshot("0"), null,
                new VoidMark(issued.plusHours(2), "Error de digitación"), false, PaperSize.A5));
        assertThat(text).contains("ANULADO", "Error de digitación", "No. EF-000001");
        assertThat(text).doesNotContain("Descuento:");
    }

    @Test
    void previewIsMarkedAndAnEmptyHeaderStillWorks() throws Exception {
        BusinessSettings empty = new BusinessSettings();
        ReceiptSnapshot s = new ReceiptSnapshot("EF-000001", issued, ReceiptSnapshot.Header.of(empty),
                ReceiptFixtures.content("0"));
        String text = text(generator.generate(s, null, null, true, PaperSize.A5));
        assertThat(text).contains("VISTA PREVIA", "COMPROBANTE INTERNO DE PAGO");
        assertThat(text).doesNotContain("NIT");
    }

    @Test
    void includesTheLogoAndIgnoresADamagedOne() throws Exception {
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(40, 20, BufferedImage.TYPE_INT_RGB), "png", png);
        byte[] withLogo = generator.generate(snapshot("0"), png.toByteArray(), null, false, PaperSize.A5);
        byte[] withoutLogo = generator.generate(snapshot("0"), null, null, false, PaperSize.A5);
        assertThat(withLogo.length).isGreaterThan(withoutLogo.length);

        byte[] damaged = generator.generate(snapshot("0"), new byte[] {(byte) 0x89, 'P', 'N', 'G', 0, 0},
                null, false, PaperSize.A5);
        assertThat(text(damaged)).contains("No. EF-000001");
    }

    @Test
    void moneyIsFormattedInColombianPesos() {
        assertThat(ReceiptPdfGenerator.money(new java.math.BigDecimal("90000.00"))).isEqualTo("$ 90.000");
        assertThat(ReceiptPdfGenerator.money(new java.math.BigDecimal("1234567.5"))).isEqualTo("$ 1.234.567,5");
        assertThat(ReceiptPdfGenerator.money(java.math.BigDecimal.ZERO)).isEqualTo("$ 0");
    }

    @Test
    void defaultIsA5PortraitAndLetterIsStillAvailable() throws Exception {
        assertThat(new ReceiptProperties(null).paperSize()).isEqualTo(PaperSize.A5);

        PdfReader a5 = new PdfReader(generator.generate(snapshot("0"), null, null, false, PaperSize.A5));
        assertThat(a5.getNumberOfPages()).isEqualTo(1);
        assertThat(a5.getPageSize(1).getWidth()).isEqualTo(com.lowagie.text.PageSize.A5.getWidth());
        assertThat(a5.getPageSize(1).getHeight()).isEqualTo(com.lowagie.text.PageSize.A5.getHeight());
        assertThat(a5.getPageSize(1).getHeight()).isGreaterThan(a5.getPageSize(1).getWidth()); // vertical
        a5.close();

        PdfReader letter = new PdfReader(generator.generate(snapshot("0"), null, null, false, PaperSize.LETTER));
        assertThat(letter.getPageSize(1).getWidth()).isEqualTo(com.lowagie.text.PageSize.LETTER.getWidth());
        letter.close();
    }

    @Test
    void aVoidedReceiptWithDiscountAndLogoStillFitsOnOneA5Page() throws Exception {
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(400, 200, BufferedImage.TYPE_INT_RGB), "png", png);
        PdfReader reader = new PdfReader(generator.generate(snapshot("10000"), png.toByteArray(),
                new VoidMark(issued, "Motivo de anulación de prueba bastante largo para ocupar varias líneas"),
                false, PaperSize.A5));
        assertThat(reader.getNumberOfPages()).isEqualTo(1);
        reader.close();
    }
}
