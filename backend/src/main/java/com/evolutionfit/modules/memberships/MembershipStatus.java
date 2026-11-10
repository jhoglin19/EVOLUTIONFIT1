package com.evolutionfit.modules.memberships;

/**
 * Estados de una membresía.
 * <ul>
 *   <li>PENDING y CANCELLED los fija una acción de un usuario (registro sin pago, anulación).</li>
 *   <li>SCHEDULED, ACTIVE, FROZEN y EXPIRED dependen de la fecha y siempre se calculan con
 *       {@link MembershipRules#resolve}. La columna status solo guarda una copia de ese cálculo.</li>
 * </ul>
 */
public enum MembershipStatus {
    PENDING,
    SCHEDULED,
    ACTIVE,
    FROZEN,
    EXPIRED,
    CANCELLED
}
