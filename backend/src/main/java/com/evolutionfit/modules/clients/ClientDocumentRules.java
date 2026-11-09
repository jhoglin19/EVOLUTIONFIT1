package com.evolutionfit.modules.clients;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import com.evolutionfit.common.exception.BusinessException;

/**
 * Formato del número de documento. Normalizar antes de comparar evita que
 * "1.234.567" y "1234567" se registren como dos personas distintas.
 */
final class ClientDocumentRules {

    private static final Set<String> NUMERIC_TYPES = Set.of("CC", "TI");
    private static final Pattern SEPARATORS = Pattern.compile("[\\s.\\-]");
    private static final Pattern DIGITS = Pattern.compile("[0-9]+");
    private static final Pattern ALPHANUMERIC = Pattern.compile("[A-Z0-9]+");

    private ClientDocumentRules() {
    }

    static String normalize(String number) {
        return SEPARATORS.matcher(number).replaceAll("").toUpperCase(Locale.ROOT);
    }

    static void requireValidFormat(String documentType, String normalizedNumber) {
        if (NUMERIC_TYPES.contains(documentType)) {
            if (!DIGITS.matcher(normalizedNumber).matches()) {
                throw new BusinessException("Para este tipo de documento el número solo puede contener dígitos");
            }
        } else if (!ALPHANUMERIC.matcher(normalizedNumber).matches()) {
            throw new BusinessException("El número de documento solo puede contener letras y números");
        }
    }
}
