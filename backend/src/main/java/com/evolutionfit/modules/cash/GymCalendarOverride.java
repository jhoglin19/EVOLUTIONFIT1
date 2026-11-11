package com.evolutionfit.modules.cash;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Excepción del ADMIN para una fecha: abierto o cerrado, sin importar el día de la semana ni los festivos. */
@Entity
@Table(name = "gym_calendar_overrides")
@Getter
@Setter
@NoArgsConstructor
public class GymCalendarOverride {

    @Id
    @Column(name = "day")
    private LocalDate day;

    @Column(name = "is_open", nullable = false)
    private boolean open;

    @Column(name = "note")
    private String note;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
