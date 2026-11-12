package com.evolutionfit.modules.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

/**
 * Inicio de sesión de punta a punta contra MySQL 8.4 real, con sus transacciones (las pruebas unitarias simulan
 * la búsqueda del usuario). Un identificador que no existe respondía 500: la búsqueda transaccional dejaba la
 * transacción marcada para deshacerse y el login intentaba confirmarla. Datos ficticios.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "JWT_SECRET=clave-ficticia-solo-para-pruebas-de-login-0123456789",
        "BOOTSTRAP_ADMIN_PASSWORD=",
        "app.cash.scheduler-enabled=false"
})
@AutoConfigureMockMvc
class LoginMySqlTest {

    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PasswordEncoder encoder;

    private ResultActions login(String identifier, String password, String ip) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"identifier\":\"" + identifier + "\",\"password\":\"" + password + "\"}")
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                }));
    }

    private void user(long id, String username, String password) {
        jdbc.update("insert ignore into roles(id,name) values (1,'ADMIN'),(2,'RECEPTIONIST')");
        jdbc.update("insert into users(id,role_id,username,password_hash,first_name,last_name) values (?,2,?,?,'Ana','Prueba')",
                id, username, encoder.encode(password));
    }

    @Test
    void unknownIdentifierAnswers401NotA500() throws Exception {
        login("no_existe_nadie", "Cualquiera123", "10.1.0.1")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Usuario o contraseña incorrectos"));
    }

    @Test
    void wrongPasswordAnswers401AndTheRightOneEnters() throws Exception {
        user(901, "login_mysql_a", "Correcta-123");

        login("login_mysql_a", "Errada-123", "10.1.0.2").andExpect(status().isUnauthorized());
        login("login_mysql_a", "Correcta-123", "10.1.0.2").andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void fiveFailuresWithAnUnknownIdentifierBlockTheSixth() throws Exception {
        for (int i = 0; i < 5; i++) {
            login("tampoco_existe", "Errada-" + i, "10.1.0.3").andExpect(status().isUnauthorized());
        }
        login("tampoco_existe", "Errada-6", "10.1.0.3").andExpect(status().isTooManyRequests());
    }
}
