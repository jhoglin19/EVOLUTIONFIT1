package com.evolutionfit.modules.memberships;

import java.time.LocalDate;
import java.util.List;

import com.evolutionfit.common.PageResponse;
import com.evolutionfit.modules.memberships.dto.CreateFreezeRequest;
import com.evolutionfit.modules.memberships.dto.CreateMembershipRequest;
import com.evolutionfit.modules.memberships.dto.CurrentMembershipResponse;
import com.evolutionfit.modules.memberships.dto.ExtendFreezeRequest;
import com.evolutionfit.modules.memberships.dto.MembershipPreviewResponse;
import com.evolutionfit.modules.memberships.dto.MembershipResponse;
import com.evolutionfit.modules.memberships.dto.MigratedMembershipRequest;
import com.evolutionfit.modules.memberships.dto.PaymentPreviewResponse;
import com.evolutionfit.modules.memberships.dto.ReasonRequest;
import com.evolutionfit.security.AuthUserDetails;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
@Tag(name = "Membresías", description = "Membresías y congelaciones (anular, migrar, ampliar y cancelar: solo ADMIN)")
public class MembershipController {

    private final MembershipService membershipService;
    private final MembershipFreezeService freezeService;

    public MembershipController(MembershipService membershipService, MembershipFreezeService freezeService) {
        this.membershipService = membershipService;
        this.freezeService = freezeService;
    }

    // ---- consultas ----

    /** {@code state} y {@code expiringSoon} se calculan con fechas. */
    @GetMapping("/memberships")
    public PageResponse<MembershipResponse> list(@RequestParam(required = false) MembershipStatus state,
                                                 @RequestParam(defaultValue = "false") boolean expiringSoon,
                                                 @RequestParam(required = false) String q,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        return PageResponse.from(membershipService.list(state, expiringSoon, q, page, size));
    }

    @GetMapping("/memberships/preview")
    public MembershipPreviewResponse preview(@RequestParam Long clientId,
                                             @RequestParam Long planId,
                                             @RequestParam(required = false)
                                             @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate) {
        return membershipService.preview(clientId, planId, startDate);
    }

    @GetMapping("/memberships/{id}")
    public MembershipResponse get(@PathVariable Long id) {
        return membershipService.get(id);
    }

    /** Fechas que tendrá una membresía pendiente si se paga hoy. */
    @GetMapping("/memberships/{id}/payment-preview")
    public PaymentPreviewResponse paymentPreview(@PathVariable Long id) {
        return membershipService.paymentPreview(id);
    }

    @GetMapping("/clients/{clientId}/memberships")
    public List<MembershipResponse> history(@PathVariable Long clientId) {
        return membershipService.history(clientId);
    }

    @GetMapping("/clients/{clientId}/memberships/current")
    public CurrentMembershipResponse current(@PathVariable Long clientId) {
        return membershipService.current(clientId);
    }

    // ---- membresías ----

    @PostMapping("/memberships")
    public ResponseEntity<MembershipResponse> create(@Valid @RequestBody CreateMembershipRequest request,
                                                     @AuthenticationPrincipal AuthUserDetails current) {
        return ResponseEntity.status(HttpStatus.CREATED).body(membershipService.create(request, current.getId()));
    }

    @PostMapping("/memberships/migrated")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MembershipResponse> createMigrated(@Valid @RequestBody MigratedMembershipRequest request,
                                                             @AuthenticationPrincipal AuthUserDetails current) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(membershipService.createMigrated(request, current.getId()));
    }

    @PostMapping("/memberships/{id}/cancel")
    @PreAuthorize("hasRole('ADMIN')")
    public MembershipResponse cancel(@PathVariable Long id, @Valid @RequestBody ReasonRequest request,
                                     @AuthenticationPrincipal AuthUserDetails current) {
        return membershipService.cancel(id, request.reason(), current.getId());
    }

    // ---- congelaciones ----

    @PostMapping("/memberships/{id}/freezes")
    public ResponseEntity<MembershipResponse> freeze(@PathVariable Long id,
                                                     @Valid @RequestBody CreateFreezeRequest request,
                                                     @AuthenticationPrincipal AuthUserDetails current) {
        return ResponseEntity.status(HttpStatus.CREATED).body(freezeService.create(id, request, current.getId()));
    }

    @PatchMapping("/freezes/{id}/extend")
    @PreAuthorize("hasRole('ADMIN')")
    public MembershipResponse extendFreeze(@PathVariable Long id, @Valid @RequestBody ExtendFreezeRequest request,
                                           @AuthenticationPrincipal AuthUserDetails current) {
        return freezeService.extend(id, request, current.getId());
    }

    @PostMapping("/freezes/{id}/cancel")
    @PreAuthorize("hasRole('ADMIN')")
    public MembershipResponse cancelFreeze(@PathVariable Long id, @Valid @RequestBody ReasonRequest request,
                                           @AuthenticationPrincipal AuthUserDetails current) {
        return freezeService.cancel(id, request.reason(), current.getId());
    }
}
