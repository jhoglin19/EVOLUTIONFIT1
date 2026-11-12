package com.evolutionfit.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

/**
 * Si la conexión NO viene del proxy de confianza (en producción: cualquier cosa fuera de la red interna de Docker),
 * X-Forwarded-For se ignora: cambiarlo en cada intento no evita el bloqueo por IP.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "JWT_SECRET=clave-ficticia-solo-para-pruebas-de-ip-0123456789",
        "BOOTSTRAP_ADMIN_PASSWORD=",
        "app.cash.scheduler-enabled=false",
        "server.forward-headers-strategy=native",
        "server.tomcat.remoteip.internal-proxies=172\\.28\\.10\\.\\d+"
})
class UntrustedProxyClientIpMySqlTest extends ClientIpMySqlTestSupport {

    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    @Test
    void aForgedForwardedForDoesNotEscapeTheIpLimit() throws Exception {
        user(811, "ip_cuenta_f", "Correcta-F-123");
        user(812, "ip_cuenta_g", "Correcta-G-123");

        for (int i = 0; i < 5; i++) {
            assertThat(login("ip_cuenta_f", "Errada-" + i, "198.51.100." + (10 + i))).isEqualTo(401);
        }
        assertThat(login("ip_cuenta_g", "Correcta-G-123", "198.51.100.99")).isEqualTo(429);
    }
}
