package com.evolutionfit.modules.cash.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Excepción del ADMIN para una fecha: abierto o cerrado. */
public record CalendarDayRequest(
        @NotNull(message = "Indica si el gimnasio abre o cierra ese día")
        Boolean open,
        @Size(max = 255, message = "La nota no puede superar 255 caracteres")
        String note) {
}
