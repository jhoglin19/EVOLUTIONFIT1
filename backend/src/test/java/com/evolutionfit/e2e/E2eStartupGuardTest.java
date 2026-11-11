package com.evolutionfit.e2e;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.evolutionfit.EvolutionFitApplication;

import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;

/**
 * Con el perfil e2e activo y la protección sin cumplir, el backend real no arranca. La URL y las credenciales
 * van por línea de comandos (más prioritaria que el .env importado) y son ficticias, y Flyway está apagado:
 * aun si la protección fallara, no habría conexión ni cambios en la base de desarrollo. La protección corre
 * antes de crear el DataSource, así que en la práctica no se intenta ninguna conexión.
 */
class E2eStartupGuardTest {

    private static void start(String url, String enabled) {
        new SpringApplicationBuilder(EvolutionFitApplication.class)
                .logStartupInfo(false)
                .run("--spring.profiles.active=e2e",
                        "--spring.main.web-application-type=none",
                        "--spring.main.banner-mode=off",
                        "--spring.datasource.url=" + url,
                        "--spring.datasource.username=usuario_ficticio_e2e",
                        "--spring.datasource.password=clave_ficticia_e2e",
                        "--spring.flyway.enabled=false",
                        "--JWT_SECRET=clave-ficticia-solo-para-pruebas-de-arranque-0123456789",
                        "--BOOTSTRAP_ADMIN_PASSWORD=",
                        "--app.cash.scheduler-enabled=false",
                        "--app.e2e.enabled=" + enabled)
                .close();
    }

    @Test
    void withTheE2eProfilePointingToTheDevelopmentDatabaseItDoesNotStart() {
        assertThatThrownBy(() -> start("jdbc:mysql://localhost:3306/evolution_fit", "true"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Perfil e2e bloqueado").hasMessageContaining("3306 o 3307")
                .satisfies(E2eStartupGuardTest::beforeAnyBean);
    }

    @Test
    void withTheE2eProfileButWithoutTheFlagItDoesNotStart() {
        assertThatThrownBy(() -> start("jdbc:mysql://localhost:3308/evolution_fit_e2e", "false"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_E2E_ENABLED=true")
                .satisfies(E2eStartupGuardTest::beforeAnyBean);
    }

    /** Falla en la fase de BeanFactoryPostProcessors: todavía no existe el DataSource ni Flyway. */
    private static void beforeAnyBean(Throwable error) {
        org.assertj.core.api.Assertions.assertThat(error.getStackTrace())
                .anyMatch(f -> f.getMethodName().equals("invokeBeanFactoryPostProcessors"))
                .noneMatch(f -> f.getClassName().contains("Hikari") || f.getClassName().contains("flyway"));
    }
}
