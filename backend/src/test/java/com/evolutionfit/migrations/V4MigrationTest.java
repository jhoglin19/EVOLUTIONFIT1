package com.evolutionfit.migrations;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Aplica V1–V3 en una MySQL 8.4 vacía, inserta pagos como los que ya existen en la base real y aplica V4:
 * los pagos quedan con precio de lista y sin descuento, sin comprobante, y la configuración queda vacía.
 */
@Testcontainers(disabledWithoutDocker = true)
class V4MigrationTest {

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
    void v4KeepsExistingPaymentsWithoutReceiptAndCreatesEmptySettings() throws SQLException {
        flyway("3").migrate();

        try (Connection c = connection(); Statement s = c.createStatement()) {
            s.execute("insert into clients(id,document_type,document_number,first_name,last_name) values (1,'CC','1','Ana','Pérez')");
            s.execute("insert into plans(id,name,duration_days,price) values (2,'Mensual',30,90000)");
            s.execute("""
                    insert into memberships(id,client_id,plan_id,plan_name,plan_duration_days,start_date,end_date,
                                            base_end_date,status,price_at_purchase)
                    values (1,1,2,'Mensual',30,'2026-11-08','2026-12-12','2026-12-07','FROZEN',90000),
                           (2,1,2,'Mensual',30,'2026-10-01','2026-10-30','2026-10-30','EXPIRED',90000)""");
            // Como el pago real anterior a la fase 5, con su movimiento de caja.
            s.execute("insert into payments(id,membership_id,amount,payment_method,status,reference) values (1,1,90000,'CASH','COMPLETED','PAGO-TEST-001')");
            s.execute("insert into cash_movements(payment_id,movement_type,amount,description) values (1,'INCOME',90000,'Pago de prueba')");
            // Un pago anulado antes de V4, sin motivo (V1 no tenía la columna).
            s.execute("insert into payments(id,membership_id,amount,payment_method,status) values (2,2,80000,'NEQUI','VOIDED')");
        }

        flyway(null).migrate();

        try (Connection c = connection(); Statement s = c.createStatement()) {
            ResultSet p1 = s.executeQuery("select * from payments where id = 1");
            p1.next();
            assertThat(p1.getBigDecimal("list_price")).isEqualByComparingTo("90000");
            assertThat(p1.getBigDecimal("discount_amount")).isEqualByComparingTo("0");
            assertThat(p1.getBigDecimal("amount")).isEqualByComparingTo("90000");
            assertThat(p1.getString("status")).isEqualTo("COMPLETED");
            assertThat(p1.getString("reference")).isEqualTo("PAGO-TEST-001");
            assertThat(p1.getString("request_id")).isNull();
            assertThat(p1.getLong("completed_membership_id")).isEqualTo(1L);

            ResultSet p2 = s.executeQuery("select void_reason, completed_membership_id from payments where id = 2");
            p2.next();
            assertThat(p2.getString(1)).isEqualTo("Anulado antes de V4 (sin motivo registrado)");
            assertThat(p2.getObject(2)).isNull();

            ResultSet settings = s.executeQuery("select * from business_settings");
            settings.next();
            assertThat(settings.getInt("id")).isEqualTo(1);
            for (String column : new String[] {"trade_name", "legal_name", "tax_id", "commercial_registration",
                    "address", "city", "phone", "email", "logo"}) {
                assertThat(settings.getObject(column)).as(column).isNull();
            }
            assertThat(settings.getString("receipt_prefix")).isEqualTo("EF-");
            assertThat(settings.next()).isFalse();

            ResultSet counts = s.executeQuery("""
                    select (select next_value from receipt_sequence), (select count(*) from payment_receipts),
                           (select count(*) from payments), (select count(*) from cash_movements),
                           (select count(*) from memberships where requested_start_date is null and activated_at is null)""");
            counts.next();
            assertThat(new long[] {counts.getLong(1), counts.getLong(2), counts.getLong(3), counts.getLong(4), counts.getLong(5)})
                    .containsExactly(1, 0, 2, 1, 2);
        }
    }

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
    }
}
