package com.evolutionfit.common.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.junit.jupiter.api.Test;

/** Los mensajes de error que van al log no llevan cédulas ni otros números largos. */
class SensitiveDataTest {

    @Test
    void duplicateEntryKeepsTheConstraintButNotTheValue() {
        String mysql = "Duplicate entry '1012345678' for key 'clients.uq_clients_document_number'";

        assertThat(SensitiveData.mask(mysql))
                .isEqualTo("Duplicate entry '***' for key 'clients.uq_clients_document_number'");
    }

    @Test
    void anyLongNumberIsMasked() {
        assertThat(SensitiveData.mask("cliente 79990187 con teléfono 3156140736 en el plan 12"))
                .isEqualTo("cliente *** con teléfono *** en el plan 12");
        assertThat(SensitiveData.mask(null)).isNull();
    }

    @Test
    void maskedExceptionKeepsTypesAndStackTraceButNotTheValues() {
        IllegalStateException cause = new IllegalStateException("Duplicate entry 'CC-52345678' for key 'x'");
        RuntimeException error = new RuntimeException("fallo al guardar el cliente 52345678", cause);

        Throwable masked = SensitiveData.masked(error);
        StringWriter out = new StringWriter();
        masked.printStackTrace(new PrintWriter(out));

        assertThat(out.toString())
                .doesNotContain("52345678")
                .contains("java.lang.RuntimeException: fallo al guardar el cliente ***")
                .contains("java.lang.IllegalStateException: Duplicate entry '***' for key 'x'")
                .contains("SensitiveDataTest.maskedExceptionKeepsTypesAndStackTraceButNotTheValues");
    }
}
