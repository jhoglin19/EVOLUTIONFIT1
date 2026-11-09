package com.evolutionfit.config;

import com.evolutionfit.modules.roles.Role;
import com.evolutionfit.modules.roles.RoleNames;
import com.evolutionfit.modules.roles.RoleRepository;
import com.evolutionfit.modules.users.User;
import com.evolutionfit.modules.users.UserRepository;

import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Crea el primer administrador cuando la tabla users está vacía.
 * La contraseña viene de la variable de entorno BOOTSTRAP_ADMIN_PASSWORD (nunca se guarda en el código).
 */
@Slf4j
@Component
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final int MIN_PASSWORD_LENGTH = 8;

    private final AppProperties properties;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminBootstrapRunner(AppProperties properties,
                                UserRepository userRepository,
                                RoleRepository roleRepository,
                                PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.count() > 0) {
            return;
        }

        AppProperties.BootstrapAdmin admin = properties.bootstrapAdmin();
        String password = admin.password();
        if (password == null || password.isBlank()) {
            log.warn("No existen usuarios y BOOTSTRAP_ADMIN_PASSWORD está vacía: no se creó ningún administrador.");
            return;
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            log.error("BOOTSTRAP_ADMIN_PASSWORD debe tener al menos {} caracteres: no se creó el administrador.",
                    MIN_PASSWORD_LENGTH);
            return;
        }

        Role adminRole = findOrCreateRole(RoleNames.ADMIN, "Acceso total al sistema");
        findOrCreateRole(RoleNames.RECEPTIONIST, "Atención en recepción");

        User user = new User();
        user.setUsername(admin.username().trim());
        user.setEmail(admin.email() == null || admin.email().isBlank() ? null : admin.email().trim());
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setFirstName(admin.firstName());
        user.setLastName(admin.lastName());
        user.setRole(adminRole);
        user.setActive(true);
        userRepository.save(user);

        log.info("Administrador inicial '{}' creado. Cambia su contraseña después del primer ingreso.",
                user.getUsername());
    }

    private Role findOrCreateRole(String name, String description) {
        return roleRepository.findByName(name).orElseGet(() -> {
            log.info("Creando rol '{}'", name);
            return roleRepository.save(new Role(name, description));
        });
    }
}
