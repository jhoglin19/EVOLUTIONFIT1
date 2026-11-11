package com.evolutionfit.modules.receipts.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Todos los datos del encabezado son opcionales: los campos vacíos no se imprimen. */
public record ReceiptSettingsRequest(
        @Size(max = 150, message = "El nombre comercial no puede superar 150 caracteres")
        String tradeName,

        @Size(max = 150, message = "El titular o razón social no puede superar 150 caracteres")
        String legalName,

        /** Texto libre con el dígito de verificación, por ejemplo "900.123.456-7". */
        @Size(max = 30, message = "El NIT no puede superar 30 caracteres")
        String taxId,

        @Size(max = 50, message = "La matrícula mercantil no puede superar 50 caracteres")
        String commercialRegistration,

        @Size(max = 200, message = "La dirección no puede superar 200 caracteres")
        String address,

        @Size(max = 80, message = "La ciudad no puede superar 80 caracteres")
        String city,

        @Size(max = 50, message = "El teléfono no puede superar 50 caracteres")
        String phone,

        @Email(message = "El correo no es válido")
        @Size(max = 150, message = "El correo no puede superar 150 caracteres")
        String email,

        @Pattern(regexp = "^[A-Za-z0-9-]{0,10}$",
                message = "El prefijo admite hasta 10 letras, números o guiones")
        String receiptPrefix,

        /** Primer número de la serie. Solo se puede cambiar antes de emitir el primer comprobante. */
        @Min(value = 1, message = "El número inicial debe ser mayor o igual a 1")
        @Max(value = 999999, message = "El número inicial no puede superar 999999")
        Long initialNumber) {
}
