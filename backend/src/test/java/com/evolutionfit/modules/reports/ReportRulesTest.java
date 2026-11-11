package com.evolutionfit.modules.reports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.evolutionfit.common.exception.BusinessException;
import com.evolutionfit.modules.payments.PaymentMethod;
import com.evolutionfit.modules.reports.ReportRepository.DayAmount;
import com.evolutionfit.modules.reports.ReportRepository.ExpirationRow;
import com.evolutionfit.modules.reports.dto.ExpirationsReportResponse;
import com.evolutionfit.modules.reports.dto.IncomeReportResponse;

import org.junit.jupiter.api.Test;

/** Rangos, periodos, CSV y totales de los reportes, sin base de datos. */
class ReportRulesTest {

    private static final LocalDate NOV_1 = LocalDate.of(2026, 11, 1);

    // ---- rango ----

    @Test
    void aRangeIncludesBothDaysAndEndsAtMidnightOfTheNextDay() {
        DateRange r = DateRange.of(NOV_1, LocalDate.of(2026, 11, 30), 366);
        assertThat(r.days()).isEqualTo(30);
        assertThat(r.start()).isEqualTo(LocalDateTime.of(2026, 11, 1, 0, 0));
        assertThat(r.endExclusive()).isEqualTo(LocalDateTime.of(2026, 12, 1, 0, 0));
        assertThat(DateRange.day(NOV_1).endExclusive()).isEqualTo(LocalDateTime.of(2026, 11, 2, 0, 0));
    }

    @Test
    void invalidRangesAreRejected() {
        assertThatThrownBy(() -> DateRange.of(null, NOV_1, 366)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> DateRange.of(NOV_1, NOV_1.minusDays(1), 366))
                .isInstanceOf(BusinessException.class).hasMessageContaining("anterior");
        assertThat(DateRange.of(LocalDate.of(2027, 1, 1), LocalDate.of(2027, 12, 31), 366).days()).isEqualTo(365);
        assertThat(DateRange.of(LocalDate.of(2028, 1, 1), LocalDate.of(2028, 12, 31), 366).days()).isEqualTo(366);
        assertThatThrownBy(() -> DateRange.of(NOV_1, NOV_1.plusDays(366), 366))
                .isInstanceOf(BusinessException.class).hasMessageContaining("366");
    }

    // ---- periodos ----

    @Test
    void weeksRunMondayToSundayAndAreClippedToTheRange() {
        // Del miércoles 4 al martes 17 de noviembre de 2026.
        DateRange r = new DateRange(LocalDate.of(2026, 11, 4), LocalDate.of(2026, 11, 17));
        List<DateRange> weeks = ReportPeriods.split(r, IncomeGrouping.WEEK);
        assertThat(weeks).containsExactly(
                new DateRange(LocalDate.of(2026, 11, 4), LocalDate.of(2026, 11, 8)),
                new DateRange(LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 15)),
                new DateRange(LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 17)));
        assertThat(ReportPeriods.periodStart(LocalDate.of(2026, 11, 5), r, IncomeGrouping.WEEK))
                .isEqualTo(LocalDate.of(2026, 11, 4));
        assertThat(ReportPeriods.periodStart(LocalDate.of(2026, 11, 15), r, IncomeGrouping.WEEK))
                .isEqualTo(LocalDate.of(2026, 11, 9));
    }

    @Test
    void monthsAreCalendarMonthsClippedToTheRange() {
        DateRange r = new DateRange(LocalDate.of(2026, 1, 15), LocalDate.of(2026, 3, 10));
        assertThat(ReportPeriods.split(r, IncomeGrouping.MONTH)).containsExactly(
                new DateRange(LocalDate.of(2026, 1, 15), LocalDate.of(2026, 1, 31)),
                new DateRange(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28)),
                new DateRange(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 10)));
        assertThat(ReportPeriods.split(r, IncomeGrouping.DAY)).hasSize(55);
    }

    // ---- ingresos ----

    @Test
    void periodsSubtractVoidsOnTheVoidDayAndSplitCashFromOtherMethods() {
        DateRange r = new DateRange(NOV_1, LocalDate.of(2026, 11, 3));
        List<DayAmount> received = List.of(
                new DayAmount(NOV_1, PaymentMethod.CASH, 2, new BigDecimal("100000")),
                new DayAmount(NOV_1, PaymentMethod.NEQUI, 1, new BigDecimal("90000")));
        List<DayAmount> voided = List.of(new DayAmount(LocalDate.of(2026, 11, 3), PaymentMethod.CASH, 1,
                new BigDecimal("10000")));

        List<IncomeReportResponse.Period> days = ReportCalculator.periods(r, IncomeGrouping.DAY, received, voided);
        assertThat(days).hasSize(3);
        assertThat(days.get(0).netAmount()).isEqualByComparingTo("190000");
        assertThat(days.get(0).cashNet()).isEqualByComparingTo("100000");
        assertThat(days.get(0).otherNet()).isEqualByComparingTo("90000");
        assertThat(days.get(1).receivedCount()).isZero(); // día sin movimientos: aparece en cero
        assertThat(days.get(2).netAmount()).isEqualByComparingTo("-10000");
        assertThat(days.get(2).cashNet()).isEqualByComparingTo("-10000");

        var methods = ReportCalculator.methodTotals(received, voided);
        assertThat(methods).extracting(m -> m.method()).containsExactly(PaymentMethod.CASH, PaymentMethod.NEQUI);
        assertThat(methods.get(0).netAmount()).isEqualByComparingTo("90000");

        String csv = ReportService.incomeCsv(new IncomeReportResponse(r.from(), r.to(), IncomeGrouping.DAY, methods,
                days)).toString();
        assertThat(csv).contains("Efectivo;2;100000;1;10000;90000\r\n")
                .contains("Total efectivo;2;100000;1;10000;90000\r\n")
                .contains("Total otros métodos;1;90000;0;0;90000\r\n")
                .contains("Total general recaudado;3;190000;1;10000;180000\r\n")
                .contains("2026-11-03;2026-11-03;0;0;1;10000;-10000;-10000;0\r\n");
    }

    // ---- vencimientos ----

    @Test
    void renewalRateCountsOnlyExpiredMemberships() {
        LocalDate today = LocalDate.of(2026, 11, 10);
        List<ExpirationRow> rows = List.of(
                row(1, "2026-11-05", LocalDateTime.of(2026, 11, 6, 9, 0), null),
                row(2, "2026-11-07", null, 99L),
                row(3, "2026-11-08", null, null),
                row(4, "2026-11-12", LocalDateTime.of(2026, 11, 9, 9, 0), null),
                row(5, "2026-11-13", null, null));
        ExpirationsReportResponse r = ReportCalculator.expirations(new DateRange(NOV_1, LocalDate.of(2026, 11, 30)),
                rows, today);
        assertThat(r.total()).isEqualTo(5);
        assertThat(r.expired()).isEqualTo(3);
        assertThat(r.renewed()).isEqualTo(2);
        assertThat(r.pendingPayment()).isEqualTo(1);
        assertThat(r.notRenewed()).isEqualTo(2);
        assertThat(r.renewalRate()).isEqualByComparingTo("33.3"); // 1 de 3 vencidas
        assertThat(r.rows().get(3).state()).isEqualTo(ExpirationsReportResponse.State.IN_FORCE);
        assertThat(r.rows().get(1).renewal()).isEqualTo(ExpirationsReportResponse.Renewal.PENDING_PAYMENT);

        assertThat(ReportCalculator.expirations(new DateRange(today, today), List.of(), today).renewalRate()).isNull();
    }

    private static ExpirationRow row(long id, String end, LocalDateTime renewedAt, Long pending) {
        LocalDate e = LocalDate.parse(end);
        return new ExpirationRow(id, id, "Cliente " + id, "100" + id, null, true, "Mensual", e.minusDays(29), e,
                renewedAt, pending);
    }

    // ---- CSV ----

    @Test
    void csvIsExcelFriendlyAndNeutralizesFormulas() {
        Csv csv = new Csv().row("Nombre", "Valor", "Fecha", "Activo")
                .row("=SUMA(A1)", new BigDecimal("90000.00"), LocalDate.of(2026, 11, 10), true)
                .row("Pérez; \"Ana\"", new BigDecimal("1234.50"), LocalDateTime.of(2026, 11, 10, 22, 5), false)
                .row("-Retiro", new BigDecimal("-5000"), null, null)
                .row("@cmd", 3, " espacio", "+57 300");
        byte[] bytes = csv.bytes();
        assertThat(bytes[0]).isEqualTo((byte) 0xEF);
        assertThat(bytes[1]).isEqualTo((byte) 0xBB);
        assertThat(bytes[2]).isEqualTo((byte) 0xBF);
        assertThat(new String(bytes, StandardCharsets.UTF_8)).isEqualTo("﻿"
                + "Nombre;Valor;Fecha;Activo\r\n"
                + "'=SUMA(A1);90000;2026-11-10;Sí\r\n"
                + "\"Pérez; \"\"Ana\"\"\";1234,5;2026-11-10 22:05;No\r\n"
                + "'-Retiro;-5000;;\r\n"
                + "'@cmd;3;\" espacio\";'+57 300\r\n");
    }
}
