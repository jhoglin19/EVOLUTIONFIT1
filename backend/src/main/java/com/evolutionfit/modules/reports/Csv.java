package com.evolutionfit.modules.reports;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * CSV que abre bien en Excel en español: UTF-8 con BOM, separador punto y coma, coma decimal y fin de
 * línea CRLF. Los textos que empiezan por = + - @ (o tabulador / retorno) llevan un apóstrofo delante,
 * así Excel no los ejecuta como fórmula (inyección CSV). Los números se escriben sin esa protección.
 */
public final class Csv {

    static final char SEPARATOR = ';';
    private static final String BOM = "﻿";
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final StringBuilder out = new StringBuilder(BOM);

    public Csv row(Object... cells) {
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                out.append(SEPARATOR);
            }
            out.append(cell(cells[i]));
        }
        out.append("\r\n");
        return this;
    }

    public Csv blank() {
        out.append("\r\n");
        return this;
    }

    public byte[] bytes() {
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public String toString() {
        return out.toString();
    }

    static String cell(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof BigDecimal n) {
            return n.stripTrailingZeros().toPlainString().replace('.', ',');
        }
        if (value instanceof Number n) {
            return n.toString();
        }
        if (value instanceof LocalDateTime t) {
            return t.format(DATE_TIME);
        }
        if (value instanceof LocalDate d) {
            return d.toString();
        }
        if (value instanceof Boolean b) {
            return b ? "Sí" : "No";
        }
        return text(value.toString());
    }

    private static String text(String raw) {
        String s = raw;
        if (!s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0) {
            s = "'" + s;
        }
        boolean quote = s.indexOf(SEPARATOR) >= 0 || s.indexOf('"') >= 0 || s.indexOf('\n') >= 0
                || s.indexOf('\r') >= 0 || (!s.isEmpty() && (s.charAt(0) == ' ' || s.charAt(s.length() - 1) == ' '));
        return quote ? '"' + s.replace("\"", "\"\"") + '"' : s;
    }
}
