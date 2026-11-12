package com.evolutionfit.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLIntegrityConstraintViolationException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockHttpServletRequest;

/** Lo que el manejador de errores escribe en el log no trae la cédula (REGLAS: logs sin datos personales). */
@ExtendWith(OutputCaptureExtension.class)
class GlobalExceptionHandlerLoggingTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/clients");

    @Test
    void constraintViolationIsLoggedWithoutTheDocument(CapturedOutput output) {
        var ex = new DataIntegrityViolationException("could not execute statement",
                new SQLIntegrityConstraintViolationException(
                        "Duplicate entry '1012345678' for key 'clients.uq_clients_document_number'"));

        var response = handler.handleDataIntegrity(ex, request);

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(output).doesNotContain("1012345678").contains("Duplicate entry '***'")
                .contains("uq_clients_document_number");
    }

    @Test
    void unexpectedErrorIsLoggedWithStackTraceButWithoutTheDocument(CapturedOutput output) {
        var ex = new IllegalStateException("no se pudo procesar",
                new SQLIntegrityConstraintViolationException("Duplicate entry '1012345678' for key 'k'"));

        handler.handleUnexpected(ex, request);

        assertThat(output).doesNotContain("1012345678").contains("java.lang.IllegalStateException: no se pudo procesar")
                .contains("Duplicate entry '***'").contains("GlobalExceptionHandlerLoggingTest");
    }
}
