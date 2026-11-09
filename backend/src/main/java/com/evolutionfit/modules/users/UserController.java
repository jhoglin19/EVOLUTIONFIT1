package com.evolutionfit.modules.users;

import com.evolutionfit.common.PageResponse;
import com.evolutionfit.modules.users.dto.CreateUserRequest;
import com.evolutionfit.modules.users.dto.ResetPasswordRequest;
import com.evolutionfit.modules.users.dto.UpdateStatusRequest;
import com.evolutionfit.modules.users.dto.UpdateUserRequest;
import com.evolutionfit.modules.users.dto.UserResponse;
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
@RequestMapping("/api/users")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Usuarios", description = "Administración de usuarios del sistema (solo administrador)")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public PageResponse<UserResponse> list(@RequestParam(required = false) String q,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        return PageResponse.from(userService.list(q, page, size));
    }

    @GetMapping("/{id}")
    public UserResponse get(@PathVariable Long id) {
        return userService.get(id);
    }

    @PostMapping
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.create(request));
    }

    @PutMapping("/{id}")
    public UserResponse update(@PathVariable Long id,
                               @Valid @RequestBody UpdateUserRequest request,
                               @AuthenticationPrincipal AuthUserDetails current) {
        return userService.update(id, request, current.getId());
    }

    @PatchMapping("/{id}/status")
    public UserResponse updateStatus(@PathVariable Long id,
                                     @Valid @RequestBody UpdateStatusRequest request,
                                     @AuthenticationPrincipal AuthUserDetails current) {
        return userService.setActive(id, request.active(), current.getId());
    }

    @PutMapping("/{id}/password")
    public ResponseEntity<Void> resetPassword(@PathVariable Long id,
                                              @Valid @RequestBody ResetPasswordRequest request) {
        userService.resetPassword(id, request.newPassword());
        return ResponseEntity.noContent().build();
    }
}
