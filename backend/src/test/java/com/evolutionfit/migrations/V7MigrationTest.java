package com.evolutionfit.migrations;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** V7 solo agrega índices: las filas existentes quedan iguales. Datos ficticios. */
@Testcontainers(disabledWithoutDocker = true)
class V7MigrationTest {

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
    void v7AddsTheDateIndexesAndKeepsTheData() throws SQLException {
        flyway("6").migrate();
        try (Connection c = connection(); Statement s = c.createStatement()) {
            s.execute("insert into clients(id,document_type,document_number,first_name,last_name) values (1,'CC','900001','Ana','Prueba')");
            s.execute("insert into plans(id,name,duration_days,price) values (1,'Mensual',30,90000)");
            s.execute("""
                    insert into memberships(id,client_id,plan_id,plan_name,plan_duration_days,start_date,end_date,base_end_date,
                                            status,price_at_purchase,activated_at)
                    values (1,1,1,'Mensual',30,'2026-11-01','2026-11-30','2026-11-30','ACTIVE',90000,'2026-11-01 09:00:00')""");
            s.execute("""
                    insert into payments(id,membership_id,amount,list_price,discount_amount,payment_method,status,paid_at,
                                         voided_at,void_reason)
                    values (1,1,90000,90000,0,'CASH','VOIDED','2026-11-01 09:00:00','2026-11-02 10:00:00','Error')""");
        }

        flyway(null).migrate();

        try (Connection c = connection(); Statement s = c.createStatement()) {
            assertThat(indexes(s, "payments")).contains("idx_payments_voided_at");
            assertThat(indexes(s, "memberships")).contains("idx_memberships_activated_at", "idx_memberships_created_at");
            ResultSet p = s.executeQuery("select status, amount, voided_at from payments where id = 1");
            p.next();
            assertThat(p.getString(1)).isEqualTo("VOIDED");
            assertThat(p.getBigDecimal(2)).isEqualByComparingTo("90000");
            assertThat(p.getString(3)).startsWith("2026-11-02 10:00:00");
            ResultSet m = s.executeQuery("select count(*) from memberships");
            m.next();
            assertThat(m.getLong(1)).isEqualTo(1);
        }
    }

    private static List<String> indexes(Statement s, String table) throws SQLException {
        List<String> names = new ArrayList<>();
        ResultSet rs = s.executeQuery("show index from " + table);
        while (rs.next()) {
            names.add(rs.getString("Key_name"));
        }
        return names;
    }

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
    }
}
