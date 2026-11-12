package com.evolutionfit.security;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Base de las pruebas de IP del cliente: Tomcat real en un puerto aleatorio (con su RemoteIpValve), MySQL real y
 * peticiones HTTP de verdad, como las que hace Caddy. Datos ficticios.
 */
abstract class ClientIpMySqlTestSupport {

    private final HttpClient http = HttpClient.newHttpClient();

    @Autowired
    private Environment environment;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PasswordEncoder encoder;

    /** POST /api/auth/login con el X-Forwarded-For indicado (null = sin cabecera). Devuelve el código HTTP. */
    int login(String username, String password, String forwardedFor) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + environment.getProperty("local.server.port") + "/api/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"identifier\":\"" + username + "\",\"password\":\"" + password + "\"}"));
        if (forwardedFor != null) {
            request.header("X-Forwarded-For", forwardedFor);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    void user(long id, String username, String password) {
        jdbc.update("insert ignore into roles(id,name) values (1,'ADMIN'),(2,'RECEPTIONIST')");
        jdbc.update("insert into users(id,role_id,username,password_hash,first_name,last_name) values (?,2,?,?,'Ana','Prueba')",
                id, username, encoder.encode(password));
    }
}
