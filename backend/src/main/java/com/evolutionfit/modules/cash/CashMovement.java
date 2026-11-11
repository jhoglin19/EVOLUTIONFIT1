package com.evolutionfit.modules.cash;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.hibernate.annotations.CreationTimestamp;

/**
 * Retiro de efectivo (movement_type = EXPENSE). Los ingresos no se guardan aquí: se leen de payments.
 * cash_closing_id lo marca solo el cierre (por SQL); aquí es de solo lectura para no pisarlo.
 */
@Entity
@Table(name = "cash_movements")
@Getter
@Setter
@NoArgsConstructor
public class CashMovement {

    public static final String EXPENSE = "EXPENSE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cash_closing_id", insertable = false, updatable = false)
    private Long cashClosingId;

    @Column(name = "user_id", updatable = false)
    private Long userId;

    @Column(name = "movement_type", nullable = false, length = 20, updatable = false)
    private String movementType = EXPENSE;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 20, updatable = false)
    private WithdrawalCategory category;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal amount;

    /** Concepto del retiro. */
    @Column(name = "description", nullable = false, updatable = false)
    private String description;

    @Column(name = "request_id", length = 36, unique = true, updatable = false)
    private String requestId;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private LocalDateTime occurredAt;

    @Column(name = "voided_at")
    private LocalDateTime voidedAt;

    @Column(name = "voided_by")
    private Long voidedBy;

    @Column(name = "void_reason")
    private String voidReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
