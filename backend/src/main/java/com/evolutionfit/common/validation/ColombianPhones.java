package com.evolutionfit.common.validation;

import java.util.regex.Pattern;

/**
 * Celulares colombianos. El sistema los guarda con los 10 dígitos, sin indicativo ni separadores
 * (por ejemplo, 3001234567); el enlace de WhatsApp agrega el +57.
 */
public final class ColombianPhones {

    public static final String INVALID_MESSAGE =
            "El celular debe tener 10 dígitos y empezar por 3 (puede llevar el indicativo +57)";

    private static final Pattern SEPARATORS = Pattern.compile("[\\s().-]");
    private static final Pattern MOBILE = Pattern.compile("3\\d{9}");

    private ColombianPhones() {
    }

    /** Los 10 dígitos del celular, o null si el valor no es un celular colombiano válido. */
    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String digits = SEPARATORS.matcher(raw.trim()).replaceAll("");
        if (digits.startsWith("+57")) {
            digits = digits.substring(3);
        } else if (digits.startsWith("57") && digits.length() == 12) {
            digits = digits.substring(2);
        }
        return MOBILE.matcher(digits).matches() ? digits : null;
    }
}
