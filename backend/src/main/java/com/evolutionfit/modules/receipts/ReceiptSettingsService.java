package com.evolutionfit.modules.receipts;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import com.evolutionfit.common.exception.BusinessException;
import com.evolutionfit.modules.audit.AuditService;
import com.evolutionfit.modules.receipts.dto.BusinessInfoResponse;
import com.evolutionfit.modules.receipts.dto.ReceiptSettingsRequest;
import com.evolutionfit.modules.receipts.dto.ReceiptSettingsResponse;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Datos del encabezado, prefijo, número inicial y logo del comprobante (solo ADMIN, lo exige el controlador). */
@Service
public class ReceiptSettingsService {

    public static final int MAX_LOGO_BYTES = 500 * 1024;
    static final String LOGO_TOO_LARGE = "El logo supera el tamaño máximo permitido de 500 KB";
    private static final String AUDIT_ENTITY = "BUSINESS_SETTINGS";
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G'};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};

    private final BusinessSettingsRepository settingsRepository;
    private final ReceiptSequenceRepository sequenceRepository;
    private final PaymentReceiptRepository receiptRepository;
    private final ReceiptService receiptService;
    private final AuditService auditService;
    private final Clock clock;

    public ReceiptSettingsService(BusinessSettingsRepository settingsRepository,
                                  ReceiptSequenceRepository sequenceRepository,
                                  PaymentReceiptRepository receiptRepository, ReceiptService receiptService,
                                  AuditService auditService, Clock clock) {
        this.settingsRepository = settingsRepository;
        this.sequenceRepository = sequenceRepository;
        this.receiptRepository = receiptRepository;
        this.receiptService = receiptService;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ReceiptSettingsResponse get() {
        BusinessSettings s = settings();
        long next = sequenceRepository.findById(BusinessSettings.SINGLE_ID)
                .map(ReceiptSequence::getNextValue).orElse(1L);
        return toResponse(s, next);
    }

    /** Solo el nombre comercial (lo usa recepción en el mensaje de WhatsApp). */
    @Transactional(readOnly = true)
    public BusinessInfoResponse businessInfo() {
        return new BusinessInfoResponse(settings().getTradeName());
    }

    @Transactional
    public ReceiptSettingsResponse update(ReceiptSettingsRequest request, Long currentUserId) {
        BusinessSettings s = settings();
        Map<String, Object> before = snapshot(s);

        s.setTradeName(clean(request.tradeName()));
        s.setLegalName(clean(request.legalName()));
        s.setTaxId(clean(request.taxId()));
        s.setCommercialRegistration(clean(request.commercialRegistration()));
        s.setAddress(clean(request.address()));
        s.setCity(clean(request.city()));
        s.setPhone(clean(request.phone()));
        s.setEmail(clean(request.email()));
        // Cambiar el prefijo no reinicia la numeración: el número sigue desde donde iba.
        s.setReceiptPrefix(request.receiptPrefix() == null ? "" : request.receiptPrefix().trim());
        s.setUpdatedBy(currentUserId);

        ReceiptSequence sequence = sequenceRepository.findForUpdate()
                .orElseThrow(() -> new IllegalStateException("Falta la fila del consecutivo de comprobantes (V4)"));
        before.put("nextNumber", sequence.getNextValue());
        Long initial = request.initialNumber();
        if (initial != null && initial != sequence.getNextValue()) {
            if (receiptRepository.count() > 0) {
                throw new BusinessException("El número inicial ya no se puede cambiar: ya se emitieron comprobantes");
            }
            sequence.setNextValue(initial);
            sequenceRepository.save(sequence);
        }
        BusinessSettings saved = settingsRepository.save(s);

        Map<String, Object> after = snapshot(saved);
        after.put("nextNumber", sequence.getNextValue());
        auditService.record(AUDIT_ENTITY, (long) BusinessSettings.SINGLE_ID, AuditService.ACTION_UPDATE,
                before, after, currentUserId);
        return toResponse(saved, sequence.getNextValue());
    }

    /** PNG o JPG de hasta 500 KB. */
    @Transactional
    public ReceiptSettingsResponse uploadLogo(byte[] content, Long currentUserId) {
        if (content == null || content.length == 0) {
            throw new BusinessException("Selecciona una imagen para el logo");
        }
        if (content.length > MAX_LOGO_BYTES) {
            throw new BusinessException(LOGO_TOO_LARGE + " (el archivo pesa " + (content.length / 1024) + " KB)");
        }
        String type = startsWith(content, PNG) ? "image/png" : startsWith(content, JPEG) ? "image/jpeg" : null;
        if (type == null) {
            throw new BusinessException("El logo debe ser una imagen PNG o JPG");
        }
        BusinessSettings s = settings();
        Map<String, Object> before = snapshot(s);
        s.setLogo(content);
        s.setLogoContentType(type);
        s.setUpdatedBy(currentUserId);
        BusinessSettings saved = settingsRepository.save(s);
        auditService.record(AUDIT_ENTITY, (long) BusinessSettings.SINGLE_ID, AuditService.ACTION_UPDATE,
                before, snapshot(saved), currentUserId);
        return get();
    }

    @Transactional
    public ReceiptSettingsResponse deleteLogo(Long currentUserId) {
        BusinessSettings s = settings();
        if (s.getLogo() != null) {
            Map<String, Object> before = snapshot(s);
            s.setLogo(null);
            s.setLogoContentType(null);
            s.setUpdatedBy(currentUserId);
            settingsRepository.save(s);
            auditService.record(AUDIT_ENTITY, (long) BusinessSettings.SINGLE_ID, AuditService.ACTION_UPDATE,
                    before, snapshot(s), currentUserId);
        }
        return get();
    }

    @Transactional(readOnly = true)
    public Logo logo() {
        BusinessSettings s = settings();
        return s.getLogo() == null ? null : new Logo(s.getLogo(), s.getLogoContentType());
    }

    /** Comprobante de muestra con el encabezado actual. Los datos del cliente son ficticios. */
    @Transactional(readOnly = true)
    public byte[] previewPdf() {
        LocalDate today = LocalDate.now(clock);
        ReceiptContent sample = new ReceiptContent("Cliente de ejemplo", "CC", "0000000000",
                "Membresía plan de ejemplo (30 días)", "Plan de ejemplo", today, today.plusDays(29),
                new BigDecimal("100000"), new BigDecimal("10000"), "Descuento de ejemplo", new BigDecimal("90000"),
                "Efectivo", null, "Usuario de ejemplo", null);
        return receiptService.previewPdf(sample);
    }

    public record Logo(byte[] content, String contentType) {
    }

    // ---- helpers ----

    private BusinessSettings settings() {
        return settingsRepository.findById(BusinessSettings.SINGLE_ID)
                .orElseThrow(() -> new IllegalStateException("Falta la fila de configuración del comprobante (V4)"));
    }

    private ReceiptSettingsResponse toResponse(BusinessSettings s, long next) {
        return new ReceiptSettingsResponse(s.getTradeName(), s.getLegalName(), s.getTaxId(),
                s.getCommercialRegistration(), s.getAddress(), s.getCity(), s.getPhone(), s.getEmail(),
                s.getReceiptPrefix(), next, ReceiptService.formatNumber(s.getReceiptPrefix(), next),
                receiptRepository.count() > 0, s.getLogo() != null, s.getLogo() == null ? 0 : s.getLogo().length, MAX_LOGO_BYTES,
                s.getUpdatedAt());
    }

    /** Para audit_logs: sin el binario del logo, solo si hay logo y su tamaño. */
    private static Map<String, Object> snapshot(BusinessSettings s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tradeName", s.getTradeName());
        m.put("legalName", s.getLegalName());
        m.put("taxId", s.getTaxId());
        m.put("commercialRegistration", s.getCommercialRegistration());
        m.put("address", s.getAddress());
        m.put("city", s.getCity());
        m.put("phone", s.getPhone());
        m.put("email", s.getEmail());
        m.put("receiptPrefix", s.getReceiptPrefix());
        m.put("logoBytes", s.getLogo() == null ? 0 : s.getLogo().length);
        return m;
    }

    private static boolean startsWith(byte[] content, byte[] magic) {
        return content.length >= magic.length && Arrays.equals(Arrays.copyOf(content, magic.length), magic);
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
