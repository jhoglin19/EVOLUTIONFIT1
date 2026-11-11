package com.evolutionfit.common.calendar;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.MonthDay;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Festivos nacionales de Colombia, calculados:
 * <ul>
 *   <li>Fijos: 1 de enero, 1 de mayo, 20 de julio, 7 de agosto, 8 y 25 de diciembre.</li>
 *   <li>Trasladables al lunes siguiente (Ley 51 de 1983, "Ley Emiliani"): 6 de enero, 19 de marzo,
 *       29 de junio, 15 de agosto, 12 de octubre, 1 y 11 de noviembre, y desde 2026 el 9 de julio
 *       (Nuestra Señora del Rosario de Chiquinquirá, Ley 2578 de 2026).</li>
 *   <li>Según la Pascua: Jueves y Viernes Santo (sin traslado), y Ascensión, Corpus Christi y Sagrado
 *       Corazón, que caen en lunes (Pascua + 43, + 64 y + 71 días).</li>
 * </ul>
 * Si cambia la ley, se ajusta aquí y en sus pruebas (que verifican 2026 y 2027 contra el calendario oficial).
 */
public final class ColombianHolidays {

    /** Primer año con el festivo de Chiquinquirá. */
    static final int CHIQUINQUIRA_SINCE = 2026;

    private static final Map<Integer, SortedMap<LocalDate, String>> CACHE = new ConcurrentHashMap<>();

    private ColombianHolidays() {
    }

    /** Festivos del año, en orden, con su nombre. */
    public static SortedMap<LocalDate, String> of(int year) {
        return CACHE.computeIfAbsent(year, ColombianHolidays::compute);
    }

    public static Optional<String> name(LocalDate day) {
        return Optional.ofNullable(of(day.getYear()).get(day));
    }

    public static boolean isHoliday(LocalDate day) {
        return of(day.getYear()).containsKey(day);
    }

    private static SortedMap<LocalDate, String> compute(int year) {
        TreeMap<LocalDate, String> days = new TreeMap<>();
        fixed(days, year, 1, 1, "Año Nuevo");
        moved(days, year, 1, 6, "Reyes Magos");
        moved(days, year, 3, 19, "San José");
        fixed(days, year, 5, 1, "Día del Trabajo");
        moved(days, year, 6, 29, "San Pedro y San Pablo");
        if (year >= CHIQUINQUIRA_SINCE) {
            moved(days, year, 7, 9, "Nuestra Señora del Rosario de Chiquinquirá");
        }
        fixed(days, year, 7, 20, "Día de la Independencia");
        fixed(days, year, 8, 7, "Batalla de Boyacá");
        moved(days, year, 8, 15, "Asunción de la Virgen");
        moved(days, year, 10, 12, "Día de la Diversidad Étnica y Cultural");
        moved(days, year, 11, 1, "Todos los Santos");
        moved(days, year, 11, 11, "Independencia de Cartagena");
        fixed(days, year, 12, 8, "Inmaculada Concepción");
        fixed(days, year, 12, 25, "Navidad");

        LocalDate easter = easterSunday(year);
        add(days, easter.minusDays(3), "Jueves Santo");
        add(days, easter.minusDays(2), "Viernes Santo");
        add(days, easter.plusDays(43), "Ascensión del Señor");
        add(days, easter.plusDays(64), "Corpus Christi");
        add(days, easter.plusDays(71), "Sagrado Corazón de Jesús");
        return Collections.unmodifiableSortedMap(days);
    }

    /** Domingo de Pascua (algoritmo anónimo gregoriano, Meeus/Jones/Butcher). */
    static LocalDate easterSunday(int year) {
        int a = year % 19;
        int b = year / 100;
        int c = year % 100;
        int d = b / 4;
        int e = b % 4;
        int f = (b + 8) / 25;
        int g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4;
        int k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int month = (h + l - 7 * m + 114) / 31;
        int day = ((h + l - 7 * m + 114) % 31) + 1;
        return LocalDate.of(year, month, day);
    }

    private static void fixed(TreeMap<LocalDate, String> days, int year, int month, int day, String name) {
        add(days, MonthDay.of(month, day).atYear(year), name);
    }

    private static void moved(TreeMap<LocalDate, String> days, int year, int month, int day, String name) {
        LocalDate date = LocalDate.of(year, month, day);
        while (date.getDayOfWeek() != DayOfWeek.MONDAY) {
            date = date.plusDays(1);
        }
        add(days, date, name);
    }

    private static void add(TreeMap<LocalDate, String> days, LocalDate date, String name) {
        days.merge(date, name, (a, b) -> a + " / " + b);
    }
}
