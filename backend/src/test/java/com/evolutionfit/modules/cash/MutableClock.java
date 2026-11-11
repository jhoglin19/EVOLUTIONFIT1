package com.evolutionfit.modules.cash;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import com.evolutionfit.config.TimeConfig;

/** Reloj de Bogotá que las pruebas mueven a mano (por ejemplo, a las 10 p.m. o tres días después). */
public class MutableClock extends Clock {

    private volatile Instant instant = Instant.now();

    public void set(LocalDateTime bogotaTime) {
        instant = bogotaTime.atZone(TimeConfig.GYM_ZONE).toInstant();
    }

    @Override
    public ZoneId getZone() {
        return TimeConfig.GYM_ZONE;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Instant instant() {
        return instant;
    }
}
