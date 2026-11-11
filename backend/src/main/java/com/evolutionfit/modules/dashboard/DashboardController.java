package com.evolutionfit.modules.dashboard;

import com.evolutionfit.modules.dashboard.dto.AdminDashboardResponse;
import com.evolutionfit.modules.dashboard.dto.DashboardResponse;
import com.evolutionfit.security.AuthUserDetails;

import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Panel de inicio. Los ingresos van en un endpoint aparte, solo para el ADMIN. */
@RestController
@RequestMapping("/api/dashboard")
@PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
@Tag(name = "Panel de inicio")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping
    public DashboardResponse today(@AuthenticationPrincipal AuthUserDetails current) {
        return dashboardService.today(current);
    }

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public AdminDashboardResponse admin() {
        return dashboardService.admin();
    }
}
