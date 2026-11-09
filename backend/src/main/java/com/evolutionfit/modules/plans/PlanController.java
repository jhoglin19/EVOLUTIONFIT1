package com.evolutionfit.modules.plans;

import com.evolutionfit.common.PageResponse;
import com.evolutionfit.modules.plans.dto.PlanRequest;
import com.evolutionfit.modules.plans.dto.PlanResponse;
import com.evolutionfit.modules.plans.dto.UpdatePlanStatusRequest;
import com.evolutionfit.security.AuthUserDetails;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/plans")
@PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
@Tag(name = "Planes", description = "Planes comerciales del gimnasio (solo el administrador los modifica)")
public class PlanController {

    private final PlanService planService;

    public PlanController(PlanService planService) {
        this.planService = planService;
    }

    @GetMapping
    public PageResponse<PlanResponse> list(@RequestParam(required = false) String q,
                                           @RequestParam(required = false) Boolean active,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        return PageResponse.from(planService.list(q, active, page, size));
    }

    @GetMapping("/{id}")
    public PlanResponse get(@PathVariable Long id) {
        return planService.get(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PlanResponse> create(@Valid @RequestBody PlanRequest request,
                                               @AuthenticationPrincipal AuthUserDetails current) {
        return ResponseEntity.status(HttpStatus.CREATED).body(planService.create(request, current.getId()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public PlanResponse update(@PathVariable Long id,
                               @Valid @RequestBody PlanRequest request,
                               @AuthenticationPrincipal AuthUserDetails current) {
        return planService.update(id, request, current.getId());
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public PlanResponse updateStatus(@PathVariable Long id,
                                     @Valid @RequestBody UpdatePlanStatusRequest request,
                                     @AuthenticationPrincipal AuthUserDetails current) {
        return planService.setActive(id, request.active(), current.getId());
    }
}
