package com.evolutionfit.modules.users.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @Email(message = "El correo no es válido")
        @Size(max = 150, message = "El correo no puede superar 150 caracteres")
        String email,

        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 80, message = "El nombre no puede superar 80 caracteres")
        String firstName,

        @NotBlank(message = "El apellido es obligatorio")
        @Size(max = 80, message = "El apellido no puede superar 80 caracteres")
        String lastName,

        @NotBlank(message = "El rol es obligatorio")
        String role) {
}
