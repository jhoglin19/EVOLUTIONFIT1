package com.evolutionfit.security;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Límite de intentos fallidos de inicio de sesión (decisión del dueño: 5 fallos en 15 minutos, por cuenta y por
 * IP). Se cambia con LOGIN_MAX_FAILURES_PER_USER, LOGIN_MAX_FAILURES_PER_IP y LOGIN_WINDOW, sin migración.
 */
@ConfigurationProperties(prefix = "app.login")
public record LoginProperties(int maxFailuresPerUser, int maxFailuresPerIp, Duration window) {

    public LoginProperties {
        if (maxFailuresPerUser < 1 || maxFailuresPerIp < 1) {
            throw new IllegalStateException("Los límites de intentos de inicio de sesión deben ser de al menos 1");
        }
        if (window == null || window.isNegative() || window.isZero()) {
            throw new IllegalStateException("LOGIN_WINDOW debe ser mayor que cero (por ejemplo 15m)");
        }
    }
}
