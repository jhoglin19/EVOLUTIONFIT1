package com.evolutionfit.security;

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

    public AuthUserDetails(Long id, String username, String passwordHash, String roleName, boolean active) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.roleName = roleName;
        this.active = active;
    }

    public static AuthUserDetails from(User user) {
        return new AuthUserDetails(user.getId(), user.getUsername(), user.getPasswordHash(),
                user.getRole().getName(), user.isActive());
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
