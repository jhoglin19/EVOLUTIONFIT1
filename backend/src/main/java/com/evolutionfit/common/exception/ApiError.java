package com.evolutionfit.common.exception;

import java.time.Instant;
import java.util.Map;

import org.springframework.http.HttpStatus;

/**
 * Formato único de error para toda la API.
 */
public record ApiError(Instant timestamp,
                       int status,
                       String error,
                       String message,
                       String path,
                       Map<String, String> fieldErrors) {

    public static ApiError of(int status, String message, String path, Map<String, String> fieldErrors) {
        HttpStatus resolved = HttpStatus.resolve(status);
        String reason = resolved != null ? resolved.getReasonPhrase() : "Error";
        return new ApiError(Instant.now(), status, reason, message, path, fieldErrors);
    }
}
