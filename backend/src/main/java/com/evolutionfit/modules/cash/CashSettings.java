package com.evolutionfit.modules.cash;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Configuración de caja (una fila, id = 1, creada por V5). start_date NULL: la caja no ha empezado.
 * Base fija y días que abre: valores por defecto "por confirmar" con el dueño.
 */
@Entity
@Table(name = "cash_settings")
@Getter
@Setter
@NoArgsConstructor
public class CashSettings {

    public static final short SINGLE_ID = 1;

    @Id
    private Short id;

    @Column(name = "opening_base", nullable = false, precision = 12, scale = 2)
    private BigDecimal openingBase;

    @Column(name = "start_date")
    private LocalDate startDate;

    /** Días que abre el gimnasio, como "MON,TUE,...". */
    @Column(name = "open_days", nullable = false, length = 40)
    private String openDays;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    public Set<DayOfWeek> openDaySet() {
        Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        if (openDays != null) {
            Arrays.stream(openDays.split(",")).map(String::trim).filter(s -> !s.isEmpty())
                    .forEach(s -> days.add(fromCode(s)));
        }
        return days;
    }

    public void setOpenDaySet(Set<DayOfWeek> days) {
        this.openDays = days.stream().sorted().map(d -> d.name().substring(0, 3)).collect(Collectors.joining(","));
    }

    public boolean isOpenOn(LocalDate day) {
        return openDaySet().contains(day.getDayOfWeek());
    }

    private static DayOfWeek fromCode(String code) {
        for (DayOfWeek d : DayOfWeek.values()) {
            if (d.name().startsWith(code.toUpperCase())) {
                return d;
            }
        }
        throw new IllegalStateException("Día no válido en cash_settings.open_days: " + code);
    }
}
