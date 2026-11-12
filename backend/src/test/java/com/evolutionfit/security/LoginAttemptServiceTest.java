package com.evolutionfit.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

/** Límite de inicios de sesión fallidos: 5 en 15 minutos, por cuenta y por IP (REGLAS_NEGOCIO, sección 13). */
class LoginAttemptServiceTest {

    /** Reloj que se puede adelantar a mano. */
    static final class MovableClock extends Clock {
        private Instant now = Instant.parse("2026-11-12T15:00:00Z");

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final MovableClock clock = new MovableClock();
    private final LoginAttemptService service =
            new LoginAttemptService(new LoginProperties(5, 5, Duration.ofMinutes(15)), clock);

    private void fail(String account, String ip, int times) {
        for (int i = 0; i < times; i++) {
            service.recordFailure(account, ip);
            clock.advance(Duration.ofSeconds(10));
        }
    }

    @Test
    void fourFailuresDoNotBlockAndTheFifthDoes() {
        fail("id:1", "10.0.0.1", 4);
        assertThat(service.blockedFor("id:1", "10.0.0.1")).isEmpty();

        fail("id:1", "10.0.0.1", 1);
        assertThat(service.blockedFor("id:1", "10.0.0.1")).isPresent();
    }

    @Test
    void theAccountIsBlockedFromAnyIp() {
        fail("id:1", "10.0.0.1", 2);
        fail("id:1", "10.0.0.2", 3);

        assertThat(service.blockedFor("id:1", "10.0.0.9")).isPresent();
        assertThat(service.blockedFor("id:2", "10.0.0.9")).isEmpty();
    }

    @Test
    void theIpIsBlockedForEveryAccount() {
        fail("id:1", "10.0.0.1", 2);
        fail("x:no-existe", "10.0.0.1", 3);

        assertThat(service.blockedFor("id:3", "10.0.0.1")).isPresent();
        assertThat(service.blockedFor("id:3", "10.0.0.2")).isEmpty();
    }

    @Test
    void theBlockEndsWhenTheOldestFailureLeavesTheWindow() {
        fail("id:1", "10.0.0.1", 5); // fallos a las 15:00:00, :10, :20, :30 y :40; reloj en 15:00:50
        Duration wait = service.blockedFor("id:1", "10.0.0.1").orElseThrow();
        assertThat(wait).isEqualTo(Duration.ofMinutes(15).minusSeconds(50));

        clock.advance(wait.minusSeconds(1));
        assertThat(service.blockedFor("id:1", "10.0.0.1")).isPresent();
        clock.advance(Duration.ofSeconds(1));
        assertThat(service.blockedFor("id:1", "10.0.0.1")).isEmpty();
    }

    @Test
    void aSuccessfulLoginClearsTheAccountButNotTheIp() {
        fail("id:1", "10.0.0.1", 4);
        service.recordSuccess("id:1");
        fail("id:1", "10.0.0.2", 4);
        assertThat(service.blockedFor("id:1", "10.0.0.3")).isEmpty();

        fail("id:2", "10.0.0.1", 1); // la IP 10.0.0.1 ya llevaba 4
        assertThat(service.blockedFor("id:5", "10.0.0.1")).isPresent();
    }

    @Test
    void purgeDropsExpiredEntries() {
        fail("id:1", "10.0.0.1", 5);
        clock.advance(Duration.ofMinutes(16));
        service.purgeExpired();

        assertThat(service.blockedFor("id:1", "10.0.0.1")).isEmpty();
        assertThat(service.recordFailure("id:1", "10.0.0.1")).isEqualTo(1);
    }

    @Test
    void invalidConfigurationFailsAtStartup() {
        assertThatThrownBy(() -> new LoginProperties(0, 5, Duration.ofMinutes(15)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new LoginProperties(5, 5, Duration.ZERO))
                .isInstanceOf(IllegalStateException.class);
    }
}
