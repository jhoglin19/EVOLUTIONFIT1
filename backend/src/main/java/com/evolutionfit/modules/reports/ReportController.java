package com.evolutionfit.modules.reports;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import com.evolutionfit.modules.reports.dto.DiscountsReportResponse;
import com.evolutionfit.modules.reports.dto.ExpirationsReportResponse;
import com.evolutionfit.modules.reports.dto.IncomeReportResponse;
import com.evolutionfit.modules.reports.dto.NewClientsReportResponse;
import com.evolutionfit.modules.reports.dto.PlanSalesReportResponse;
import com.evolutionfit.modules.reports.dto.VoidedPaymentsReportResponse;
import com.evolutionfit.security.AuthUserDetails;

import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Reportes y exportación CSV. Solo ADMIN. Fechas en hora de Bogotá, con los dos extremos incluidos. */
@RestController
@RequestMapping("/api/reports")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Reportes")
public class ReportController {

    private static final MediaType CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/income")
    public IncomeReportResponse income(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                       @RequestParam(required = false) IncomeGrouping groupBy) {
        return reportService.income(from, to, groupBy);
    }

    @GetMapping("/new-clients")
    public NewClientsReportResponse newClients(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                               @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reportService.newClients(from, to);
    }

    @GetMapping("/plans")
    public PlanSalesReportResponse plans(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reportService.planSales(from, to);
    }

    @GetMapping("/voided-payments")
    public VoidedPaymentsReportResponse voidedPayments(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reportService.voidedPayments(from, to);
    }

    @GetMapping("/discounts")
    public DiscountsReportResponse discounts(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                             @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reportService.discounts(from, to);
    }

    @GetMapping("/expirations")
    public ExpirationsReportResponse expirations(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reportService.expirations(from, to);
    }

    /** report: income, new-clients, plans, voided-payments, discounts o expirations. */
    @GetMapping("/{report}/export")
    public ResponseEntity<byte[]> export(@PathVariable String report,
                                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                         @RequestParam(required = false) IncomeGrouping groupBy,
                                         @AuthenticationPrincipal AuthUserDetails current) {
        ReportService.Export export = reportService.export(report, from, to, groupBy, current.getId());
        return ResponseEntity.ok()
                .contentType(CSV)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(export.filename()).build().toString())
                .body(export.content());
    }
}
