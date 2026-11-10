package com.evolutionfit.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Reloj del gimnasio. Los servicios piden "hoy" a este reloj (y no a LocalDate.now()) para que las
 * reglas de fechas se puedan probar con un día fijo.
 */
@Configuration
@EnableScheduling
public class TimeConfig {

    public static final ZoneId GYM_ZONE = ZoneId.of("America/Bogota");

    @Bean
    public Clock clock() {
        return Clock.system(GYM_ZONE);
    }
}
