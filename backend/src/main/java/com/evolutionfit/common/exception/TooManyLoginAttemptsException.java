package com.evolutionfit.common.exception;

import java.time.Duration;

import org.springframework.http.HttpStatus;

/** Demasiados inicios de sesión fallidos: 429 con Retry-After. */
public class TooManyLoginAttemptsException extends BusinessException {

    private final long retryAfterSeconds;

    public TooManyLoginAttemptsException(Duration wait) {
        super(HttpStatus.TOO_MANY_REQUESTS, message(wait));
        this.retryAfterSeconds = Math.max(1, (long) Math.ceil(wait.toMillis() / 1000.0));
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    private static String message(Duration wait) {
        long minutes = Math.max(1, (long) Math.ceil(wait.toSeconds() / 60.0));
        return "Demasiados intentos fallidos. Espera " + minutes + (minutes == 1 ? " minuto" : " minutos")
                + " e intenta de nuevo.";
    }
}
