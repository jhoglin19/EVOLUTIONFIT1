package com.evolutionfit.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.PropertySourcesPropertyResolver;
import org.springframework.core.io.ClassPathResource;

/** Swagger: encendido en desarrollo, apagado por defecto con el perfil prod, encendible con SWAGGER_ENABLED. */
class SwaggerProfileConfigTest {

    private static PropertySourcesPropertyResolver resolver(Map<String, Object> variables, String... files)
            throws IOException {
        MutablePropertySources sources = new MutablePropertySources();
        sources.addFirst(new MapPropertySource("variables", variables));
        for (String file : files) { // como Spring: el archivo del perfil gana sobre application.yml
            for (PropertySource<?> source : new YamlPropertySourceLoader().load(file, new ClassPathResource(file))) {
                sources.addFirst(source);
            }
        }
        return new PropertySourcesPropertyResolver(sources);
    }

    @Test
    void developmentKeepsSwaggerOn() throws IOException {
        var env = resolver(Map.of(), "application.yml");

        assertThat(env.getProperty("springdoc.api-docs.enabled")).isEqualTo("true");
        assertThat(env.getProperty("springdoc.swagger-ui.enabled")).isEqualTo("true");
    }

    @Test
    void productionTurnsSwaggerOffByDefault() throws IOException {
        var env = resolver(Map.of(), "application.yml", "application-prod.yml");

        assertThat(env.getProperty("springdoc.api-docs.enabled")).isEqualTo("false");
        assertThat(env.getProperty("springdoc.swagger-ui.enabled")).isEqualTo("false");
    }

    @Test
    void productionCanTurnItOnWithTheVariable() throws IOException {
        var env = resolver(Map.of("SWAGGER_ENABLED", "true"), "application.yml", "application-prod.yml");

        assertThat(env.getProperty("springdoc.api-docs.enabled")).isEqualTo("true");
        assertThat(env.getProperty("springdoc.swagger-ui.enabled")).isEqualTo("true");
    }
}
