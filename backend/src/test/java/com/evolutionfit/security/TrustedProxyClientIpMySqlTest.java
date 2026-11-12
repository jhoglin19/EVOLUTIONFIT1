package com.evolutionfit.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

/**
 * Detrás del proxy de confianza (aquí 127.0.0.1 hace de Caddy) la IP del cliente sale de X-Forwarded-For: dos
 * IPs distintas tienen contadores de bloqueo distintos. El cliente B usa otra cuenta.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "JWT_SECRET=clave-ficticia-solo-para-pruebas-de-ip-0123456789",
        "BOOTSTRAP_ADMIN_PASSWORD=",
        "app.cash.scheduler-enabled=false",
        "server.forward-headers-strategy=native",
        "server.tomcat.remoteip.internal-proxies=127\\.0\\.0\\.1"
})
class TrustedProxyClientIpMySqlTest extends ClientIpMySqlTestSupport {

    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    private static final String IP_A = "203.0.113.10";
    private static final String IP_B = "203.0.113.20";

    @Test
    void twoClientIpsHaveSeparateCounters() throws Exception {
        user(801, "ip_cuenta_a", "Correcta-A-123");
        user(802, "ip_cuenta_b", "Correcta-B-123");
        user(803, "ip_cuenta_c", "Correcta-C-123");

        for (int i = 0; i < 5; i++) {
            assertThat(login("ip_cuenta_a", "Errada-" + i, IP_A)).isEqualTo(401);
        }
        // La IP A quedó bloqueada: ni otra cuenta con su contraseña correcta entra desde ella.
        assertThat(login("ip_cuenta_c", "Correcta-C-123", IP_A)).isEqualTo(429);
        // La IP B tiene su propio contador: el cliente B entra con su cuenta.
        assertThat(login("ip_cuenta_b", "Correcta-B-123", IP_B)).isEqualTo(200);
        // Y la cuenta A sigue bloqueada desde cualquier IP (límite por cuenta).
        assertThat(login("ip_cuenta_a", "Correcta-A-123", IP_B)).isEqualTo(429);
    }

    @Test
    void theRightmostAddressIsTheOneTheProxyAdded() throws Exception {
        user(804, "ip_cuenta_d", "Correcta-D-123");
        user(805, "ip_cuenta_e", "Correcta-E-123");

        // Lo que el cliente inventa va a la izquierda; Caddy agrega (o reescribe) la IP real a la derecha.
        for (int i = 0; i < 5; i++) {
            assertThat(login("ip_cuenta_d", "Errada-" + i, "198.51.100." + i + ", 203.0.113.30")).isEqualTo(401);
        }
        assertThat(login("ip_cuenta_e", "Correcta-E-123", "203.0.113.30")).isEqualTo(429);
    }
}
