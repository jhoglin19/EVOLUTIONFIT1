package com.evolutionfit.modules.memberships;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Reglas configurables (application.yml, prefijo app.memberships). Los valores por defecto son los
 * definidos por el dueño en docs/REGLAS_NEGOCIO.md.
 *
 * @param expiryWarningDays días antes del vencimiento en que aparece el aviso (5)
 * @param maxFreezeDays     máximo de días de una congelación (30)
 * @param recentlyExpiredDays días hacia atrás de la alerta "vencidas recientes" (15, fase 7)
 */
@ConfigurationProperties(prefix = "app.memberships")
public record MembershipProperties(Integer expiryWarningDays, Integer maxFreezeDays, Integer recentlyExpiredDays) {

    public MembershipProperties {
        expiryWarningDays = expiryWarningDays == null ? 5 : expiryWarningDays;
        maxFreezeDays = maxFreezeDays == null ? 30 : maxFreezeDays;
        recentlyExpiredDays = recentlyExpiredDays == null ? 15 : recentlyExpiredDays;
    }
}
