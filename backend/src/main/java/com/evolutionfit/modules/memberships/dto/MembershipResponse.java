package com.evolutionfit.modules.memberships.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.evolutionfit.modules.memberships.Membership;
import com.evolutionfit.modules.memberships.MembershipRules;
import com.evolutionfit.modules.memberships.MembershipStatus;

/** {@code status} siempre es el calculado para hoy, no la copia guardada en la columna. */
public record MembershipResponse(Long id,
                                 Long clientId,
                                 String clientName,
                                 String clientDocument,
                                 Long planId,
                                 String planName,
                                 int planDurationDays,
                                 LocalDate startDate,
                                 LocalDate endDate,
                                 LocalDate baseEndDate,
                                 MembershipStatus status,
                                 BigDecimal priceAtPurchase,
                                 String notes,
                                 boolean migrated,
                                 String migrationNote,
                                 boolean freezeUsedInPreviousSystem,
                                 boolean canFreeze,
                                 List<FreezeResponse> freezes,
                                 LocalDateTime cancelledAt,
                                 String cancellationReason,
                                 LocalDateTime createdAt) {

    public static MembershipResponse from(Membership m, LocalDate today) {
        return new MembershipResponse(
                m.getId(),
                m.getClient().getId(),
                m.getClient().getFirstName() + " " + m.getClient().getLastName(),
                m.getClient().getDocumentNumber(),
                m.getPlan().getId(),
                m.getPlanName(),
                m.getPlanDurationDays(),
                m.getStartDate(),
                m.getEndDate(),
                m.getBaseEndDate(),
                MembershipRules.resolve(m, today),
                m.getPriceAtPurchase(),
                m.getNotes(),
                m.isMigrated(),
                m.getMigrationNote(),
                m.isFreezeUsedInPreviousSystem(),
                MembershipRules.canFreeze(m, today),
                m.getFreezes().stream().map(f -> FreezeResponse.from(f, today)).toList(),
                m.getCancelledAt(),
                m.getCancellationReason(),
                m.getCreatedAt());
    }
}
