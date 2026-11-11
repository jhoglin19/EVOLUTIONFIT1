package com.evolutionfit.modules.receipts;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.evolutionfit.common.exception.ResourceNotFoundException;
import com.evolutionfit.modules.receipts.ReceiptPdfGenerator.VoidMark;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Comprobantes internos (docs/REGLAS_NEGOCIO.md, sección 6): consecutivo único y continuo, un
 * comprobante por pago, PDF generado a partir de la copia guardada.
 */
@Service
public class ReceiptService {

    /** 6 dígitos con ceros a la izquierda; a partir del 1.000.000 crece sin perder números. */
    static final String NUMBER_FORMAT = "%06d";

    private final ReceiptSequenceRepository sequenceRepository;
    private final PaymentReceiptRepository receiptRepository;
    private final BusinessSettingsRepository settingsRepository;
    private final ReceiptPdfGenerator pdfGenerator;
    private final ReceiptProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public ReceiptService(ReceiptSequenceRepository sequenceRepository, PaymentReceiptRepository receiptRepository,
                          BusinessSettingsRepository settingsRepository, ReceiptPdfGenerator pdfGenerator,
                          ReceiptProperties properties, ObjectMapper objectMapper, Clock clock) {
        this.sequenceRepository = sequenceRepository;
        this.receiptRepository = receiptRepository;
        this.settingsRepository = settingsRepository;
        this.pdfGenerator = pdfGenerator;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * Emite el comprobante dentro de la transacción del pago. El número se toma con bloqueo y se
     * incrementa en la misma transacción: si el pago se revierte, el número no se consume.
     */
    @Transactional
    public PaymentReceipt issue(Long paymentId, ReceiptContent content, Long userId) {
        ReceiptSequence sequence = sequenceRepository.findForUpdate()
                .orElseThrow(() -> new IllegalStateException("Falta la fila del consecutivo de comprobantes (V4)"));
        long number = sequence.getNextValue();
        sequence.setNextValue(number + 1);
        sequenceRepository.save(sequence);

        BusinessSettings settings = settings();
        LocalDateTime now = LocalDateTime.now(clock);
        String receiptNumber = formatNumber(settings.getReceiptPrefix(), number);
        ReceiptSnapshot snapshot = new ReceiptSnapshot(receiptNumber, now, ReceiptSnapshot.Header.of(settings), content);

        PaymentReceipt receipt = new PaymentReceipt();
        receipt.setPaymentId(paymentId);
        receipt.setSequenceNumber(number);
        receipt.setReceiptNumber(receiptNumber);
        receipt.setSnapshot(toJson(snapshot));
        receipt.setIssuedAt(now);
        receipt.setIssuedBy(userId);
        return receiptRepository.save(receipt);
    }

    @Transactional(readOnly = true)
    public Optional<PaymentReceipt> findByPayment(Long paymentId) {
        return receiptRepository.findByPaymentId(paymentId);
    }

    @Transactional(readOnly = true)
    public Map<Long, String> numbersByPayment(Collection<Long> paymentIds) {
        if (paymentIds.isEmpty()) {
            return Map.of();
        }
        return receiptRepository.findByPaymentIdIn(paymentIds).stream()
                .collect(Collectors.toMap(PaymentReceipt::getPaymentId, PaymentReceipt::getReceiptNumber,
                        (a, b) -> a, java.util.HashMap::new));
    }

    /** PDF del comprobante de un pago, con la marca de anulado si corresponde. */
    @Transactional(readOnly = true)
    public ReceiptPdf pdf(Long paymentId, VoidMark voided) {
        PaymentReceipt receipt = receiptRepository.findByPaymentId(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Este pago no tiene comprobante"));
        ReceiptSnapshot snapshot = fromJson(receipt.getSnapshot());
        byte[] bytes = pdfGenerator.generate(snapshot, settings().getLogo(), voided, false, properties.paperSize());
        return new ReceiptPdf(receipt.getReceiptNumber(), bytes);
    }

    /** Vista previa con datos de ejemplo y el encabezado actual. No consume número. */
    @Transactional(readOnly = true)
    public byte[] previewPdf(ReceiptContent sample) {
        BusinessSettings settings = settings();
        long next = sequenceRepository.findById(BusinessSettings.SINGLE_ID).map(ReceiptSequence::getNextValue).orElse(1L);
        ReceiptSnapshot snapshot = new ReceiptSnapshot(formatNumber(settings.getReceiptPrefix(), next),
                LocalDateTime.now(clock), ReceiptSnapshot.Header.of(settings), sample);
        return pdfGenerator.generate(snapshot, settings.getLogo(), null, true, properties.paperSize());
    }

    public record ReceiptPdf(String receiptNumber, byte[] content) {
    }

    static String formatNumber(String prefix, long number) {
        return (prefix == null ? "" : prefix) + String.format(NUMBER_FORMAT, number);
    }

    private BusinessSettings settings() {
        return settingsRepository.findById(BusinessSettings.SINGLE_ID)
                .orElseThrow(() -> new IllegalStateException("Falta la fila de configuración del comprobante (V4)"));
    }

    private String toJson(ReceiptSnapshot snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo guardar la copia del comprobante", e);
        }
    }

    ReceiptSnapshot fromJson(String json) {
        try {
            return objectMapper.readValue(json, ReceiptSnapshot.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo leer la copia del comprobante", e);
        }
    }

}
