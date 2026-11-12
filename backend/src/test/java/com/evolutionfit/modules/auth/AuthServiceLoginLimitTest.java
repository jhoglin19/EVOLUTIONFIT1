package com.evolutionfit.modules.auth;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;

import com.evolutionfit.common.exception.TooManyLoginAttemptsException;
import com.evolutionfit.modules.auth.dto.LoginRequest;
import com.evolutionfit.modules.roles.Role;
import com.evolutionfit.modules.users.User;
import com.evolutionfit.modules.users.UserRepository;
import com.evolutionfit.security.AuthUserDetails;
import com.evolutionfit.security.JwtService;
import com.evolutionfit.security.LoginAttemptService;
import com.evolutionfit.security.LoginProperties;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

/** El límite de intentos se aplica antes de verificar la contraseña y cuenta por cuenta, no por lo que se teclea. */
class AuthServiceLoginLimitTest {

    private final AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
    private final UserRepository users = mock(UserRepository.class);
    private final LoginAttemptService attempts = mock(LoginAttemptService.class);
    private AuthService service;
    private User recepcion;

    @BeforeEach
    void setUp() {
        service = new AuthService(authenticationManager, mock(JwtService.class), users, mock(PasswordEncoder.class),
                attempts);
        when(attempts.properties()).thenReturn(new LoginProperties(5, 5, Duration.ofMinutes(15)));
        recepcion = new User();
        recepcion.setId(8L);
        recepcion.setUsername("recepcion");
        recepcion.setEmail("recepcion@ejemplo.co");
        recepcion.setRole(new Role("RECEPTIONIST", "Recepción"));
        recepcion.setActive(true);
        when(users.findByUsername("recepcion")).thenReturn(Optional.of(recepcion));
        when(users.findByUsername("recepcion@ejemplo.co")).thenReturn(Optional.empty());
        when(users.findByEmail("recepcion@ejemplo.co")).thenReturn(Optional.of(recepcion));
        when(users.findByUsername("nadie")).thenReturn(Optional.empty());
        when(users.findByEmail("nadie")).thenReturn(Optional.empty());
    }

    @Test
    void blockedAccountGets429WithoutCheckingThePassword() {
        when(attempts.blockedFor("id:8", "10.0.0.1")).thenReturn(Optional.of(Duration.ofMinutes(14)));

        assertThatThrownBy(() -> service.login(new LoginRequest("recepcion", "la-correcta"), "10.0.0.1"))
                .isInstanceOf(TooManyLoginAttemptsException.class)
                .hasMessage("Demasiados intentos fallidos. Espera 14 minutos e intenta de nuevo.");
        verifyNoInteractions(authenticationManager);
    }

    @Test
    void usernameAndEmailShareTheSameCounter() {
        when(attempts.blockedFor(any(), any())).thenReturn(Optional.empty());
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("x"));

        assertThatThrownBy(() -> service.login(new LoginRequest(" recepcion@ejemplo.co ", "mala"), "10.0.0.1"))
                .isInstanceOf(BadCredentialsException.class);

        verify(attempts).recordFailure("id:8", "10.0.0.1");
    }

    @Test
    void unknownIdentifiersAlsoCount() {
        when(attempts.blockedFor(any(), any())).thenReturn(Optional.empty());
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("x"));

        assertThatThrownBy(() -> service.login(new LoginRequest("Nadie", "mala"), "10.0.0.1"))
                .isInstanceOf(BadCredentialsException.class);

        verify(attempts).recordFailure("x:nadie", "10.0.0.1");
    }

    @Test
    void successfulLoginClearsTheAccountCounter() {
        when(attempts.blockedFor(any(), any())).thenReturn(Optional.empty());
        AuthUserDetails principal = AuthUserDetails.from(recepcion);
        when(authenticationManager.authenticate(any()))
                .thenReturn(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        when(users.findById(8L)).thenReturn(Optional.of(recepcion));

        service.login(new LoginRequest("recepcion", "la-correcta"), "10.0.0.1");

        verify(attempts).recordSuccess("id:8");
    }
}
