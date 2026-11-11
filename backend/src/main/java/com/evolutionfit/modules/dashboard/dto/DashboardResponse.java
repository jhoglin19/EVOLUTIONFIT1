package com.evolutionfit.modules.dashboard.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.evolutionfit.modules.memberships.dto.ExpirationAlertResponse;
import com.evolutionfit.modules.payments.PaymentMethod;
import com.evolutionfit.modules.payments.PaymentStatus;
import com.evolutionfit.modules.reports.dto.EnrollmentCounts;

/**
 * Panel de inicio para recepción y ADMIN: solo datos operativos del día calendario, sin sumas de ingresos
 * (docs/REGLAS_NEGOCIO.md, sección 11). Los ingresos van en {@link AdminDashboardResponse}.
 *
 * @param payments pagos registrados hoy (también los que ya se anularon, marcados), con el valor de cada uno
 */
public record DashboardResponse(LocalDate today, int warningDays, int recentlyExpiredDays,
                                long activeMemberships, long frozenMemberships,
                                long expiringCount, List<ExpirationAlertResponse> expiring,
                                long recentlyExpiredCount, List<ExpirationAlertResponse> recentlyExpired,
                                EnrollmentCounts enrollments,
                                long paymentsCount, long voidedPaymentsCount, List<TodayPayment> payments,
                                CashCard cash) {

    public record TodayPayment(Long id, LocalDateTime paidAt, Long clientId, String clientName, String planName,
                               PaymentMethod method, BigDecimal amount, PaymentStatus status, String receiptNumber) {
    }

    /**
     * Estado de la caja con su propio día (corte de las 10 p.m.), sin montos. pendingReviews solo para el ADMIN.
     */
    public record CashCard(boolean started, LocalDate startDate, LocalDate cashDate, LocalDateTime cutoffAt,
                           boolean openDay, String closedReason, List<PendingCount> pendingCounts,
                           Long pendingReviews) {
    }

    public record PendingCount(Long closingId, LocalDate cashDate) {
    }
}
