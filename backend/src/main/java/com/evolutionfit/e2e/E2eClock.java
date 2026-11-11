package com.evolutionfit.e2e;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

import com.evolutionfit.common.exception.BusinessException;
import com.evolutionfit.config.TimeConfig;

import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Reloj de las pruebas de punta a punta: la hora real más un desfase que solo puede crecer. Reemplaza al
 * reloj del gimnasio (TimeConfig) únicamente con el perfil e2e.
 * <p>
 * Solo avanza porque las fechas de registro (created_at) las pone la base / Hibernate con la hora real: si el
 * reloj retrocediera, un pago podría quedar "antes" de su propio registro.
 * <p>
 * Hora de inicio, para que las pruebas no dependan de a qué hora se ejecutan:
 * <ul>
 *   <li>antes de las 8:00 a.m. real: empieza a las 8:00 a.m. de hoy;</li>
 *   <li>entre las 8:00 a.m. y las 8:00 p.m.: la hora real;</li>
 *   <li>desde las 8:00 p.m.: a las 8:00 a.m. de mañana, para tener el día completo antes del corte de las 10 p.m.</li>
 * </ul>
 */
@Component
@Primary
@Profile("e2e")
public class E2eClock extends Clock {

    static final LocalTime DAY_START = LocalTime.of(8, 0);
    static final LocalTime LATEST_START = LocalTime.of(20, 0);

    private final Clock real;
    private volatile Duration offset;

    public E2eClock() {
        this(Clock.system(TimeConfig.GYM_ZONE));
    }

    E2eClock(Clock real) {
        this.real = real;
        LocalDateTime now = LocalDateTime.now(real);
        this.offset = Duration.between(now, startFor(now));
    }

    static LocalDateTime startFor(LocalDateTime real) {
        LocalDate day = real.toLocalDate();
        if (real.toLocalTime().isBefore(DAY_START)) {
            return day.atTime(DAY_START);
        }
        if (!real.toLocalTime().isBefore(LATEST_START)) {
            return day.plusDays(1).atTime(DAY_START);
        }
        return real;
    }

    /** Adelanta el reloj hasta {@code target} (hora de Bogotá). Nunca hacia atrás. */
    public synchronized LocalDateTime advanceTo(LocalDateTime target) {
        LocalDateTime now = LocalDateTime.now(this);
        if (target.isBefore(now)) {
            throw new BusinessException("El reloj de pruebas solo avanza: ya son las " + now);
        }
        offset = offset.plus(Duration.between(now, target));
        return LocalDateTime.now(this);
    }

    public Duration offset() {
        return offset;
    }

    @Override
    public ZoneId getZone() {
        return TimeConfig.GYM_ZONE;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        throw new UnsupportedOperationException("El reloj de pruebas siempre está en hora de Bogotá");
    }

    @Override
    public Instant instant() {
        return real.instant().plus(offset);
    }
}
