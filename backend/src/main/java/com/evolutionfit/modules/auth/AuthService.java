package com.evolutionfit.modules.auth;

import java.time.LocalDateTime;

import com.evolutionfit.common.exception.BusinessException;
import com.evolutionfit.common.exception.ResourceNotFoundException;
import com.evolutionfit.modules.auth.dto.ChangePasswordRequest;
import com.evolutionfit.modules.auth.dto.LoginRequest;
import com.evolutionfit.modules.auth.dto.LoginResponse;
import com.evolutionfit.modules.users.User;
import com.evolutionfit.modules.users.UserRepository;
import com.evolutionfit.modules.users.dto.UserResponse;
import com.evolutionfit.security.AuthUserDetails;
import com.evolutionfit.security.JwtService;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(AuthenticationManager authenticationManager,
                       JwtService jwtService,
                       UserRepository userRepository,
                       PasswordEncoder passwordEncoder) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Valida las credenciales (usuario o correo + contraseña) y emite un token JWT.
     * Lanza BadCredentialsException / DisabledException, que el manejador global responde como 401.
     */
    @Transactional
    public LoginResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.identifier().trim(), request.password()));

        AuthUserDetails principal = (AuthUserDetails) authentication.getPrincipal();
        userRepository.updateLastLogin(principal.getId(), LocalDateTime.now());

        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        return new LoginResponse(
                jwtService.generateToken(principal),
                "Bearer",
                jwtService.getExpirationSeconds(),
                UserResponse.from(user));
    }

    @Transactional(readOnly = true)
    public UserResponse me(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        return UserResponse.from(user);
    }

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        // 400 (no 401): un 401 haría que el frontend cierre la sesión por un simple error de digitación.
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BusinessException("La contraseña actual es incorrecta");
        }
        if (request.currentPassword().equals(request.newPassword())) {
            throw new BusinessException("La nueva contraseña debe ser diferente a la actual");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
    }
}
