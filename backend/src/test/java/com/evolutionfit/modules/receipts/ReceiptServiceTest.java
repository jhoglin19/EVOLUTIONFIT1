package com.evolutionfit.modules.receipts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import com.evolutionfit.common.exception.ResourceNotFoundException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReceiptServiceTest {

    private final ReceiptSequenceRepository sequences = mock(ReceiptSequenceRepository.class);
    private final PaymentReceiptRepository receipts = mock(PaymentReceiptRepository.class);
    private final BusinessSettingsRepository settingsRepository = mock(BusinessSettingsRepository.class);
    private final ObjectMapper json = JsonMapper.builder()
            .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-10T15:00:00Z"), ZoneId.of("America/Bogota"));
    private ReceiptService service;
    private ReceiptSequence sequence;
    private BusinessSettings settings;

    @BeforeEach
    void setUp() {
        service = new ReceiptService(sequences, receipts, settingsRepository, new ReceiptPdfGenerator(),
                new ReceiptProperties(null), json, clock);
        sequence = new ReceiptSequence();
        sequence.setId((short) 1);
        sequence.setNextValue(1);
        settings = ReceiptFixtures.settings("EF-");
        when(sequences.findForUpdate()).thenReturn(Optional.of(sequence));
        when(settingsRepository.findById(BusinessSettings.SINGLE_ID)).thenReturn(Optional.of(settings));
        when(receipts.save(any(PaymentReceipt.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void numbersAreConsecutiveWithPrefixAndSixDigits() {
        PaymentReceipt first = service.issue(100L, ReceiptFixtures.content("0"), 8L);
        PaymentReceipt second = service.issue(101L, ReceiptFixtures.content("0"), 8L);

        assertThat(first.getReceiptNumber()).isEqualTo("EF-000001");
        assertThat(first.getSequenceNumber()).isEqualTo(1);
        assertThat(second.getReceiptNumber()).isEqualTo("EF-000002");
        assertThat(sequence.getNextValue()).isEqualTo(3);
        assertThat(first.getIssuedBy()).isEqualTo(8L);
        assertThat(first.getPaymentId()).isEqualTo(100L);
    }

    @Test
    void changingThePrefixKeepsTheNumbering() {
        service.issue(100L, ReceiptFixtures.content("0"), 8L);
        settings.setReceiptPrefix("RC-");
        assertThat(service.issue(101L, ReceiptFixtures.content("0"), 8L).getReceiptNumber()).isEqualTo("RC-000002");

        settings.setReceiptPrefix("");
        sequence.setNextValue(1234567);
        assertThat(service.issue(102L, ReceiptFixtures.content("0"), 8L).getReceiptNumber()).isEqualTo("1234567");
    }

    @Test
    void theSnapshotKeepsWhatWasPrintedEvenIfSettingsChangeLater() {
        PaymentReceipt receipt = service.issue(100L, ReceiptFixtures.content("10000"), 8L);
        settings.setTradeName("Otro nombre");

        ReceiptSnapshot snapshot = service.fromJson(receipt.getSnapshot());
        assertThat(snapshot.business().tradeName()).isEqualTo("Gimnasio Ficticio");
        assertThat(snapshot.business().taxId()).isEqualTo("000.000.000-0");
        assertThat(snapshot.content().amount()).isEqualByComparingTo("80000");
        assertThat(snapshot.receiptNumber()).isEqualTo("EF-000001");
        assertThat(snapshot.issuedAt()).isEqualTo(java.time.LocalDateTime.of(2026, 11, 10, 10, 0));
    }

    @Test
    void aPaymentWithoutReceiptSaysSo() {
        when(receipts.findByPaymentId(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.pdf(1L, null))
                .isInstanceOf(ResourceNotFoundException.class).hasMessageContaining("no tiene comprobante");
    }
}
