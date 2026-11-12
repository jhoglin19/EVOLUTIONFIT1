package com.evolutionfit.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Cuenta los inicios de sesión fallidos en una ventana deslizante, por cuenta y por IP. Si una de las dos llega al
 * límite, se rechaza todo intento (aun con la contraseña correcta) hasta que el fallo más antiguo salga de la
 * ventana.
 *
 * - Usa la hora real (no E2eClock): es una defensa de seguridad, no una regla de negocio.
 * - Vive en memoria: el sistema corre en un solo servidor. Reiniciar el backend borra los contadores.
 * - Un inicio de sesión correcto borra los fallos de esa cuenta, no los de la IP.
 */
@Service
public class LoginAttemptService {

    private final LoginProperties properties;
    private final Clock clock;
    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();

    @Autowired
    public LoginAttemptService(LoginProperties properties) {
        this(properties, Clock.systemUTC());
    }

    LoginAttemptService(LoginProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    /** Tiempo que falta para poder intentar de nuevo, si la cuenta o la IP están bloqueadas. */
    public Optional<Duration> blockedFor(String accountKey, String ip) {
        Instant now = clock.instant();
        Duration wait = max(waitFor(userKey(accountKey), properties.maxFailuresPerUser(), now),
                waitFor(ipKey(ip), properties.maxFailuresPerIp(), now));
        return wait.isZero() ? Optional.empty() : Optional.of(wait);
    }

    /** Registra un fallo y devuelve cuántos lleva la cuenta en la ventana. */
    public int recordFailure(String accountKey, String ip) {
        Instant now = clock.instant();
        add(ipKey(ip), now);
        return add(userKey(accountKey), now);
    }

    public void recordSuccess(String accountKey) {
        failures.remove(userKey(accountKey));
    }

    public LoginProperties properties() {
        return properties;
    }

    /** Limpieza periódica para que el mapa no crezca con IPs o nombres que no vuelven. */
    @Scheduled(fixedDelay = 300_000)
    public void purgeExpired() {
        Instant now = clock.instant();
        failures.forEach((key, deque) -> failures.computeIfPresent(key, (k, d) -> {
            prune(d, now);
            return d.isEmpty() ? null : d;
        }));
    }

    private int add(String key, Instant now) {
        int[] size = {0};
        failures.compute(key, (k, d) -> {
            Deque<Instant> result = d == null ? new ArrayDeque<>() : d;
            prune(result, now);
            result.addLast(now);
            size[0] = result.size();
            return result;
        });
        return size[0];
    }

    private Duration waitFor(String key, int max, Instant now) {
        Duration[] wait = {Duration.ZERO};
        failures.computeIfPresent(key, (k, d) -> {
            prune(d, now);
            if (d.size() >= max) {
                // Se libera cuando sale de la ventana el fallo que dejaría la cuenta por debajo del límite.
                Instant release = d.stream().skip(d.size() - max).findFirst().orElse(now).plus(properties.window());
                wait[0] = Duration.between(now, release);
            }
            return d.isEmpty() ? null : d;
        });
        return wait[0].isNegative() ? Duration.ZERO : wait[0];
    }

    private void prune(Deque<Instant> deque, Instant now) {
        Instant limit = now.minus(properties.window());
        while (!deque.isEmpty() && !deque.peekFirst().isAfter(limit)) {
            deque.pollFirst();
        }
    }

    private static Duration max(Duration a, Duration b) {
        return a.compareTo(b) >= 0 ? a : b;
    }

    private static String userKey(String accountKey) {
        return "u:" + accountKey;
    }

    private static String ipKey(String ip) {
        return "ip:" + (ip == null ? "desconocida" : ip);
    }
}
