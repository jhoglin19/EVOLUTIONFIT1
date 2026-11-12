package com.evolutionfit.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.evolutionfit.EvolutionFitApplication;

import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.mock.env.MockEnvironment;

/** El perfil prod no arranca si detecta el perfil e2e o APP_E2E_ENABLED=true. */
class ProdGuardTest {

    private static MockEnvironment env(String enabled, String... profiles) {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles(profiles);
        if (enabled != null) {
            env.setProperty("app.e2e.enabled", enabled);
        }
        return env;
    }

    @Test
    void productionWithoutE2eSettingsPasses() {
        assertThatCode(() -> ProdGuard.check(env(null, "prod"))).doesNotThrowAnyException();
        assertThatCode(() -> ProdGuard.check(env("false", "prod"))).doesNotThrowAnyException();
    }

    @Test
    void productionWithTheE2eProfileOrFlagIsBlocked() {
        assertThatThrownBy(() -> ProdGuard.check(env(null, "prod", "e2e")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("el perfil e2e está activo");
        assertThatThrownBy(() -> ProdGuard.check(env(" TRUE ", "prod")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("APP_E2E_ENABLED=true");
    }

    /**
     * Arranque real con datos ficticios y Flyway apagado: falla en la fase de BeanFactoryPostProcessors, antes de
     * crear el DataSource, así que no se intenta ninguna conexión.
     */
    @Test
    void theRealApplicationDoesNotStartWithProdAndTheE2eFlag() {
        assertThatThrownBy(() -> new SpringApplicationBuilder(EvolutionFitApplication.class)
                .logStartupInfo(false)
                .run("--spring.profiles.active=prod",
                        "--spring.main.web-application-type=none",
                        "--spring.main.banner-mode=off",
                        "--spring.datasource.url=jdbc:mysql://localhost:3399/base_ficticia",
                        "--spring.datasource.username=usuario_ficticio",
                        "--spring.datasource.password=clave_ficticia",
                        "--spring.flyway.enabled=false",
                        "--JWT_SECRET=clave-ficticia-solo-para-pruebas-de-arranque-0123456789",
                        "--BOOTSTRAP_ADMIN_PASSWORD=",
                        "--app.cash.scheduler-enabled=false",
                        "--app.e2e.enabled=true")
                .close())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Perfil prod bloqueado")
                .satisfies(error -> assertThat(error.getStackTrace())
                        .anyMatch(f -> f.getMethodName().equals("invokeBeanFactoryPostProcessors")));
    }
}
