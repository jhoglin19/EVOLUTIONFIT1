package com.evolutionfit.modules.auth;

import com.evolutionfit.modules.auth.dto.ChangePasswordRequest;
import com.evolutionfit.modules.auth.dto.LoginRequest;
import com.evolutionfit.modules.auth.dto.LoginResponse;
import com.evolutionfit.modules.users.dto.UserResponse;
import com.evolutionfit.security.AuthUserDetails;

import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Autenticación")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    @SecurityRequirements // endpoint público: sin candado en Swagger
    public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        // Detrás del proxy, Tomcat toma la IP del cliente de X-Forwarded-For (server.forward-headers-strategy).
        return authService.login(request, http.getRemoteAddr());
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public UserResponse me(@AuthenticationPrincipal AuthUserDetails current) {
        return authService.me(current.getId());
    }

    @PostMapping("/change-password")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request,
                                               @AuthenticationPrincipal AuthUserDetails current) {
        authService.changePassword(current.getId(), request);
        return ResponseEntity.noContent().build();
    }
}
