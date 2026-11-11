package com.evolutionfit.e2e;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Protección del perfil e2e (pruebas de punta a punta con Playwright). Si el perfil está activo, el backend
 * NO arranca a menos que se cumpla todo:
 * <ul>
 *   <li>APP_E2E_ENABLED=true (app.e2e.enabled);</li>
 *   <li>"e2e" es el único perfil activo;</li>
 *   <li>la base es jdbc:mysql://localhost:3308/evolution_fit_e2e (nunca la de desarrollo, 3306, ni la temporal, 3307).</li>
 * </ul>
 * Es un BeanFactoryPostProcessor: se ejecuta antes de crear cualquier bean, incluidos el DataSource y Flyway,
 * así que con una configuración equivocada no se abre ninguna conexión a la base.
 */
@Component
@Profile("e2e")
public class E2eGuard implements BeanFactoryPostProcessor, EnvironmentAware, Ordered {

    static final String DATABASE = "evolution_fit_e2e";
    static final int PORT = 3308;
    private static final Pattern MYSQL_URL = Pattern.compile("^jdbc:mysql://([^:/?]+):(\\d+)/([^?]+)(\\?.*)?$");
    private static final List<String> LOCAL_HOSTS = List.of("localhost", "127.0.0.1");

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
        if (!"true".equalsIgnoreCase(env.getProperty("app.e2e.enabled", "false").trim())) {
            problems.add("falta APP_E2E_ENABLED=true");
        }
        String[] profiles = env.getActiveProfiles();
        if (!Arrays.equals(profiles, new String[] {"e2e"})) {
            problems.add("el único perfil activo debe ser e2e (activos: " + String.join(", ", profiles) + ")");
        }
        String url = env.getProperty("spring.datasource.url", "");
        Matcher m = MYSQL_URL.matcher(url);
        if (url.contains(":3306") || url.contains(":3307")) {
            problems.add("la base apunta al puerto 3306 o 3307 (desarrollo / temporal)");
        }
        if (!m.matches()) {
            problems.add("la URL de la base no es jdbc:mysql://localhost:" + PORT + "/" + DATABASE);
        } else {
            if (!LOCAL_HOSTS.contains(m.group(1))) {
                problems.add("la base debe estar en localhost (es " + m.group(1) + ")");
            }
            if (!String.valueOf(PORT).equals(m.group(2))) {
                problems.add("la base debe estar en el puerto " + PORT + " (es " + m.group(2) + ")");
            }
            if (!DATABASE.equals(m.group(3))) {
                problems.add("la base debe llamarse " + DATABASE + " (es " + m.group(3) + ")");
            }
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Perfil e2e bloqueado, el backend no arranca: " + String.join("; ", problems));
        }
    }
}
