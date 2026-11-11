package com.evolutionfit.modules.cash;

import java.math.BigDecimal;
import java.time.LocalDate;
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

/**
 * Cierre diario. Lo crea el sistema a las 10 p.m. (o al ponerse al día) y congela el efectivo esperado;
 * esos valores no cambian nunca. Después solo se agregan el arqueo y la revisión del ADMIN.
 */
@Entity
@Table(name = "cash_closings")
@Getter
@Setter
@NoArgsConstructor
public class CashClosing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cash_date", nullable = false, unique = true, updatable = false)
    private LocalDate cashDate;

    /** Columnas de V1: siempre iguales a cash_date (un cierre por día). */
    @Column(name = "period_start", nullable = false, updatable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false, updatable = false)
    private LocalDate periodEnd;

    @Column(name = "window_start", nullable = false, updatable = false)
    private LocalDateTime windowStart;

    @Column(name = "window_end", nullable = false, updatable = false)
    private LocalDateTime windowEnd;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CashClosingStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "closing_mode", nullable = false, length = 20)
    private ClosingMode closingMode;

    @Column(name = "was_open_day", nullable = false)
    private boolean wasOpenDay;

    @Column(name = "has_cash_activity", nullable = false)
    private boolean hasCashActivity;

    @Column(name = "opening_base", nullable = false, precision = 12, scale = 2)
    private BigDecimal openingBase;

    @Column(name = "cash_income", nullable = false, precision = 12, scale = 2)
    private BigDecimal cashIncome;

    @Column(name = "cash_voided", nullable = false, precision = 12, scale = 2)
    private BigDecimal cashVoided;

    @Column(name = "cash_withdrawals", nullable = false, precision = 12, scale = 2)
    private BigDecimal cashWithdrawals;

    @Column(name = "expected_cash", nullable = false, precision = 12, scale = 2)
    private BigDecimal expectedCash;

    /** Columnas de V1, con sus equivalentes. */
    @Column(name = "total_income", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalIncome;

    @Column(name = "total_expenses", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalExpenses;

    @Column(name = "total_adjustments", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAdjustments;

    @Column(name = "balance", nullable = false, precision = 12, scale = 2)
    private BigDecimal balance;

    @Column(name = "notes")
    private String notes;

    // ---- arqueo ----

    /** Efectivo de días posteriores que ya estaba en la caja al contar (entró después del corte). */
    @Column(name = "post_cut_income", precision = 12, scale = 2)
    private BigDecimal postCutIncome;

    /** Efectivo de días posteriores que ya había salido al contar (retiros y anulaciones después del corte). */
    @Column(name = "post_cut_outflow", precision = 12, scale = 2)
    private BigDecimal postCutOutflow;

    @Column(name = "adjusted_expected_cash", precision = 12, scale = 2)
    private BigDecimal adjustedExpectedCash;

    @Column(name = "counted_cash", precision = 12, scale = 2)
    private BigDecimal countedCash;

    @Column(name = "difference", precision = 12, scale = 2)
    private BigDecimal difference;

    @Column(name = "post_cut_detail", columnDefinition = "json")
    private String postCutDetail;

    @Column(name = "count_note", length = 500)
    private String countNote;

    @Column(name = "counted_by")
    private Long countedBy;

    @Column(name = "counted_at")
    private LocalDateTime countedAt;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "review_note", length = 500)
    private String reviewNote;

    /** NULL: lo cerró el sistema. */
    @Column(name = "closed_by")
    private Long closedBy;

    @Column(name = "closed_at", nullable = false, updatable = false)
    private LocalDateTime closedAt;

    public void clearCount() {
        postCutIncome = null;
        postCutOutflow = null;
        adjustedExpectedCash = null;
        countedCash = null;
        difference = null;
        postCutDetail = null;
        countNote = null;
        countedBy = null;
        countedAt = null;
        reviewedBy = null;
        reviewedAt = null;
        reviewNote = null;
    }
}
