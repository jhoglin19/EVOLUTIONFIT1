package com.evolutionfit.e2e;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class E2eGuardTest {

    private static MockEnvironment env(String url, String enabled, String... profiles) {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles(profiles);
        env.setProperty("spring.datasource.url", url);
        if (enabled != null) {
            env.setProperty("app.e2e.enabled", enabled);
        }
        return env;
    }

    private static final String OK_URL =
            "jdbc:mysql://localhost:3308/evolution_fit_e2e?useSSL=false&serverTimezone=America/Bogota";

    @Test
    void theE2eDatabaseWithTheFlagAndOnlyTheE2eProfilePasses() {
        assertThatCode(() -> E2eGuard.check(env(OK_URL, "true", "e2e"))).doesNotThrowAnyException();
        assertThatCode(() -> E2eGuard.check(env("jdbc:mysql://127.0.0.1:3308/evolution_fit_e2e", "true", "e2e")))
                .doesNotThrowAnyException();
    }

    @Test
    void theDevelopmentAndTemporaryDatabasesAreRejected() {
        assertThatThrownBy(() -> E2eGuard.check(env("jdbc:mysql://localhost:3306/evolution_fit", "true", "e2e")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("3306 o 3307");
        assertThatThrownBy(() -> E2eGuard.check(env("jdbc:mysql://localhost:3307/evolution_fit_e2e", "true", "e2e")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("3306 o 3307");
        assertThatThrownBy(() -> E2eGuard.check(env("jdbc:mysql://localhost:3308/evolution_fit", "true", "e2e")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("evolution_fit_e2e");
        assertThatThrownBy(() -> E2eGuard.check(env("jdbc:mysql://db.example.com:3308/evolution_fit_e2e", "true", "e2e")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("localhost");
        assertThatThrownBy(() -> E2eGuard.check(env("", "true", "e2e")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void withoutTheFlagOrWithAnotherProfileItDoesNotStart() {
        assertThatThrownBy(() -> E2eGuard.check(env(OK_URL, null, "e2e")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("APP_E2E_ENABLED=true");
        assertThatThrownBy(() -> E2eGuard.check(env(OK_URL, "false", "e2e")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("APP_E2E_ENABLED=true");
        assertThatThrownBy(() -> E2eGuard.check(env(OK_URL, "true", "e2e", "prod")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("único perfil");
    }
}
