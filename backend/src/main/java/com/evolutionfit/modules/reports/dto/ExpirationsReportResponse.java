package com.evolutionfit.modules.reports.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Membresías (pagadas o migradas, no anuladas) que vencen o vencieron en el periodo, con su renovación.
 * Incluye clientes inactivos. renewalRate: porcentaje de las ya vencidas que renovaron (null sin vencidas).
 */
public record ExpirationsReportResponse(LocalDate from, LocalDate to, long total, long expired, long renewed,
                                        long pendingPayment, long notRenewed, BigDecimal renewalRate, List<Row> rows) {

    /** EXPIRED: ya venció. IN_FORCE: vence hoy o después. */
    public enum State {
        IN_FORCE,
        EXPIRED
    }

    /** RENEWED: tiene otra que empieza después. PENDING_PAYMENT: la renovación está registrada sin pagar. */
    public enum Renewal {
        RENEWED,
        PENDING_PAYMENT,
        NOT_RENEWED
    }

    public record Row(Long membershipId, Long clientId, String clientName, String clientDocument, String clientPhone,
                      boolean clientActive, String planName, LocalDate startDate, LocalDate endDate, State state,
                      Renewal renewal, LocalDateTime renewedAt, Long pendingRenewalId) {
    }
}
