package com.evolutionfit.modules.reports;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.evolutionfit.modules.audit.AuditService;
import com.evolutionfit.modules.cash.CashSummary.MethodTotal;
import com.evolutionfit.modules.payments.PaymentMethod;
import com.evolutionfit.modules.reports.ReportRepository.DayAmount;
import com.evolutionfit.modules.reports.dto.DiscountsReportResponse;
import com.evolutionfit.modules.reports.dto.ExpirationsReportResponse;
import com.evolutionfit.modules.reports.dto.IncomeReportResponse;
import com.evolutionfit.modules.reports.dto.NewClientsReportResponse;
import com.evolutionfit.modules.reports.dto.PlanSalesReportResponse;
import com.evolutionfit.modules.reports.dto.VoidedPaymentsReportResponse;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reportes del ADMIN (docs/REGLAS_NEGOCIO.md, sección 11). Solo leen; el controlador exige el rol ADMIN.
 * Las exportaciones quedan en audit_logs (quién exportó qué reporte y de qué fechas).
 */
@Service
public class ReportService {

    static final String AUDIT_REPORT = "REPORT";
    static final String ACTION_EXPORT = "EXPORT";

    private final ReportRepository repository;
    private final ReportProperties properties;
    private final AuditService auditService;
    private final Clock clock;

    public ReportService(ReportRepository repository, ReportProperties properties, AuditService auditService,
                         Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.auditService = auditService;
        this.clock = clock;
    }

    public DateRange range(LocalDate from, LocalDate to) {
        return DateRange.of(from, to, properties.maxRangeDays());
    }

    // ---- reportes ----

    @Transactional(readOnly = true)
    public IncomeReportResponse income(LocalDate from, LocalDate to, IncomeGrouping grouping) {
        DateRange range = range(from, to);
        IncomeGrouping g = grouping == null ? IncomeGrouping.DAY : grouping;
        List<DayAmount> received = repository.receivedByDay(range);
        List<DayAmount> voided = repository.voidedByDay(range);
        return new IncomeReportResponse(range.from(), range.to(), g, ReportCalculator.methodTotals(received, voided),
                ReportCalculator.periods(range, g, received, voided));
    }

    @Transactional(readOnly = true)
    public NewClientsReportResponse newClients(LocalDate from, LocalDate to) {
        DateRange range = range(from, to);
        List<NewClientsReportResponse.Row> fresh = repository.newClients(range);
        List<NewClientsReportResponse.Row> migrated = repository.migratedClients(range);
        List<NewClientsReportResponse.Row> rows = new ArrayList<>(fresh);
        rows.addAll(migrated);
        rows.sort((a, b) -> a.at().compareTo(b.at()));
        return new NewClientsReportResponse(range.from(), range.to(), fresh.size(), migrated.size(), rows);
    }

    @Transactional(readOnly = true)
    public PlanSalesReportResponse planSales(LocalDate from, LocalDate to) {
        DateRange range = range(from, to);
        List<PlanSalesReportResponse.Row> rows = repository.planSales(range);
        return new PlanSalesReportResponse(range.from(), range.to(),
                rows.stream().mapToLong(PlanSalesReportResponse.Row::sold).sum(),
                rows.stream().map(PlanSalesReportResponse.Row::revenue).reduce(BigDecimal.ZERO, BigDecimal::add), rows);
    }

    @Transactional(readOnly = true)
    public VoidedPaymentsReportResponse voidedPayments(LocalDate from, LocalDate to) {
        DateRange range = range(from, to);
        List<VoidedPaymentsReportResponse.Row> rows = repository.voidedPayments(range);
        return new VoidedPaymentsReportResponse(range.from(), range.to(), rows.size(),
                rows.stream().map(VoidedPaymentsReportResponse.Row::amount).reduce(BigDecimal.ZERO, BigDecimal::add),
                rows);
    }

    @Transactional(readOnly = true)
    public DiscountsReportResponse discounts(LocalDate from, LocalDate to) {
        DateRange range = range(from, to);
        List<DiscountsReportResponse.Row> rows = repository.discounts(range);
        List<DiscountsReportResponse.Row> valid = rows.stream()
                .filter(r -> r.status() != com.evolutionfit.modules.payments.PaymentStatus.VOIDED).toList();
        return new DiscountsReportResponse(range.from(), range.to(), valid.size(),
                valid.stream().map(DiscountsReportResponse.Row::discountAmount).reduce(BigDecimal.ZERO, BigDecimal::add),
                rows.size() - valid.size(), rows);
    }

    @Transactional(readOnly = true)
    public ExpirationsReportResponse expirations(LocalDate from, LocalDate to) {
        DateRange range = range(from, to);
        return ReportCalculator.expirations(range, repository.expirations(range), LocalDate.now(clock));
    }

    // ---- exportación CSV ----

    /** Nombre de archivo y contenido. */
    public record Export(String filename, byte[] content) {
    }

    @Transactional
    public Export export(String report, LocalDate from, LocalDate to, IncomeGrouping grouping, Long userId) {
        Csv csv = switch (report) {
            case "income" -> incomeCsv(income(from, to, grouping));
            case "new-clients" -> newClientsCsv(newClients(from, to));
            case "plans" -> plansCsv(planSales(from, to));
            case "voided-payments" -> voidedCsv(voidedPayments(from, to));
            case "discounts" -> discountsCsv(discounts(from, to));
            case "expirations" -> expirationsCsv(expirations(from, to));
            default -> throw new com.evolutionfit.common.exception.ResourceNotFoundException("Reporte no encontrado");
        };
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("report", report);
        detail.put("from", from.toString());
        detail.put("to", to.toString());
        if ("income".equals(report)) {
            detail.put("groupBy", (grouping == null ? IncomeGrouping.DAY : grouping).name());
        }
        auditService.record(AUDIT_REPORT, null, ACTION_EXPORT, null, detail, userId);
        return new Export(report + "_" + from + "_" + to + ".csv", csv.bytes());
    }

    static Csv incomeCsv(IncomeReportResponse r) {
        Csv csv = new Csv();
        csv.row("Ingresos del " + r.from() + " al " + r.to(),
                "Recibido por fecha de pago; anulado por fecha de anulación; neto = recibido - anulado. Días calendario.");
        csv.blank();
        csv.row("Método", "Pagos", "Recibido", "Anulaciones", "Anulado", "Neto");
        Totals cash = new Totals("Total efectivo");
        Totals other = new Totals("Total otros métodos");
        Totals all = new Totals("Total general recaudado");
        for (MethodTotal m : r.methods()) {
            csv.row(m.method().label(), m.receivedCount(), m.receivedAmount(), m.voidedCount(), m.voidedAmount(),
                    m.netAmount());
            (m.method() == PaymentMethod.CASH ? cash : other).add(m);
            all.add(m);
        }
        for (Totals t : List.of(cash, other, all)) {
            csv.row(t.label, t.count, t.received, t.voidedCount, t.voided, t.received.subtract(t.voided));
        }
        csv.blank();
        csv.row("Desde", "Hasta", "Pagos", "Recibido", "Anulaciones", "Anulado", "Neto", "Efectivo neto",
                "Otros métodos neto");
        for (IncomeReportResponse.Period p : r.periods()) {
            csv.row(p.start(), p.end(), p.receivedCount(), p.receivedAmount(), p.voidedCount(), p.voidedAmount(),
                    p.netAmount(), p.cashNet(), p.otherNet());
        }
        return csv;
    }

    static Csv newClientsCsv(NewClientsReportResponse r) {
        Csv csv = new Csv().row("Tipo", "Fecha", "Cliente", "Documento", "Celular", "Cliente activo", "Plan",
                "Valor pagado");
        for (NewClientsReportResponse.Row row : r.rows()) {
            csv.row(row.type() == NewClientsReportResponse.Type.NEW ? "Nuevo" : "Migrado", row.at(), row.clientName(),
                    row.clientDocument(), row.clientPhone(), row.clientActive(), row.planName(), row.paidAmount());
        }
        return csv;
    }

    static Csv plansCsv(PlanSalesReportResponse r) {
        Csv csv = new Csv().row("Plan", "Vendidas", "A clientes nuevos", "Renovaciones", "Ingreso", "Descuentos");
        for (PlanSalesReportResponse.Row row : r.rows()) {
            csv.row(row.planNames(), row.sold(), row.newClients(), row.renewals(), row.revenue(), row.discounts());
        }
        return csv.row("Total", r.totalSold(), null, null, r.totalRevenue(), null);
    }

    static Csv voidedCsv(VoidedPaymentsReportResponse r) {
        Csv csv = new Csv().row("Comprobante", "Fecha del pago", "Fecha de anulación", "Cliente", "Documento", "Plan",
                "Método", "Valor", "Anulado por", "Motivo");
        for (VoidedPaymentsReportResponse.Row row : r.rows()) {
            csv.row(row.receiptNumber(), row.paidAt(), row.voidedAt(), row.clientName(), row.clientDocument(),
                    row.planName(), row.method().label(), row.amount(), row.voidedBy(), row.voidReason());
        }
        return csv.row("Total", null, null, null, null, null, null, r.amount(), null, null);
    }

    static Csv discountsCsv(DiscountsReportResponse r) {
        Csv csv = new Csv().row("Comprobante", "Fecha del pago", "Cliente", "Documento", "Plan", "Método", "Estado",
                "Precio", "Descuento", "Total pagado", "Motivo", "Aplicado por");
        for (DiscountsReportResponse.Row row : r.rows()) {
            csv.row(row.receiptNumber(), row.paidAt(), row.clientName(), row.clientDocument(), row.planName(),
                    row.method().label(), row.status() == com.evolutionfit.modules.payments.PaymentStatus.VOIDED
                            ? "Anulado" : "Pagado",
                    row.listPrice(), row.discountAmount(), row.amount(), row.discountReason(), row.discountedBy());
        }
        return csv.row("Total (sin anulados)", null, null, null, null, null, null, null, r.discountAmount(), null,
                null, null);
    }

    static Csv expirationsCsv(ExpirationsReportResponse r) {
        Csv csv = new Csv().row("Vence", "Estado", "Renovación", "Fecha de renovación", "Cliente", "Documento",
                "Celular", "Cliente activo", "Plan", "Inicio");
        for (ExpirationsReportResponse.Row row : r.rows()) {
            csv.row(row.endDate(), row.state() == ExpirationsReportResponse.State.EXPIRED ? "Vencida" : "Vigente",
                    switch (row.renewal()) {
                        case RENEWED -> "Renovó";
                        case PENDING_PAYMENT -> "Renovación pendiente de pago";
                        case NOT_RENEWED -> "Sin renovar";
                    }, row.renewedAt(), row.clientName(), row.clientDocument(), row.clientPhone(), row.clientActive(),
                    row.planName(), row.startDate());
        }
        return csv;
    }

    private static final class Totals {
        private final String label;
        private int count;
        private int voidedCount;
        private BigDecimal received = BigDecimal.ZERO;
        private BigDecimal voided = BigDecimal.ZERO;

        private Totals(String label) {
            this.label = label;
        }

        private void add(MethodTotal m) {
            count += m.receivedCount();
            voidedCount += m.voidedCount();
            received = received.add(m.receivedAmount());
            voided = voided.add(m.voidedAmount());
        }
    }
}
