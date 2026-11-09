package com.evolutionfit.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Error de regla de negocio. El mensaje se muestra tal cual al usuario.
 */
public class BusinessException extends RuntimeException {

    private final HttpStatus status;

    public BusinessException(String message) {
        this(HttpStatus.BAD_REQUEST, message);
    }

    public BusinessException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
