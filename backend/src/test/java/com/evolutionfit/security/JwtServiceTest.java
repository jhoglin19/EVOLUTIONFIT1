package com.evolutionfit.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import com.evolutionfit.config.AppProperties;

import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private static final String SECRET = "una-clave-de-pruebas-de-al-menos-32-caracteres!!";
    private static final String OTHER_SECRET = "otra-clave-distinta-de-pruebas-con-32-caracteres!!";

    private static JwtService service(String secret, long minutes) {
        AppProperties properties = new AppProperties(
                new AppProperties.Jwt(secret, minutes),
                new AppProperties.Cors(List.of("http://localhost:4200")),
                new AppProperties.BootstrapAdmin("admin", "", "", "Admin", "Sistema"));
        return new JwtService(properties);
    }

    private static AuthUserDetails user() {
        return new AuthUserDetails(1L, "admin", "hash", "ADMIN", true);
    }

    @Test
    void generatedTokenReturnsTheUsername() {
        JwtService jwt = service(SECRET, 60);

        String token = jwt.generateToken(user());

        assertThat(jwt.extractUsername(token)).contains("admin");
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        String token = service(OTHER_SECRET, 60).generateToken(user());

        assertThat(service(SECRET, 60).extractUsername(token)).isEmpty();
    }

    @Test
    void expiredTokenIsRejected() {
        JwtService jwt = service(SECRET, -1);

        String token = jwt.generateToken(user());

        assertThat(jwt.extractUsername(token)).isEmpty();
    }

    @Test
    void garbageTokenIsRejected() {
        JwtService jwt = service(SECRET, 60);

        assertThat(jwt.extractUsername("esto-no-es-un-jwt")).isEmpty();
        assertThat(jwt.extractUsername("")).isEmpty();
    }

    @Test
    void secretShorterThan32BytesFailsAtStartup() {
        assertThatThrownBy(() -> service("corta", 60))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void expirationIsReportedInSeconds() {
        assertThat(service(SECRET, 30).getExpirationSeconds()).isEqualTo(1800);
    }
}
