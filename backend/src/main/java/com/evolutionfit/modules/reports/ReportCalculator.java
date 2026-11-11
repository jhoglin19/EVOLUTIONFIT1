package com.evolutionfit.modules.reports;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.evolutionfit.modules.cash.CashSummary.MethodTotal;
import com.evolutionfit.modules.payments.PaymentMethod;
import com.evolutionfit.modules.reports.ReportRepository.DayAmount;
import com.evolutionfit.modules.reports.ReportRepository.ExpirationRow;
import com.evolutionfit.modules.reports.dto.ExpirationsReportResponse;
import com.evolutionfit.modules.reports.dto.IncomeReportResponse;

/** Cálculos de los reportes sin base de datos. */
public final class ReportCalculator {

    private ReportCalculator() {
    }

    /** Totales por método, en el orden del enum (efectivo primero), solo los métodos con movimientos. */
    public static List<MethodTotal> methodTotals(List<DayAmount> received, List<DayAmount> voided) {
        Map<PaymentMethod, MethodTotal> map = new EnumMap<>(PaymentMethod.class);
        for (DayAmount r : received) {
            MethodTotal t = map.getOrDefault(r.method(), empty(r.method()));
            map.put(r.method(), new MethodTotal(r.method(), t.receivedCount() + r.count(),
                    t.receivedAmount().add(r.amount()), t.voidedCount(), t.voidedAmount()));
        }
        for (DayAmount v : voided) {
            MethodTotal t = map.getOrDefault(v.method(), empty(v.method()));
            map.put(v.method(), new MethodTotal(v.method(), t.receivedCount(), t.receivedAmount(),
                    t.voidedCount() + v.count(), t.voidedAmount().add(v.amount())));
        }
        return List.copyOf(map.values());
    }

    /**
     * Ingresos por periodo: cada día suma lo recibido ese día y resta lo anulado ese día (aunque el pago sea
     * de otro periodo), igual que Caja. Incluye los periodos sin movimientos.
     */
    public static List<IncomeReportResponse.Period> periods(DateRange range, IncomeGrouping grouping,
                                                            List<DayAmount> received, List<DayAmount> voided) {
        Map<LocalDate, Acc> acc = new LinkedHashMap<>();
        for (DateRange p : ReportPeriods.split(range, grouping)) {
            acc.put(p.from(), new Acc(p));
        }
        for (DayAmount r : received) {
            Acc a = acc.get(ReportPeriods.periodStart(r.day(), range, grouping));
            a.receivedCount += r.count();
            a.received = a.received.add(r.amount());
            if (r.method() == PaymentMethod.CASH) {
                a.cash = a.cash.add(r.amount());
            }
        }
        for (DayAmount v : voided) {
            Acc a = acc.get(ReportPeriods.periodStart(v.day(), range, grouping));
            a.voidedCount += v.count();
            a.voided = a.voided.add(v.amount());
            if (v.method() == PaymentMethod.CASH) {
                a.cash = a.cash.subtract(v.amount());
            }
        }
        List<IncomeReportResponse.Period> out = new ArrayList<>();
        for (Acc a : acc.values()) {
            BigDecimal net = a.received.subtract(a.voided);
            out.add(new IncomeReportResponse.Period(a.period.from(), a.period.to(), a.receivedCount, a.received,
                    a.voidedCount, a.voided, net, a.cash, net.subtract(a.cash)));
        }
        return out;
    }

    public static ExpirationsReportResponse expirations(DateRange range, List<ExpirationRow> rows, LocalDate today) {
        List<ExpirationsReportResponse.Row> out = new ArrayList<>();
        long expired = 0;
        long renewed = 0;
        long renewedExpired = 0;
        long pending = 0;
        for (ExpirationRow r : rows) {
            var state = r.endDate().isBefore(today) ? ExpirationsReportResponse.State.EXPIRED
                    : ExpirationsReportResponse.State.IN_FORCE;
            var renewal = r.renewedAt() != null ? ExpirationsReportResponse.Renewal.RENEWED
                    : r.pendingRenewalId() != null ? ExpirationsReportResponse.Renewal.PENDING_PAYMENT
                    : ExpirationsReportResponse.Renewal.NOT_RENEWED;
            boolean isExpired = state == ExpirationsReportResponse.State.EXPIRED;
            boolean isRenewed = renewal == ExpirationsReportResponse.Renewal.RENEWED;
            expired += isExpired ? 1 : 0;
            renewed += isRenewed ? 1 : 0;
            renewedExpired += isExpired && isRenewed ? 1 : 0;
            pending += renewal == ExpirationsReportResponse.Renewal.PENDING_PAYMENT ? 1 : 0;
            out.add(new ExpirationsReportResponse.Row(r.membershipId(), r.clientId(), r.clientName(), r.clientDocument(),
                    r.clientPhone(), r.clientActive(), r.planName(), r.startDate(), r.endDate(), state, renewal,
                    r.renewedAt(), isRenewed ? null : r.pendingRenewalId()));
        }
        BigDecimal rate = expired == 0 ? null
                : BigDecimal.valueOf(renewedExpired * 100).divide(BigDecimal.valueOf(expired), 1, RoundingMode.HALF_UP);
        return new ExpirationsReportResponse(range.from(), range.to(), rows.size(), expired, renewed, pending,
                rows.size() - renewed - pending, rate, out);
    }

    private static MethodTotal empty(PaymentMethod method) {
        return new MethodTotal(method, 0, BigDecimal.ZERO, 0, BigDecimal.ZERO);
    }

    private static final class Acc {
        private final DateRange period;
        private int receivedCount;
        private int voidedCount;
        private BigDecimal received = BigDecimal.ZERO;
        private BigDecimal voided = BigDecimal.ZERO;
        private BigDecimal cash = BigDecimal.ZERO;

        private Acc(DateRange period) {
            this.period = period;
        }
    }
}
