package com.evolutionfit.modules.memberships.dto;

/** Contadores para el número del menú y el panel de inicio. */
public record AlertCountsResponse(long expiring, long recentlyExpired, int warningDays, int recentlyExpiredDays) {
}
