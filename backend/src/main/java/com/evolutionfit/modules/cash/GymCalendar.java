package com.evolutionfit.modules.cash;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Optional;

import com.evolutionfit.common.calendar.ColombianHolidays;

import org.springframework.stereotype.Component;

/**
 * Si el gimnasio abre un día. Orden: excepción del ADMIN para esa fecha → festivo de Colombia (si la
 * configuración cierra en festivos) → días de la semana configurados (por defecto, lunes a sábado).
 * Un día cerrado igual tiene cierre de caja; sin movimientos en efectivo no exige arqueo, y con
 * movimientos se trata como un día normal.
 */
@Component
public class GymCalendar {

    private final GymCalendarOverrideRepository overrideRepository;

    public GymCalendar(GymCalendarOverrideRepository overrideRepository) {
        this.overrideRepository = overrideRepository;
    }

    public DayInfo day(LocalDate date, CashSettings settings) {
        return resolve(date, settings, overrideRepository.findById(date));
    }

    /** La regla sin base de datos (para pruebas y para armar el calendario del año). */
    public static DayInfo resolve(LocalDate date, CashSettings settings, Optional<GymCalendarOverride> override) {
        Optional<String> holiday = ColombianHolidays.name(date);
        boolean defaultOpen = settings.openDaySet().contains(date.getDayOfWeek())
                && !(settings.isClosedOnHolidays() && holiday.isPresent());
        String defaultReason = defaultOpen ? null
                : holiday.isPresent() && settings.isClosedOnHolidays() ? "Festivo: " + holiday.get()
                : date.getDayOfWeek() == DayOfWeek.SUNDAY ? "Domingo" : "Día sin atención";
        if (override.isPresent()) {
            GymCalendarOverride o = override.get();
            String reason = o.isOpen() ? null
                    : "Cerrado por el administrador" + (o.getNote() == null ? "" : ": " + o.getNote());
            return new DayInfo(date, o.isOpen(), reason, holiday.orElse(null), defaultOpen, true, o.getNote());
        }
        return new DayInfo(date, defaultOpen, defaultReason, holiday.orElse(null), defaultOpen, false, null);
    }

    /**
     * @param closedReason por qué está cerrado (null si abre)
     * @param holiday      nombre del festivo, si lo es
     * @param defaultOpen  lo que diría la regla sin la excepción del ADMIN
     */
    public record DayInfo(LocalDate date, boolean open, String closedReason, String holiday, boolean defaultOpen,
                          boolean overridden, String note) {
    }
}
