package com.evolutionfit.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.LocalDateTime;

import com.evolutionfit.common.exception.BusinessException;
import com.evolutionfit.config.TimeConfig;

import org.junit.jupiter.api.Test;

class E2eClockTest {

    private static Clock realAt(String bogota) {
        return Clock.fixed(LocalDateTime.parse(bogota).atZone(TimeConfig.GYM_ZONE).toInstant(), TimeConfig.GYM_ZONE);
    }

    @Test
    void startsAtEightBeforeOpeningUsesRealTimeDuringTheDayAndTomorrowAtEightFromEightPm() {
        assertThat(LocalDateTime.now(new E2eClock(realAt("2026-11-11T06:30:00"))))
                .isEqualTo("2026-11-11T08:00:00");
        assertThat(LocalDateTime.now(new E2eClock(realAt("2026-11-11T14:15:00"))))
                .isEqualTo("2026-11-11T14:15:00");
        assertThat(LocalDateTime.now(new E2eClock(realAt("2026-11-11T20:00:00"))))
                .isEqualTo("2026-11-12T08:00:00");
        assertThat(LocalDateTime.now(new E2eClock(realAt("2026-11-11T23:40:00"))))
                .isEqualTo("2026-11-12T08:00:00");
    }

    @Test
    void neverStartsBehindTheRealTime() {
        for (String real : new String[] {"2026-11-11T00:00:00", "2026-11-11T07:59:59", "2026-11-11T19:59:59", "2026-11-11T21:59:00"}) {
            E2eClock clock = new E2eClock(realAt(real));
            assertThat(clock.offset().isNegative()).as(real).isFalse();
        }
    }

    @Test
    void onlyMovesForward() {
        E2eClock clock = new E2eClock(realAt("2026-11-11T10:00:00"));
        assertThat(clock.advanceTo(LocalDateTime.parse("2026-11-11T22:01:00"))).isEqualTo("2026-11-11T22:01:00");
        assertThat(clock.getZone()).isEqualTo(TimeConfig.GYM_ZONE);
        assertThatThrownBy(() -> clock.advanceTo(LocalDateTime.parse("2026-11-11T21:00:00")))
                .isInstanceOf(BusinessException.class).hasMessageContaining("solo avanza");
        assertThat(LocalDateTime.now(clock)).isEqualTo("2026-11-11T22:01:00");
    }
}
