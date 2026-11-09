package com.evolutionfit.modules.roles;

/**
 * Nombres de rol tal como se guardan en la tabla roles.name.
 * Spring Security los usa como authority "ROLE_" + nombre en mayúsculas.
 */
public final class RoleNames {

    public static final String ADMIN = "ADMIN";
    public static final String RECEPTIONIST = "RECEPTIONIST";

    private RoleNames() {
    }
}
