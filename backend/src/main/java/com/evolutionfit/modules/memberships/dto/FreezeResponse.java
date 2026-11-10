package com.evolutionfit.modules.memberships.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.evolutionfit.modules.memberships.FreezeStatus;
import com.evolutionfit.modules.memberships.MembershipFreeze;
import com.evolutionfit.modules.memberships.MembershipRules;

public record FreezeResponse(Long id,
                             Long membershipId,
                             LocalDate startDate,
                             LocalDate endDate,
                             int days,
                             int appliedDays,
                             FreezeStatus status,
                             String reason,
                             boolean countsTowardLimit,
                             LocalDateTime cancelledAt,
                             String cancellationReason,
                             LocalDateTime createdAt) {

    public static FreezeResponse from(MembershipFreeze f, LocalDate today) {
        return new FreezeResponse(
                f.getId(),
                f.getMembership().getId(),
                f.getStartDate(),
                f.getEndDate(),
                f.totalDays(),
                f.getAppliedDays(),
                MembershipRules.resolve(f, today),
                f.getReason(),
                f.countsTowardLimit(),
                f.getCancelledAt(),
                f.getCancellationReason(),
                f.getCreatedAt());
    }
}
