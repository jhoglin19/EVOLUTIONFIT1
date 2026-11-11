package com.evolutionfit.modules.memberships.dto;

import java.time.LocalDate;

import com.evolutionfit.modules.memberships.MembershipStatus;

/**
 * Una alerta de vencimiento (por vencer o vencida reciente).
 *
 * @param daysToEnd        días entre hoy y el vencimiento: 0 vence hoy, 3 vence en tres días, −2 venció anteayer
 * @param pendingRenewalId renovación ya registrada pero pendiente de pago (se cobra en vez de crear otra)
 */
public record ExpirationAlertResponse(Long membershipId, Long clientId, String clientName, String clientDocument,
                                      String clientPhone, String planName, LocalDate startDate, LocalDate endDate,
                                      MembershipStatus status, long daysToEnd, Long pendingRenewalId,
                                      String pendingRenewalPlanName) {
}
