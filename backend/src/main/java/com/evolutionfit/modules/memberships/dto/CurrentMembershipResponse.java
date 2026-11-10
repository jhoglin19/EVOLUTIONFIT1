package com.evolutionfit.modules.memberships.dto;

/** Membresía vigente hoy y la siguiente (pendiente de pago o programada). Ambas pueden ser null. */
public record CurrentMembershipResponse(MembershipResponse current, MembershipResponse next) {
}
