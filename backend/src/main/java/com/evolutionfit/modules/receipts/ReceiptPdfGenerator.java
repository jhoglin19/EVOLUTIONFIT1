package com.evolutionfit.modules.receipts;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfGState;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import org.springframework.stereotype.Component;

/**
 * Arma el PDF del comprobante interno a partir de la copia guardada al emitir. No consulta la base.
 * El tamaño de papel es un parámetro para poder agregar la tirilla de 80 mm más adelante.
 */
@Component
public class ReceiptPdfGenerator {

    private static final Locale ES_CO = Locale.forLanguageTag("es-CO");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy hh:mm a", ES_CO);
    private static final Color MUTED = new Color(90, 90, 90);
    private static final Color LINE = new Color(200, 200, 200);

    /** Anulación del pago, para marcar el comprobante. */
    public record VoidMark(LocalDateTime voidedAt, String reason) {
    }

    /**
     * @param logo    imagen PNG/JPG actual del gimnasio, o null
     * @param voided  datos de anulación, o null si el pago está vigente
     * @param preview marca "VISTA PREVIA" (configuración)
     */
    public byte[] generate(ReceiptSnapshot receipt, byte[] logo, VoidMark voided, boolean preview, PaperSize paper) {
        Rectangle size = switch (paper) {
            case LETTER -> PageSize.LETTER;
        };
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(size, 54, 54, 54, 54);
        PdfWriter writer = PdfWriter.getInstance(doc, out);
        doc.addTitle("Comprobante " + receipt.receiptNumber());
        doc.addCreator("EvolutionFit");
        doc.open();

        doc.add(header(receipt.business(), logo));
        doc.add(title(receipt));
        doc.add(clientTable(receipt.content()));
        doc.add(detailTable(receipt.content()));
        doc.add(paymentTable(receipt.content()));
        if (voided != null) {
            Paragraph p = new Paragraph("ANULADO el " + voided.voidedAt().format(DATE_TIME) + " · Motivo: "
                    + voided.reason(), font(10, Font.BOLD, new Color(180, 0, 0)));
            p.setSpacingBefore(12);
            doc.add(p);
        }
        Paragraph footer = new Paragraph("Documento interno de soporte de pago. No es factura electrónica de venta.",
                font(8, Font.ITALIC, MUTED));
        footer.setSpacingBefore(24);
        footer.setAlignment(Element.ALIGN_CENTER);
        doc.add(footer);

        if (voided != null) {
            watermark(writer, size, "ANULADO", new Color(200, 0, 0));
        } else if (preview) {
            watermark(writer, size, "VISTA PREVIA", new Color(120, 120, 120));
        }
        doc.close();
        return out.toByteArray();
    }

    // ---- secciones ----

    private PdfPTable header(ReceiptSnapshot.Header b, byte[] logo) {
        Image image = null;
        if (logo != null) {
            try {
                image = Image.getInstance(logo);
                image.scaleToFit(120, 70);
            } catch (Exception e) {
                image = null; // un logo dañado no impide emitir el comprobante
            }
        }
        PdfPTable table = new PdfPTable(image != null ? new float[] {1.2f, 3f} : new float[] {1f});
        table.setWidthPercentage(100);
        if (image != null) {
            PdfPCell cell = new PdfPCell(image, false);
            cell.setBorder(Rectangle.NO_BORDER);
            cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            table.addCell(cell);
        }
        PdfPCell info = new PdfPCell();
        info.setBorder(Rectangle.NO_BORDER);
        if (b.tradeName() != null) {
            info.addElement(new Paragraph(b.tradeName(), font(14, Font.BOLD, Color.BLACK)));
        }
        List<String> lines = new ArrayList<>();
        addIf(lines, b.legalName(), "");
        addIf(lines, b.taxId(), "NIT ");
        addIf(lines, b.commercialRegistration(), "Matrícula mercantil ");
        lines.add(join(" · ", b.address(), b.city()));
        lines.add(join(" · ", b.phone(), b.email()));
        for (String line : lines) {
            if (!line.isBlank()) {
                info.addElement(new Paragraph(line, font(9, Font.NORMAL, MUTED)));
            }
        }
        table.addCell(info);
        table.setSpacingAfter(16);
        return table;
    }

    private PdfPTable title(ReceiptSnapshot r) {
        PdfPTable t = new PdfPTable(new float[] {3f, 2f});
        t.setWidthPercentage(100);
        PdfPCell left = new PdfPCell(new Phrase("COMPROBANTE INTERNO DE PAGO", font(13, Font.BOLD, Color.BLACK)));
        PdfPCell right = new PdfPCell();
        right.addElement(rightAligned("No. " + r.receiptNumber(), font(13, Font.BOLD, Color.BLACK)));
        right.addElement(rightAligned("Fecha de emisión: " + r.issuedAt().format(DATE_TIME), font(9, Font.NORMAL, MUTED)));
        for (PdfPCell c : List.of(left, right)) {
            c.setBorder(Rectangle.BOTTOM);
            c.setBorderColor(LINE);
            c.setPaddingBottom(8);
            c.setVerticalAlignment(Element.ALIGN_MIDDLE);
            t.addCell(c);
        }
        t.setSpacingAfter(12);
        return t;
    }

    private PdfPTable clientTable(ReceiptContent c) {
        PdfPTable t = twoColumns();
        row(t, "Cliente", c.clientName());
        row(t, "Documento", join(" ", c.clientDocumentType(), c.clientDocument()));
        t.setSpacingAfter(12);
        return t;
    }

    private PdfPTable detailTable(ReceiptContent c) {
        PdfPTable t = new PdfPTable(new float[] {4f, 1.5f});
        t.setWidthPercentage(100);
        headCell(t, "Concepto", Element.ALIGN_LEFT);
        headCell(t, "Valor", Element.ALIGN_RIGHT);
        bodyCell(t, c.concept(), Element.ALIGN_LEFT, Font.NORMAL);
        bodyCell(t, money(c.listPrice()), Element.ALIGN_RIGHT, Font.NORMAL);
        if (c.discountAmount() != null && c.discountAmount().signum() > 0) {
            bodyCell(t, "Descuento: " + c.discountReason(), Element.ALIGN_LEFT, Font.NORMAL);
            bodyCell(t, "- " + money(c.discountAmount()), Element.ALIGN_RIGHT, Font.NORMAL);
        }
        bodyCell(t, "Total pagado", Element.ALIGN_LEFT, Font.BOLD);
        bodyCell(t, money(c.amount()), Element.ALIGN_RIGHT, Font.BOLD);
        t.setSpacingAfter(12);
        return t;
    }

    private PdfPTable paymentTable(ReceiptContent c) {
        PdfPTable t = twoColumns();
        row(t, "Método de pago", c.paymentMethod());
        if (c.reference() != null) {
            row(t, "Referencia", c.reference());
        }
        row(t, "Vigencia", c.membershipStart().format(DATE) + " a " + c.membershipEnd().format(DATE));
        if (c.receivedBy() != null) {
            row(t, "Registrado por", c.receivedBy());
        }
        if (c.notes() != null) {
            row(t, "Notas", c.notes());
        }
        return t;
    }

    // ---- utilidades ----

    private static void watermark(PdfWriter writer, Rectangle size, String text, Color color) {
        PdfContentByte over = writer.getDirectContent();
        over.saveState();
        PdfGState state = new PdfGState();
        state.setFillOpacity(0.18f);
        over.setGState(state);
        over.beginText();
        over.setColorFill(color);
        over.setFontAndSize(helveticaBold(), 80);
        over.showTextAligned(Element.ALIGN_CENTER, text, size.getWidth() / 2, size.getHeight() / 2, 35);
        over.endText();
        over.restoreState();
    }

    private static BaseFont helveticaBold() {
        try {
            return BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo cargar la fuente del comprobante", e);
        }
    }

    private static PdfPTable twoColumns() {
        PdfPTable t = new PdfPTable(new float[] {1.5f, 4f});
        t.setWidthPercentage(100);
        return t;
    }

    private static void row(PdfPTable t, String label, String value) {
        PdfPCell l = new PdfPCell(new Phrase(label, font(9, Font.BOLD, MUTED)));
        PdfPCell v = new PdfPCell(new Phrase(value == null ? "" : value, font(10, Font.NORMAL, Color.BLACK)));
        for (PdfPCell c : List.of(l, v)) {
            c.setBorder(Rectangle.NO_BORDER);
            c.setPaddingBottom(4);
            t.addCell(c);
        }
    }

    private static void headCell(PdfPTable t, String text, int align) {
        PdfPCell c = new PdfPCell(new Phrase(text, font(9, Font.BOLD, Color.BLACK)));
        c.setHorizontalAlignment(align);
        c.setBackgroundColor(new Color(240, 240, 240));
        c.setBorderColor(LINE);
        c.setPadding(6);
        t.addCell(c);
    }

    private static void bodyCell(PdfPTable t, String text, int align, int style) {
        PdfPCell c = new PdfPCell(new Phrase(text, font(10, style, Color.BLACK)));
        c.setHorizontalAlignment(align);
        c.setBorderColor(LINE);
        c.setPadding(6);
        t.addCell(c);
    }

    private static Paragraph rightAligned(String text, Font font) {
        Paragraph p = new Paragraph(text, font);
        p.setAlignment(Element.ALIGN_RIGHT);
        return p;
    }

    private static Font font(float size, int style, Color color) {
        return FontFactory.getFont(FontFactory.HELVETICA, BaseFont.WINANSI, false, size, style, color);
    }

    private static void addIf(List<String> lines, String value, String prefix) {
        if (value != null && !value.isBlank()) {
            lines.add(prefix + value);
        }
    }

    private static String join(String separator, String... parts) {
        List<String> present = new ArrayList<>();
        for (String p : parts) {
            if (p != null && !p.isBlank()) {
                present.add(p);
            }
        }
        return String.join(separator, present);
    }

    /** Pesos colombianos: "$ 90.000" o "$ 90.000,50". */
    static String money(BigDecimal value) {
        DecimalFormat format = new DecimalFormat("$ #,##0.##", DecimalFormatSymbols.getInstance(ES_CO));
        return format.format(value == null ? BigDecimal.ZERO : value);
    }
}
