package com.evolutionfit.migrations;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

/** V8 agrega users.password_changed_at en NULL: los usuarios y sus contraseñas quedan iguales. Datos ficticios. */
@Testcontainers(disabledWithoutDocker = true)
class V8MigrationTest {

    @Container
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    private Flyway flyway(String target) {
        var config = Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration");
        if (target != null) {
            config.target(target);
        }
        return config.load();
    }

    @Test
    void v8AddsPasswordChangedAtAsNullAndKeepsTheUsers() throws SQLException {
        flyway("7").migrate();
        try (Connection c = connection(); Statement s = c.createStatement()) {
            s.execute("insert into roles(id,name,description) values (1,'ADMIN','Administrador')");
            s.execute("""
                    insert into users(id,role_id,username,email,password_hash,first_name,last_name,is_active,last_login_at)
                    values (1,1,'admin','admin@ejemplo.co','$2a$10$hashficticio',
                            'Ana','Prueba',1,'2026-11-10 08:00:00')""");
        }

        flyway(null).migrate();

        try (Connection c = connection(); Statement s = c.createStatement()) {
            ResultSet col = s.executeQuery("""
                    select is_nullable, data_type, datetime_precision from information_schema.columns
                    where table_schema = database() and table_name = 'users' and column_name = 'password_changed_at'""");
            assertThat(col.next()).isTrue();
            assertThat(col.getString(1)).isEqualTo("YES");
            assertThat(col.getString(2)).isEqualTo("datetime");
            assertThat(col.getInt(3)).isEqualTo(6);

            ResultSet u = s.executeQuery(
                    "select username, password_hash, is_active, last_login_at, password_changed_at from users where id = 1");
            u.next();
            assertThat(u.getString(1)).isEqualTo("admin");
            assertThat(u.getString(2)).isEqualTo("$2a$10$hashficticio");
            assertThat(u.getBoolean(3)).isTrue();
            assertThat(u.getString(4)).startsWith("2026-11-10 08:00:00");
            assertThat(u.getObject(5)).isNull();
        }
    }

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
    }
}
