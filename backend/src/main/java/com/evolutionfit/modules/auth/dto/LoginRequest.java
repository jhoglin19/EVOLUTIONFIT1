package com.evolutionfit.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "El usuario o correo es obligatorio")
        @Size(max = 150, message = "El usuario o correo es demasiado largo")
        String identifier,

        @NotBlank(message = "La contraseña es obligatoria")
        @Size(max = 72, message = "La contraseña es demasiado larga")
        String password) {
}
