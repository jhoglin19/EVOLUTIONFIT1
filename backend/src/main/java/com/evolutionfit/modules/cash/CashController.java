package com.evolutionfit.modules.cash;

import java.time.LocalDate;
import java.util.List;

import com.evolutionfit.common.PageResponse;
import com.evolutionfit.modules.cash.dto.CalendarDayRequest;
import com.evolutionfit.modules.cash.dto.CalendarResponse;
import com.evolutionfit.modules.cash.dto.CashClosingDetailResponse;
import com.evolutionfit.modules.cash.dto.CashClosingResponse;
import com.evolutionfit.modules.cash.dto.CashSettingsRequest;
import com.evolutionfit.modules.cash.dto.CashSettingsResponse;
import com.evolutionfit.modules.cash.dto.CashTodayResponse;
import com.evolutionfit.modules.cash.dto.CountPreviewResponse;
import com.evolutionfit.modules.cash.dto.CountRequest;
import com.evolutionfit.modules.cash.dto.MonthlyReportResponse;
import com.evolutionfit.modules.cash.dto.NoteRequest;
import com.evolutionfit.modules.cash.dto.WithdrawalRequest;
import com.evolutionfit.modules.cash.dto.WithdrawalResponse;
import com.evolutionfit.modules.memberships.dto.ReasonRequest;
import com.evolutionfit.security.AuthUserDetails;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Caja. Recepción: día en curso, retiros y sus arqueos pendientes. ADMIN: todo, más historial,
 * revisión, reporte mensual y configuración.
 */
@RestController
@RequestMapping("/api/cash")
@PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
@Tag(name = "Caja", description = "Caja del día, retiros, cierre automático, arqueo y reportes")
public class CashController {

    private final CashService cashService;
    private final CashCountService countService;
    private final CashReportService reportService;
    private final GymCalendarService calendarService;

    public CashController(CashService cashService, CashCountService countService, CashReportService reportService,
                          GymCalendarService calendarService) {
        this.cashService = cashService;
        this.countService = countService;
        this.reportService = reportService;
        this.calendarService = calendarService;
    }

    @GetMapping("/today")
    public CashTodayResponse today(@AuthenticationPrincipal AuthUserDetails current) {
        return cashService.today(current);
    }

    // ---- retiros ----

    @PostMapping("/withdrawals")
    @ResponseStatus(HttpStatus.CREATED)
    public WithdrawalResponse withdraw(@Valid @RequestBody WithdrawalRequest request,
                                       @AuthenticationPrincipal AuthUserDetails current) {
        return cashService.registerWithdrawal(request, current);
    }

    @GetMapping("/withdrawals")
    @PreAuthorize("hasRole('ADMIN')")
    public List<WithdrawalResponse> withdrawals(@RequestParam(required = false)
                                                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                @RequestParam(required = false)
                                                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                @RequestParam(required = false) WithdrawalCategory category) {
        return cashService.listWithdrawals(from, to, category);
    }

    @PostMapping("/withdrawals/{id}/void")
    @PreAuthorize("hasRole('ADMIN')")
    public WithdrawalResponse voidWithdrawal(@PathVariable Long id, @Valid @RequestBody ReasonRequest request,
                                             @AuthenticationPrincipal AuthUserDetails current) {
        return cashService.voidWithdrawal(id, request.reason(), current.getId());
    }

    // ---- cierres y arqueo ----

    @GetMapping("/closings/pending")
    public List<CashClosingResponse> pending() {
        return countService.pending();
    }

    @GetMapping("/closings/{id}/count-preview")
    public CountPreviewResponse countPreview(@PathVariable Long id, @AuthenticationPrincipal AuthUserDetails current) {
        return countService.preview(id, current);
    }

    @PostMapping("/closings/{id}/count")
    public CashClosingResponse count(@PathVariable Long id, @Valid @RequestBody CountRequest request,
                                     @AuthenticationPrincipal AuthUserDetails current) {
        return countService.count(id, request, current);
    }

    @GetMapping("/closings")
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<CashClosingResponse> closings(@RequestParam(required = false)
                                                      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                      @RequestParam(required = false)
                                                      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                      @RequestParam(required = false) CashClosingStatus status,
                                                      @RequestParam(defaultValue = "0") int page,
                                                      @RequestParam(defaultValue = "31") int size) {
        return PageResponse.from(reportService.list(from, to, status, page, size));
    }

    @GetMapping("/closings/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public CashClosingDetailResponse closing(@PathVariable Long id) {
        return reportService.detail(id);
    }

    @PostMapping("/closings/{id}/review")
    @PreAuthorize("hasRole('ADMIN')")
    public CashClosingResponse review(@PathVariable Long id, @Valid @RequestBody NoteRequest request,
                                      @AuthenticationPrincipal AuthUserDetails current) {
        return countService.review(id, request.note(), current.getId());
    }

    @PostMapping("/closings/{id}/reopen-count")
    @PreAuthorize("hasRole('ADMIN')")
    public CashClosingResponse reopenCount(@PathVariable Long id, @Valid @RequestBody ReasonRequest request,
                                           @AuthenticationPrincipal AuthUserDetails current) {
        return countService.reopen(id, request.reason(), current.getId());
    }

    // ---- reporte y configuración ----

    @GetMapping("/reports/monthly")
    @PreAuthorize("hasRole('ADMIN')")
    public MonthlyReportResponse monthly(@RequestParam String month) {
        return reportService.monthly(month);
    }

    @GetMapping("/settings")
    @PreAuthorize("hasRole('ADMIN')")
    public CashSettingsResponse settings() {
        return cashService.getSettings();
    }

    @PutMapping("/settings")
    @PreAuthorize("hasRole('ADMIN')")
    public CashSettingsResponse updateSettings(@Valid @RequestBody CashSettingsRequest request,
                                               @AuthenticationPrincipal AuthUserDetails current) {
        return cashService.updateSettings(request, current.getId());
    }

    // ---- calendario de atención (ADMIN) ----

    @GetMapping("/calendar")
    @PreAuthorize("hasRole('ADMIN')")
    public CalendarResponse calendar(@RequestParam(required = false) Integer year) {
        return calendarService.year(year);
    }

    @PutMapping("/calendar/{day}")
    @PreAuthorize("hasRole('ADMIN')")
    public CalendarResponse.Day setDay(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate day,
                                       @Valid @RequestBody CalendarDayRequest request,
                                       @AuthenticationPrincipal AuthUserDetails current) {
        return calendarService.set(day, request.open(), request.note(), current.getId());
    }

    @DeleteMapping("/calendar/{day}")
    @PreAuthorize("hasRole('ADMIN')")
    public CalendarResponse.Day clearDay(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate day,
                                         @AuthenticationPrincipal AuthUserDetails current) {
        return calendarService.clear(day, current.getId());
    }
}
