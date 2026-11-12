package com.evolutionfit.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import com.evolutionfit.config.AppProperties;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

/** V8: un token emitido antes del último cambio de contraseña ya no autentica. */
class JwtAuthenticationFilterTest {

    private final JwtService jwt = new JwtService(new AppProperties(
            new AppProperties.Jwt("una-clave-de-pruebas-de-al-menos-32-caracteres!!", 60),
            new AppProperties.Cors(List.of("http://localhost:4200")),
            new AppProperties.BootstrapAdmin("admin", "", "", "Admin", "Sistema")));
    private final AuthUserDetailsService users = mock(AuthUserDetailsService.class);
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwt, users);

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private boolean authenticates(String token) throws Exception {
        SecurityContextHolder.clearContext();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/clients");
        request.addHeader("Authorization", "Bearer " + token);
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        return SecurityContextHolder.getContext().getAuthentication() != null;
    }

    private void userChangedPasswordAt(Instant changedAt) {
        when(users.loadUserByUsername("recepcion"))
                .thenReturn(new AuthUserDetails(2L, "recepcion", "hash", "RECEPTIONIST", true, changedAt));
    }

    @Test
    void userWhoNeverChangedThePasswordKeepsTheSession() throws Exception {
        userChangedPasswordAt(null);
        String token = jwt.generateToken(new AuthUserDetails(2L, "recepcion", "hash", "RECEPTIONIST", true));

        assertThat(authenticates(token)).isTrue();
    }

    @Test
    void tokenIssuedBeforeThePasswordChangeIsRejected() throws Exception {
        String token = jwt.generateToken(new AuthUserDetails(2L, "recepcion", "hash", "RECEPTIONIST", true));
        userChangedPasswordAt(Instant.now().plus(2, ChronoUnit.SECONDS));

        assertThat(authenticates(token)).isFalse();
    }

    @Test
    void tokenIssuedAfterThePasswordChangeIsAccepted() throws Exception {
        userChangedPasswordAt(Instant.now().minus(2, ChronoUnit.SECONDS));
        String token = jwt.generateToken(new AuthUserDetails(2L, "recepcion", "hash", "RECEPTIONIST", true));

        assertThat(authenticates(token)).isTrue();
    }

    @Test
    void loginInTheSameSecondAsTheChangeStillWorks() {
        Instant changedAt = Instant.parse("2026-11-12T15:00:00.700Z");
        AuthUserDetails user = new AuthUserDetails(2L, "recepcion", "hash", "RECEPTIONIST", true, changedAt);

        assertThat(user.tokenIssuedBeforePasswordChange(Instant.parse("2026-11-12T15:00:00Z"))).isFalse();
        assertThat(user.tokenIssuedBeforePasswordChange(Instant.parse("2026-11-12T14:59:59Z"))).isTrue();
        assertThat(user.tokenIssuedBeforePasswordChange(null)).isTrue();
    }
}
