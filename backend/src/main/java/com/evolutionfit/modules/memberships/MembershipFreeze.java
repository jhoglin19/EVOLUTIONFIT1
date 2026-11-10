package com.evolutionfit.modules.memberships;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "membership_freezes")
@Getter
@Setter
@NoArgsConstructor
public class MembershipFreeze {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "membership_id", nullable = false)
    private Membership membership;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    /** Último día congelado según lo registrado (o ampliado). */
    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    /** Días que se suman al vencimiento: todos si no se canceló; los transcurridos si se canceló. */
    @Column(name = "applied_days", nullable = false)
    private int appliedDays;

    @Column(name = "reason", nullable = false)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private FreezeStatus status;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "cancelled_by")
    private Long cancelledBy;

    @Column(name = "cancellation_reason")
    private String cancellationReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public boolean isCancelled() {
        return status == FreezeStatus.CANCELLED;
    }

    /** Días calendario seguidos registrados, del inicio al fin incluidos. */
    public int totalDays() {
        return (int) ChronoUnit.DAYS.between(startDate, endDate) + 1;
    }

    /**
     * Regla "1 congelación por membresía": una congelación cancelada antes de empezar no cuenta.
     * Si se canceló el mismo día de inicio o después, sí cuenta.
     */
    public boolean countsTowardLimit() {
        return !isCancelled() || !cancelledAt.toLocalDate().isBefore(startDate);
    }
}
