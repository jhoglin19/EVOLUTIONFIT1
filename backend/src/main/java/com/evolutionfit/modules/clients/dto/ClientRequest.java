package com.evolutionfit.modules.clients.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ClientRequest(
        @NotBlank(message = "El tipo de documento es obligatorio")
        @Pattern(regexp = "CC|TI|CE|PA|PPT", message = "El tipo de documento no es válido")
        String documentType,

        @NotBlank(message = "El número de documento es obligatorio")
        @Size(max = 30, message = "El número de documento no puede superar 30 caracteres")
        String documentNumber,

        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 80, message = "El nombre no puede superar 80 caracteres")
        String firstName,

        @NotBlank(message = "El apellido es obligatorio")
        @Size(max = 80, message = "El apellido no puede superar 80 caracteres")
        String lastName,

        @Email(message = "El correo no es válido")
        @Size(max = 150, message = "El correo no puede superar 150 caracteres")
        String email,

        @Pattern(regexp = "^$|^[0-9+()\\s-]{7,30}$",
                message = "El teléfono debe tener entre 7 y 30 caracteres: dígitos, +, paréntesis, guion o espacios")
        String phone,

        @PastOrPresent(message = "La fecha de nacimiento no puede ser futura")
        LocalDate birthDate,

        @Size(max = 200, message = "La dirección no puede superar 200 caracteres")
        String address,

        @Size(max = 2000, message = "Las notas no pueden superar 2000 caracteres")
        String notes) {
}
