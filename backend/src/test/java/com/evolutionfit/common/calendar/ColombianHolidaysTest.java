package com.evolutionfit.common.calendar;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Festivos contra el calendario oficial publicado (19 festivos en 2026 y en 2027, con el de Chiquinquirá
 * creado por la Ley 2578 de 2026). Si una ley cambia los festivos, estas listas se actualizan con ella.
 */
class ColombianHolidaysTest {

    private static Map<LocalDate, String> official(int year, Object... dateAndName) {
        Map<LocalDate, String> m = new LinkedHashMap<>();
        for (int i = 0; i < dateAndName.length; i += 2) {
            m.put(LocalDate.parse(year + "-" + dateAndName[i]), (String) dateAndName[i + 1]);
        }
        return m;
    }

    @Test
    void holidays2026MatchTheOfficialCalendar() {
        assertThat(ColombianHolidays.of(2026)).containsExactlyEntriesOf(official(2026,
                "01-01", "Año Nuevo",
                "01-12", "Reyes Magos",
                "03-23", "San José",
                "04-02", "Jueves Santo",
                "04-03", "Viernes Santo",
                "05-01", "Día del Trabajo",
                "05-18", "Ascensión del Señor",
                "06-08", "Corpus Christi",
                "06-15", "Sagrado Corazón de Jesús",
                "06-29", "San Pedro y San Pablo",
                "07-13", "Nuestra Señora del Rosario de Chiquinquirá",
                "07-20", "Día de la Independencia",
                "08-07", "Batalla de Boyacá",
                "08-17", "Asunción de la Virgen",
                "10-12", "Día de la Diversidad Étnica y Cultural",
                "11-02", "Todos los Santos",
                "11-16", "Independencia de Cartagena",
                "12-08", "Inmaculada Concepción",
                "12-25", "Navidad"));
    }

    @Test
    void holidays2027MatchTheOfficialCalendar() {
        assertThat(ColombianHolidays.of(2027)).containsExactlyEntriesOf(official(2027,
                "01-01", "Año Nuevo",
                "01-11", "Reyes Magos",
                "03-22", "San José",
                "03-25", "Jueves Santo",
                "03-26", "Viernes Santo",
                "05-01", "Día del Trabajo",
                "05-10", "Ascensión del Señor",
                "05-31", "Corpus Christi",
                "06-07", "Sagrado Corazón de Jesús",
                "07-05", "San Pedro y San Pablo",
                "07-12", "Nuestra Señora del Rosario de Chiquinquirá",
                "07-20", "Día de la Independencia",
                "08-07", "Batalla de Boyacá",
                "08-16", "Asunción de la Virgen",
                "10-18", "Día de la Diversidad Étnica y Cultural",
                "11-01", "Todos los Santos",
                "11-15", "Independencia de Cartagena",
                "12-08", "Inmaculada Concepción",
                "12-25", "Navidad"));
    }

    @Test
    void easterIsComputedWithTheGregorianRule() {
        assertThat(ColombianHolidays.easterSunday(2024)).isEqualTo(LocalDate.of(2024, 3, 31));
        assertThat(ColombianHolidays.easterSunday(2025)).isEqualTo(LocalDate.of(2025, 4, 20));
        assertThat(ColombianHolidays.easterSunday(2026)).isEqualTo(LocalDate.of(2026, 4, 5));
        assertThat(ColombianHolidays.easterSunday(2027)).isEqualTo(LocalDate.of(2027, 3, 28));
        assertThat(ColombianHolidays.easterSunday(2038)).isEqualTo(LocalDate.of(2038, 4, 25));
    }

    @Test
    void movableHolidaysAlwaysFallOnMondayAndFixedOnesNeverMove() {
        for (int year = 2024; year <= 2040; year++) {
            ColombianHolidays.of(year).forEach((date, name) -> {
                boolean fixed = name.matches("Año Nuevo|Día del Trabajo|Día de la Independencia|Batalla de Boyacá"
                        + "|Inmaculada Concepción|Navidad|Jueves Santo|Viernes Santo");
                if (!fixed) {
                    assertThat(date.getDayOfWeek()).as(name + " " + date).isEqualTo(DayOfWeek.MONDAY);
                }
            });
            assertThat(ColombianHolidays.isHoliday(LocalDate.of(year, 12, 25))).isTrue();
            assertThat(ColombianHolidays.isHoliday(LocalDate.of(year, 7, 20))).isTrue();
        }
    }

    @Test
    void chiquinquiraOnlyExistsFrom2026() {
        // 2025: San Pedro (domingo 29 de junio) y el Sagrado Corazón caen el mismo lunes 30: 17 fechas.
        assertThat(ColombianHolidays.of(2025)).hasSize(17)
                .containsEntry(LocalDate.of(2025, 6, 30), "San Pedro y San Pablo / Sagrado Corazón de Jesús")
                .doesNotContainValue("Nuestra Señora del Rosario de Chiquinquirá");
        assertThat(ColombianHolidays.of(2026)).hasSize(19);
        assertThat(ColombianHolidays.name(LocalDate.of(2026, 11, 10))).isEmpty();
        assertThat(ColombianHolidays.name(LocalDate.of(2026, 11, 16))).contains("Independencia de Cartagena");
    }
}
