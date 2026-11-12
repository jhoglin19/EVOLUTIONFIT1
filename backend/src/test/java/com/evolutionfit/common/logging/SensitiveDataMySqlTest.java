package com.evolutionfit.common.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

/** Con el mensaje real de MySQL 8.4: la cédula repetida no llega al log. Datos ficticios. */
@Testcontainers(disabledWithoutDocker = true)
class SensitiveDataMySqlTest {

    @Container
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    @Test
    void duplicateDocumentMessageIsMasked() throws SQLException {
        Flyway.configure().dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration").load().migrate();
        try (Connection c = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
             Statement s = c.createStatement()) {
            String insert = "insert into clients(document_type,document_number,first_name,last_name,phone) "
                    + "values ('CC','1012345678','Ana','Prueba','3001234567')";
            s.execute(insert);
            Throwable error = catchThrowable(() -> s.execute(insert));

            assertThat(error.getMessage()).contains("1012345678"); // MySQL sí lo trae
            assertThat(SensitiveData.mask(error.getMessage()))
                    .doesNotContain("1012345678")
                    .contains("uq_clients_document_number");
        }
    }
}
