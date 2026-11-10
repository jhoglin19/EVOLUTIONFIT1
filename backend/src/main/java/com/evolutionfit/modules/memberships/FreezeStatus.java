package com.evolutionfit.modules.memberships;

/** PENDING = programada a futuro. Solo CANCELLED es fijo; el resto se calcula con la fecha. */
public enum FreezeStatus {
    PENDING,
    ACTIVE,
    COMPLETED,
    CANCELLED
}
