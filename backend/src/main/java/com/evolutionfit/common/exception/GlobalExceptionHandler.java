package com.evolutionfit.common.exception;

import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

import lombok.extern.slf4j.Slf4j;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Manejo centralizado de excepciones: todas las respuestas de error usan {@link ApiError}.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    // ---- Errores propios de la aplicación ----

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiError> handleBusiness(BusinessException ex, HttpServletRequest request) {
        return build(ex.getStatus().value(), ex.getMessage(), request.getRequestURI(), null);
    }

    @ExceptionHandler({BadCredentialsException.class, DisabledException.class})
    public ResponseEntity<ApiError> handleBadCredentials(Exception ex, HttpServletRequest request) {
        // Mismo mensaje para clave incorrecta y usuario inactivo: no revela el estado de la cuenta.
        return build(HttpStatus.UNAUTHORIZED.value(), "Usuario o contraseña incorrectos",
                request.getRequestURI(), null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN.value(), "No tienes permisos para realizar esta acción",
                request.getRequestURI(), null);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException ex,
                                                              HttpServletRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getConstraintViolations().forEach(v ->
                errors.putIfAbsent(v.getPropertyPath().toString(), v.getMessage()));
        return build(HttpStatus.BAD_REQUEST.value(), "Datos inválidos", request.getRequestURI(), errors);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException ex,
                                                        HttpServletRequest request) {
        log.warn("Violación de integridad de datos: {}", ex.getMostSpecificCause().getMessage());
        return build(HttpStatus.CONFLICT.value(),
                "La operación no se puede completar porque viola una restricción de los datos",
                request.getRequestURI(), null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Error no controlado en {}", request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Error interno del servidor",
                request.getRequestURI(), null);
    }

    // ---- Errores estándar de Spring MVC, unificados al formato ApiError ----

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(fe ->
                errors.putIfAbsent(fe.getField(), fe.getDefaultMessage()));
        return ResponseEntity.badRequest()
                .body(ApiError.of(HttpStatus.BAD_REQUEST.value(), "Datos inválidos", path(request), errors));
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        return ResponseEntity.badRequest()
                .body(ApiError.of(HttpStatus.BAD_REQUEST.value(),
                        "El cuerpo de la solicitud es inválido o está incompleto", path(request), null));
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        ApiError error = ApiError.of(statusCode.value(), messageFor(statusCode), path(request), null);
        return new ResponseEntity<>(error, headers, statusCode);
    }

    // ---- Utilidades ----

    private static ResponseEntity<ApiError> build(int status, String message, String path,
                                                  Map<String, String> fieldErrors) {
        return ResponseEntity.status(status).body(ApiError.of(status, message, path, fieldErrors));
    }

    private static String path(WebRequest request) {
        if (request instanceof ServletWebRequest servletRequest) {
            return servletRequest.getRequest().getRequestURI();
        }
        return null;
    }

    private static String messageFor(HttpStatusCode status) {
        return switch (status.value()) {
            case 404 -> "Recurso no encontrado";
            case 405 -> "Método HTTP no permitido para este recurso";
            case 415 -> "Tipo de contenido no soportado";
            case 400 -> "Solicitud inválida";
            default -> "No se pudo procesar la solicitud";
        };
    }
}
