package com.evolutionfit.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank(message = "La contraseña actual es obligatoria")
        @Size(max = 72, message = "La contraseña actual es demasiado larga")
        String currentPassword,

        @NotBlank(message = "La nueva contraseña es obligatoria")
        @Size(min = 8, max = 72, message = "La nueva contraseña debe tener entre 8 y 72 caracteres")
        String newPassword) {
}
