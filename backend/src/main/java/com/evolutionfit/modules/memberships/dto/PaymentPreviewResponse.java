package com.evolutionfit.modules.memberships.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Lo que pasará si se paga hoy una membresía pendiente.
 *
 * @param requestedStartPassed la fecha elegida al vender ya pasó: empezará el día del pago (aviso en pantalla)
 */
public record PaymentPreviewResponse(Long membershipId,
                                     String planName,
                                     BigDecimal listPrice,
                                     LocalDate startDate,
                                     LocalDate endDate,
                                     boolean scheduled,
                                     LocalDate requestedStartDate,
                                     boolean requestedStartPassed,
                                     LocalDate currentEndDate) {
}
