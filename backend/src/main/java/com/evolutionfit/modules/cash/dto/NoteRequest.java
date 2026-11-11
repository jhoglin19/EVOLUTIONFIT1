package com.evolutionfit.modules.cash.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Nota obligatoria de la revisión o motivo de la reapertura de un arqueo. */
public record NoteRequest(
        @NotBlank(message = "La nota es obligatoria")
        @Size(max = 500, message = "La nota no puede superar 500 caracteres")
        String note) {
}
