package com.evolutionfit.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(Jwt jwt, Cors cors, BootstrapAdmin bootstrapAdmin) {

    public record Jwt(String secret, long expirationMinutes) {
    }

    public record Cors(List<String> allowedOrigins) {
    }

    public record BootstrapAdmin(String username, String email, String password,
                                 String firstName, String lastName) {
    }
}
