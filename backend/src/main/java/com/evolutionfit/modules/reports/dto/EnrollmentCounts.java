package com.evolutionfit.modules.reports.dto;

/**
 * Inscripciones de un periodo (docs/REGLAS_NEGOCIO.md, sección 11).
 *
 * @param newClients     membresías pagadas que son la primera del cliente (de cualquier tipo, migrada o no)
 * @param renewals       membresías pagadas de clientes que ya tenían otra (o que vienen del sistema anterior)
 * @param migrated       membresías migradas registradas en el periodo
 * @param pendingPayment membresías registradas en el periodo que siguen pendientes de pago
 */
public record EnrollmentCounts(long newClients, long renewals, long migrated, long pendingPayment) {
}
