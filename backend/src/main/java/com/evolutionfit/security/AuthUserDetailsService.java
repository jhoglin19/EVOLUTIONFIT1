package com.evolutionfit.security;

import com.evolutionfit.modules.users.User;
import com.evolutionfit.modules.users.UserRepository;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Carga el usuario por nombre de usuario o, si no existe, por correo.
 * El nombre de usuario tiene prioridad para que el token (que guarda el username) siempre resuelva al mismo usuario.
 */
@Service
public class AuthUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public AuthUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String identifier) throws UsernameNotFoundException {
        String value = identifier == null ? "" : identifier.trim();
        User user = userRepository.findByUsername(value)
                .or(() -> userRepository.findByEmail(value))
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));
        return AuthUserDetails.from(user);
    }
}
