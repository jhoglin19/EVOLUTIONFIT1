package com.evolutionfit.modules.users;

import java.time.Instant;
import java.time.LocalDateTime;

import com.evolutionfit.modules.roles.Role;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @Column(name = "username", nullable = false, length = 60, unique = true)
    private String username;

    @Column(name = "email", length = 150, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    /**
     * Último cambio o restablecimiento de la contraseña (hora real, no el reloj de negocio: se compara con la hora
     * de emisión del token). Los tokens emitidos antes dejan de servir.
     */
    @Column(name = "password_changed_at")
    private Instant passwordChangedAt;

    /** Cambia la contraseña y cierra todas las sesiones abiertas del usuario. */
    public void changePasswordHash(String newHash) {
        this.passwordHash = newHash;
        this.passwordChangedAt = Instant.now();
    }

    @Column(name = "first_name", nullable = false, length = 80)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 80)
    private String lastName;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
