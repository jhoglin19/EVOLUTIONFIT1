package com.evolutionfit.security;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import com.evolutionfit.modules.users.User;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Usuario autenticado. Se construye a partir de la entidad User con el rol ya cargado.
 */
public class AuthUserDetails implements UserDetails {

    private final Long id;
    private final String username;
    private final String passwordHash;
    private final String roleName;
    private final boolean active;
    private final Instant passwordChangedAt;

    public AuthUserDetails(Long id, String username, String passwordHash, String roleName, boolean active) {
        this(id, username, passwordHash, roleName, active, null);
    }

    public AuthUserDetails(Long id, String username, String passwordHash, String roleName, boolean active,
                           Instant passwordChangedAt) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.roleName = roleName;
        this.active = active;
        this.passwordChangedAt = passwordChangedAt;
    }

    public static AuthUserDetails from(User user) {
        return new AuthUserDetails(user.getId(), user.getUsername(), user.getPasswordHash(),
                user.getRole().getName(), user.isActive(), user.getPasswordChangedAt());
    }

    /**
     * El token se emitió antes del último cambio de contraseña. La hora de emisión del JWT va en segundos, así que el
     * cambio se compara truncado al segundo: un inicio de sesión en el mismo segundo del cambio sigue sirviendo.
     */
    public boolean tokenIssuedBeforePasswordChange(Instant issuedAt) {
        return passwordChangedAt != null
                && (issuedAt == null || issuedAt.isBefore(passwordChangedAt.truncatedTo(ChronoUnit.SECONDS)));
    }

    public Long getId() {
        return id;
    }

    public String getRoleName() {
        return roleName;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + roleName.toUpperCase(Locale.ROOT)));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }
}
