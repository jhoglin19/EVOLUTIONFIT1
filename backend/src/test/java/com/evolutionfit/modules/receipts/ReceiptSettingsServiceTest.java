package com.evolutionfit.modules.receipts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.awt.image.BufferedImage;
import java.time.Clock;
import java.util.Map;
import java.util.Optional;

import javax.imageio.ImageIO;

import com.evolutionfit.common.exception.BusinessException;
import com.evolutionfit.modules.audit.AuditService;
import com.evolutionfit.modules.receipts.dto.ReceiptSettingsRequest;
import com.evolutionfit.modules.receipts.dto.ReceiptSettingsResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReceiptSettingsServiceTest {

    private final BusinessSettingsRepository settingsRepository = mock(BusinessSettingsRepository.class);
    private final ReceiptSequenceRepository sequences = mock(ReceiptSequenceRepository.class);
    private final PaymentReceiptRepository receipts = mock(PaymentReceiptRepository.class);
    private final AuditService audit = mock(AuditService.class);
    private ReceiptSettingsService service;
    private ReceiptSequence sequence;
    private BusinessSettings settings;

    @BeforeEach
    void setUp() {
        service = new ReceiptSettingsService(settingsRepository, sequences, receipts, mock(ReceiptService.class),
                audit, Clock.systemDefaultZone());
        settings = new BusinessSettings();
        settings.setId(BusinessSettings.SINGLE_ID);
        settings.setReceiptPrefix("EF-");
        sequence = new ReceiptSequence();
        sequence.setId((short) 1);
        sequence.setNextValue(1);
        when(settingsRepository.findById(BusinessSettings.SINGLE_ID)).thenReturn(Optional.of(settings));
        when(settingsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(sequences.findForUpdate()).thenReturn(Optional.of(sequence));
        when(sequences.findById((short) 1)).thenReturn(Optional.of(sequence));
    }

    private static ReceiptSettingsRequest request(String prefix, Long initial) {
        return new ReceiptSettingsRequest(" Gimnasio Ficticio ", "Empresa Ficticia S.A.S.", "000.000.000-0",
                "00000-00", "Calle Ficticia 1", "Ciudad Ficticia", "000", "contacto@ejemplo.invalid", prefix, initial);
    }

    @Test
    void defaultsAreEmptyWithPrefixEfAndNumberOne() {
        ReceiptSettingsResponse r = service.get();
        assertThat(r.tradeName()).isNull();
        assertThat(r.nextReceiptNumber()).isEqualTo("EF-000001");
        assertThat(r.initialNumberLocked()).isFalse();
        assertThat(r.maxLogoSizeBytes()).isEqualTo(512000);
    }

    @Test
    void headerFieldsAreSavedTrimmedAndAudited() {
        ReceiptSettingsResponse r = service.update(request("EF-", null), 7L);
        assertThat(r.tradeName()).isEqualTo("Gimnasio Ficticio");
        assertThat(r.legalName()).isEqualTo("Empresa Ficticia S.A.S.");
        assertThat(r.taxId()).isEqualTo("000.000.000-0");
        assertThat(r.commercialRegistration()).isEqualTo("00000-00");
        assertThat(r.city()).isEqualTo("Ciudad Ficticia");
        verify(audit).record(eq("BUSINESS_SETTINGS"), eq(1L), eq(AuditService.ACTION_UPDATE), any(), any(), eq(7L));
    }

    @Test
    void initialNumberCanBeSetBeforeTheFirstReceipt() {
        when(receipts.count()).thenReturn(0L);
        ReceiptSettingsResponse r = service.update(request("EF-", 1500L), 7L);
        assertThat(sequence.getNextValue()).isEqualTo(1500);
        assertThat(r.nextReceiptNumber()).isEqualTo("EF-001500");
    }

    @Test
    void initialNumberIsLockedAfterTheFirstReceipt() {
        when(receipts.count()).thenReturn(1L);
        sequence.setNextValue(2);
        assertThatThrownBy(() -> service.update(request("EF-", 1500L), 7L))
                .isInstanceOf(BusinessException.class).hasMessageContaining("ya se emitieron");
        assertThat(service.get().initialNumberLocked()).isTrue();
        // Enviar el número actual no es un cambio.
        service.update(request("RC-", 2L), 7L);
        assertThat(settings.getReceiptPrefix()).isEqualTo("RC-");
        assertThat(sequence.getNextValue()).isEqualTo(2);
    }

    @Test
    void logoOver500KbIsRejectedWithTheMaximumInTheMessage() {
        byte[] big = new byte[500 * 1024 + 1];
        big[0] = (byte) 0x89;
        assertThatThrownBy(() -> service.uploadLogo(big, 7L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("tamaño máximo permitido de 500 KB");
    }

    @Test
    void logoMustBePngOrJpg() {
        assertThatThrownBy(() -> service.uploadLogo("GIF89a....".getBytes(), 7L))
                .isInstanceOf(BusinessException.class).hasMessageContaining("PNG o JPG");
        assertThatThrownBy(() -> service.uploadLogo(new byte[0], 7L)).isInstanceOf(BusinessException.class);
    }

    @Test
    void aValidPngIsStoredAndTheAuditDoesNotContainTheImage() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB), "png", out);

        ReceiptSettingsResponse r = service.uploadLogo(out.toByteArray(), 7L);

        assertThat(r.hasLogo()).isTrue();
        assertThat(settings.getLogoContentType()).isEqualTo("image/png");
        verify(audit).record(eq("BUSINESS_SETTINGS"), eq(1L), eq(AuditService.ACTION_UPDATE), any(),
                argThat(after -> after instanceof Map<?, ?> m && m.get("logoBytes").equals(out.size())
                        && !m.containsKey("logo")), eq(7L));
    }

    @Test
    void businessInfoExposesOnlyTheTradeName() {
        assertThat(service.businessInfo().tradeName()).isNull();
        settings.setTradeName("Gimnasio Ficticio");
        settings.setTaxId("000.000.000-0");
        assertThat(service.businessInfo()).isEqualTo(new com.evolutionfit.modules.receipts.dto.BusinessInfoResponse("Gimnasio Ficticio"));
    }
}
