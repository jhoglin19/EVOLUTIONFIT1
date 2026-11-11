package com.evolutionfit.modules.cash;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;

import com.evolutionfit.common.calendar.ColombianHolidays;
import com.evolutionfit.common.exception.BusinessException;
import com.evolutionfit.common.exception.ResourceNotFoundException;
import com.evolutionfit.modules.audit.AuditService;
import com.evolutionfit.modules.cash.dto.CalendarResponse;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Calendario de atención (solo ADMIN, lo exige el controlador). El ADMIN puede abrir o cerrar una fecha
 * concreta. Un día que ya tiene cierre de caja no se puede cambiar: los cierres guardados no cambian.
 */
@Service
public class GymCalendarService {

    static final String AUDIT_ENTITY = "GYM_CALENDAR";

    private final GymCalendarOverrideRepository overrideRepository;
    private final CashSettingsRepository settingsRepository;
    private final CashClosingRepository closingRepository;
    private final AuditService auditService;
    private final Clock clock;

    public GymCalendarService(GymCalendarOverrideRepository overrideRepository, CashSettingsRepository settingsRepository,
                              CashClosingRepository closingRepository, AuditService auditService, Clock clock) {
        this.overrideRepository = overrideRepository;
        this.settingsRepository = settingsRepository;
        this.closingRepository = closingRepository;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public CalendarResponse year(Integer year) {
        int y = year == null ? LocalDate.now(clock).getYear() : year;
        if (y < 2000 || y > 2100) {
            throw new BusinessException("Año no válido");
        }
        CashSettings settings = settings();
        Map<LocalDate, Optional<GymCalendarOverride>> days = new TreeMap<>();
        ColombianHolidays.of(y).keySet().forEach(d -> days.put(d, Optional.empty()));
        overrideRepository.findByDayBetweenOrderByDayAsc(LocalDate.of(y, 1, 1), LocalDate.of(y, 12, 31))
                .forEach(o -> days.put(o.getDay(), Optional.of(o)));
        List<CalendarResponse.Day> list = days.entrySet().stream()
                .map(e -> toDay(GymCalendar.resolve(e.getKey(), settings, e.getValue()))).toList();
        return new CalendarResponse(y, List.copyOf(new TreeSet<DayOfWeek>(settings.openDaySet())),
                settings.isClosedOnHolidays(), list);
    }

    @Transactional
    public CalendarResponse.Day set(LocalDate day, boolean open, String note, Long adminId) {
        ensureNotClosed(day);
        Optional<GymCalendarOverride> existing = overrideRepository.findById(day);
        Map<String, Object> before = existing.map(GymCalendarService::snapshot).orElse(null);
        GymCalendarOverride o = existing.orElseGet(() -> {
            GymCalendarOverride n = new GymCalendarOverride();
            n.setDay(day);
            return n;
        });
        o.setOpen(open);
        o.setNote(note == null || note.isBlank() ? null : note.trim());
        o.setUpdatedBy(adminId);
        GymCalendarOverride saved = overrideRepository.saveAndFlush(o);
        auditService.record(AUDIT_ENTITY, null, before == null ? AuditService.ACTION_CREATE : AuditService.ACTION_UPDATE,
                before, snapshot(saved), adminId);
        return toDay(GymCalendar.resolve(day, settings(), Optional.of(saved)));
    }

    /** Quita la excepción: el día vuelve a la regla general. */
    @Transactional
    public CalendarResponse.Day clear(LocalDate day, Long adminId) {
        ensureNotClosed(day);
        GymCalendarOverride o = overrideRepository.findById(day)
                .orElseThrow(() -> new ResourceNotFoundException("Ese día no tiene una excepción"));
        overrideRepository.delete(o);
        overrideRepository.flush();
        auditService.record(AUDIT_ENTITY, null, "DELETE", snapshot(o), null, adminId);
        return toDay(GymCalendar.resolve(day, settings(), Optional.empty()));
    }

    private void ensureNotClosed(LocalDate day) {
        if (closingRepository.findByCashDate(day).isPresent()) {
            throw new BusinessException("Ese día ya tiene cierre de caja; los cierres guardados no cambian");
        }
    }

    private CalendarResponse.Day toDay(GymCalendar.DayInfo d) {
        return new CalendarResponse.Day(d.date(), d.open(), d.closedReason(), d.holiday(), d.defaultOpen(),
                d.overridden(), d.note(), closingRepository.findByCashDate(d.date()).isPresent());
    }

    private CashSettings settings() {
        return settingsRepository.findById(CashSettings.SINGLE_ID)
                .orElseThrow(() -> new IllegalStateException("Falta la fila de cash_settings (V5)"));
    }

    private static Map<String, Object> snapshot(GymCalendarOverride o) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("day", o.getDay().toString());
        m.put("open", o.isOpen());
        m.put("note", o.getNote());
        return m;
    }
}
