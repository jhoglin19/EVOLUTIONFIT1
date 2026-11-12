package com.evolutionfit.modules.auth;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;

import com.evolutionfit.common.exception.BusinessException;
import com.evolutionfit.common.exception.ResourceNotFoundException;
import com.evolutionfit.common.exception.TooManyLoginAttemptsException;
import com.evolutionfit.modules.auth.dto.ChangePasswordRequest;
import com.evolutionfit.modules.auth.dto.LoginRequest;
import com.evolutionfit.modules.auth.dto.LoginResponse;
import com.evolutionfit.modules.users.User;
import com.evolutionfit.modules.users.UserRepository;
import com.evolutionfit.modules.users.dto.UserResponse;
import com.evolutionfit.security.AuthUserDetails;
import com.evolutionfit.security.JwtService;
import com.evolutionfit.security.LoginAttemptService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptService loginAttempts;

    public AuthService(AuthenticationManager authenticationManager,
                       JwtService jwtService,
                       UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       LoginAttemptService loginAttempts) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.loginAttempts = loginAttempts;
    }

    /**
     * Valida las credenciales (usuario o correo + contraseña) y emite un token JWT.
     * Lanza BadCredentialsException / DisabledException, que el manejador global responde como 401.
     */
    @Transactional
    public LoginResponse login(LoginRequest request, String ip) {
        String identifier = request.identifier().trim();
        // Usuario y correo de la misma cuenta comparten contador; los identificadores que no existen también cuentan.
        Optional<User> account = userRepository.findByUsername(identifier).or(() -> userRepository.findByEmail(identifier));
        String accountKey = account.map(u -> "id:" + u.getId()).orElse("x:" + identifier.toLowerCase(Locale.ROOT));
        // En el registro va el usuario solo si la cuenta existe: así no queda escrita una contraseña tecleada en el
        // campo de usuario. Nunca se registra la contraseña.
        String who = account.map(User::getUsername).orElse("(no existe)");

        Optional<Duration> blocked = loginAttempts.blockedFor(accountKey, ip);
        if (blocked.isPresent()) {
            log.warn("Inicio de sesión bloqueado por intentos fallidos: usuario={} ip={}", who, ip);
            throw new TooManyLoginAttemptsException(blocked.get());
        }

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(identifier, request.password()));
        } catch (BadCredentialsException | DisabledException ex) {
            int count = loginAttempts.recordFailure(accountKey, ip);
            log.warn("Inicio de sesión fallido: usuario={} ip={} fallos de la cuenta en la ventana={}/{}",
                    who, ip, count, loginAttempts.properties().maxFailuresPerUser());
            throw ex;
        }
        loginAttempts.recordSuccess(accountKey);

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

        user.changePasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
    }
}
