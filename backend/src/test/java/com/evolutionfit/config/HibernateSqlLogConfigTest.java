package com.evolutionfit.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;

/**
 * Hibernate registra el mensaje crudo de MySQL (con la cédula en "Duplicate entry"): su logger queda apagado y el
 * error lo registra GlobalExceptionHandler ya enmascarado.
 */
class HibernateSqlLogConfigTest {

    @Test
    void sqlExceptionHelperIsOff() throws IOException {
        var sources = new YamlPropertySourceLoader().load("application.yml", new ClassPathResource("application.yml"));

        assertThat(sources).anySatisfy(source -> assertThat(
                String.valueOf(source.getProperty("logging.level.org.hibernate.engine.jdbc.spi.SqlExceptionHelper")))
                .isEqualToIgnoringCase("off"));
    }
}
