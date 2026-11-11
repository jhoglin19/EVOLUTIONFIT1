package com.evolutionfit.modules.payments.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.evolutionfit.modules.memberships.MembershipStatus;
import com.evolutionfit.modules.payments.PaymentMethod;
import com.evolutionfit.modules.payments.PaymentStatus;

/** {@code membershipStatus} es el calculado para hoy. {@code receiptNumber} es null en pagos sin comprobante. */
public record PaymentResponse(Long id,
                              Long membershipId,
                              Long clientId,
                              String clientName,
                              String clientDocument,
                              String planName,
                              LocalDate membershipStartDate,
                              LocalDate membershipEndDate,
                              MembershipStatus membershipStatus,
                              BigDecimal listPrice,
                              BigDecimal discountAmount,
                              String discountReason,
                              BigDecimal amount,
                              PaymentMethod method,
                              String reference,
                              String notes,
                              PaymentStatus status,
                              LocalDateTime paidAt,
                              LocalDateTime voidedAt,
                              String voidReason,
                              String receiptNumber) {
}
