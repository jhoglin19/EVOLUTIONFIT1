package com.evolutionfit.modules.memberships;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.evolutionfit.modules.clients.Client;
import com.evolutionfit.modules.plans.Plan;

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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "memberships")
@Getter
@Setter
@NoArgsConstructor
public class Membership {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    /** Nombre del plan al venderse: un cambio posterior del plan no altera esta membresía. */
    @Column(name = "plan_name", nullable = false, length = 80)
    private String planName;

    /** Duración del plan al venderse; se usa para recalcular fechas sin depender del plan actual. */
    @Column(name = "plan_duration_days", nullable = false)
    private int planDurationDays;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    /** Inicio futuro elegido al vender (opcional). Se respeta al pagar si sigue siendo futuro. */
    @Column(name = "requested_start_date")
    private LocalDate requestedStartDate;

    /** Momento en que el pago total la activó. Null en migradas y pendientes. */
    @Column(name = "activated_at")
    private LocalDateTime activatedAt;

    /** Vencimiento efectivo = vencimiento base + días congelados aplicados. */
    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    /** Vencimiento sin congelaciones. */
    @Column(name = "base_end_date", nullable = false)
    private LocalDate baseEndDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MembershipStatus status;

    @Column(name = "price_at_purchase", nullable = false, precision = 12, scale = 2)
    private BigDecimal priceAtPurchase;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @Column(name = "is_migrated", nullable = false)
    private boolean migrated;

    @Column(name = "migration_note")
    private String migrationNote;

    @Column(name = "freeze_used_in_previous_system", nullable = false)
    private boolean freezeUsedInPreviousSystem;

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

    @OneToMany(mappedBy = "membership")
    @OrderBy("startDate ASC, id ASC")
    @BatchSize(size = 50)
    private List<MembershipFreeze> freezes = new ArrayList<>();
}
