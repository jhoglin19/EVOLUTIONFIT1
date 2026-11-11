package com.evolutionfit.modules.cash;

import java.time.LocalTime;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Caja (docs/REGLAS_NEGOCIO.md, sección 4). La base, la fecha de inicio y los días que abre están en la
 * tabla cash_settings (las edita el ADMIN); aquí solo va lo que no cambia en la operación diaria.
 *
 * @param closingTime      hora del cierre automático, en hora de Bogotá (por defecto 22:00)
 * @param schedulerEnabled si false no corre el cierre automático programado (solo para pruebas)
 */
@ConfigurationProperties(prefix = "app.cash")
public record CashProperties(String closingTime, Boolean schedulerEnabled) {

    public CashProperties {
        closingTime = closingTime == null || closingTime.isBlank() ? "22:00" : closingTime.trim();
        schedulerEnabled = schedulerEnabled == null || schedulerEnabled;
    }

    public LocalTime closingLocalTime() {
        return LocalTime.parse(closingTime);
    }
}
