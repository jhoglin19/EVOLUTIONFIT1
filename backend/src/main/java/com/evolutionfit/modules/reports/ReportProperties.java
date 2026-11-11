package com.evolutionfit.modules.reports;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Límites de los reportes (application.yml, prefijo app.reports).
 *
 * @param maxRangeDays días máximos de un rango de fechas (366: un año, incluido uno bisiesto)
 */
@ConfigurationProperties(prefix = "app.reports")
public record ReportProperties(Integer maxRangeDays) {

    public ReportProperties {
        maxRangeDays = maxRangeDays == null ? 366 : maxRangeDays;
    }
}
