package com.evolutionfit;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class EvolutionFitApplication {

    public static void main(String[] args) {
        // Las fechas de membresías, caja y accesos dependen de la hora local del gimnasio.
        TimeZone.setDefault(TimeZone.getTimeZone("America/Bogota"));
        SpringApplication.run(EvolutionFitApplication.class, args);
    }
}
