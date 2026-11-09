package com.evolutionfit.modules.users.dto;

import java.time.LocalDateTime;

import com.evolutionfit.modules.users.User;

public record UserResponse(Long id,
                           String username,
                           String email,
                           String firstName,
                           String lastName,
                           String role,
                           boolean active,
                           LocalDateTime lastLoginAt,
                           LocalDateTime createdAt) {

    /** Requiere que user.role ya esté cargado (los repositorios lo traen con EntityGraph). */
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getRole().getName(),
                user.isActive(),
                user.getLastLoginAt(),
                user.getCreatedAt());
    }
}
