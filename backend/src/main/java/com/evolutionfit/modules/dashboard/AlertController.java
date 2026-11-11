package com.evolutionfit.modules.dashboard;

import com.evolutionfit.common.PageResponse;
import com.evolutionfit.modules.memberships.ExpirationAlertKind;
import com.evolutionfit.modules.memberships.MembershipAlertService;
import com.evolutionfit.modules.memberships.dto.AlertCountsResponse;
import com.evolutionfit.modules.memberships.dto.ExpirationAlertResponse;

import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Alertas de vencimiento dentro del sistema: recepción y ADMIN. */
@RestController
@RequestMapping("/api/alerts")
@PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
@Tag(name = "Alertas de vencimiento")
public class AlertController {

    private final MembershipAlertService alertService;

    public AlertController(MembershipAlertService alertService) {
        this.alertService = alertService;
    }

    @GetMapping("/expirations")
    public PageResponse<ExpirationAlertResponse> expirations(@RequestParam ExpirationAlertKind kind,
                                                             @RequestParam(required = false) String q,
                                                             @RequestParam(defaultValue = "0") int page,
                                                             @RequestParam(defaultValue = "20") int size) {
        return PageResponse.from(alertService.list(kind, q, page, size));
    }

    @GetMapping("/count")
    public AlertCountsResponse count() {
        return alertService.counts();
    }
}
