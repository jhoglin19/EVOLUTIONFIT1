package com.evolutionfit.modules.users.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @NotBlank(message = "El usuario es obligatorio")
        @Size(min = 3, max = 60, message = "El usuario debe tener entre 3 y 60 caracteres")
        @Pattern(regexp = "^[A-Za-z0-9._-]+$",
                message = "El usuario solo puede contener letras, números, punto, guion y guion bajo")
        String username,

        @Email(message = "El correo no es válido")
        @Size(max = 150, message = "El correo no puede superar 150 caracteres")
        String email,

        @NotBlank(message = "La contraseña es obligatoria")
        @Size(min = 8, max = 72, message = "La contraseña debe tener entre 8 y 72 caracteres")
        String password,

        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 80, message = "El nombre no puede superar 80 caracteres")
        String firstName,

        @NotBlank(message = "El apellido es obligatorio")
        @Size(max = 80, message = "El apellido no puede superar 80 caracteres")
        String lastName,

        @NotBlank(message = "El rol es obligatorio")
        String role) {
}
