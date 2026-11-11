package com.evolutionfit.modules.reports.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Clientes nuevos y migrados del periodo. Un cliente es nuevo si su primera membresía, de cualquier tipo,
 * cae en el periodo. Quien tiene una membresía migrada cuenta como migrado, y sus pagos posteriores son
 * renovaciones.
 */
public record NewClientsReportResponse(LocalDate from, LocalDate to, long newClients, long migratedClients,
                                       List<Row> rows) {

    public enum Type {
        NEW,
        MIGRATED
    }

    /** at: cuándo se pagó la primera membresía (nuevo) o cuándo se registró la migrada (migrado). */
    public record Row(Type type, Long clientId, String clientName, String clientDocument, String clientPhone,
                      boolean clientActive, Long membershipId, String planName, LocalDateTime at, BigDecimal paidAmount) {
    }
}
