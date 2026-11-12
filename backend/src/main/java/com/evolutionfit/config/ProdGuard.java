package com.evolutionfit.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Protección del perfil prod: el backend de producción NO arranca si detecta algo de las pruebas de punta a punta,
 * que adelantan el reloj (E2eClock) y exponen /api/e2e/clock:
 * <ul>
 *   <li>el perfil e2e activo junto con prod;</li>
 *   <li>APP_E2E_ENABLED=true (app.e2e.enabled).</li>
 * </ul>
 * Como E2eGuard, es un BeanFactoryPostProcessor: corre antes de crear el DataSource y Flyway, así que no se abre
 * ninguna conexión a la base de producción.
 */
@Component
@Profile("prod")
public class ProdGuard implements BeanFactoryPostProcessor, EnvironmentAware, Ordered {

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        check(environment);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    /** Lanza IllegalStateException con todos los problemas encontrados. */
    public static void check(Environment env) {
        List<String> problems = new ArrayList<>();
        if (Arrays.asList(env.getActiveProfiles()).contains("e2e")) {
            problems.add("el perfil e2e está activo junto con prod");
        }
        if ("true".equalsIgnoreCase(env.getProperty("app.e2e.enabled", "false").trim())) {
            problems.add("APP_E2E_ENABLED=true no se permite en producción");
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Perfil prod bloqueado, el backend no arranca: " + String.join("; ", problems));
        }
    }
}
