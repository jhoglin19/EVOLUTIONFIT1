package com.evolutionfit.common.logging;

import java.util.regex.Pattern;

/**
 * Quita de los mensajes que van al log los valores que pueden identificar a una persona (cédula, teléfono).
 *
 * MySQL repite el valor en los errores de restricción, por ejemplo:
 * {@code Duplicate entry 'CC-1012345678' for key 'clients.uq_clients_document'}. Aquí queda
 * {@code Duplicate entry '***' for key 'clients.uq_clients_document'}: se conserva qué restricción falló.
 * Como red de seguridad, cualquier secuencia de 5 o más dígitos se reemplaza por ***.
 */
public final class SensitiveData {

    private static final Pattern DUPLICATE_ENTRY = Pattern.compile("(?i)(Duplicate entry ')(.*?)(' for key)");
    private static final Pattern LONG_NUMBER = Pattern.compile("\\d{5,}");

    private SensitiveData() {
    }

    public static String mask(String message) {
        if (message == null) {
            return null;
        }
        String masked = DUPLICATE_ENTRY.matcher(message).replaceAll("$1***$3");
        return LONG_NUMBER.matcher(masked).replaceAll("***");
    }

    /**
     * Copia de la excepción (y de sus causas) con los mensajes enmascarados y las mismas trazas, para registrar
     * un error inesperado sin perder dónde ocurrió.
     */
    public static Throwable masked(Throwable error) {
        if (error == null) {
            return null;
        }
        Throwable copy = new MaskedException(error.getClass().getName() + ": " + mask(error.getMessage()),
                masked(error.getCause() == error ? null : error.getCause()));
        copy.setStackTrace(error.getStackTrace());
        return copy;
    }

    /** Solo para el log: el tipo original va al inicio del mensaje. */
    static final class MaskedException extends RuntimeException {
        MaskedException(String message, Throwable cause) {
            super(message, cause, false, true);
        }

        @Override
        public String toString() {
            return getMessage();
        }
    }
}
