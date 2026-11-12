package com.evolutionfit.migrations;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Aplica V1 y V2 en una MySQL 8.4 vacía, inserta datos como los que ya existen en la base real y luego
 * aplica V3: comprueba que rellena el plan y el vencimiento base, y que suma los días congelados.
 */
@Testcontainers(disabledWithoutDocker = true)
class V3MigrationTest {

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
    void v3BackfillsThePlanAndAddsFrozenDaysToTheExpiry() throws SQLException {
        flyway("2").migrate();

        try (Connection c = connection(); Statement s = c.createStatement()) {
            s.execute("insert into clients(id,document_type,document_number,first_name,last_name) values (1,'CC','1','Ana','Pérez')");
            s.execute("insert into plans(id,name,duration_days,price) values (2,'Mensual',30,90000)");
            // 1: congelada 5 días (08 al 12) que no se habían sumado, como la membresía de prueba actual.
            s.execute("""
                    insert into memberships(id,client_id,plan_id,start_date,end_date,status,price_at_purchase,notes)
                    values (1,1,2,'2026-11-08','2026-12-07','FROZEN',90000,'Membresía mensual de prueba')""");
            s.execute("""
                    insert into membership_freezes(membership_id,start_date,end_date,applied_days,reason,status)
                    values (1,'2026-11-08','2026-11-12',0,'Prueba de congelacion por 5 dias','ACTIVE')""");
            s.execute("insert into payments(membership_id,amount,payment_method,reference) values (1,90000,'CASH','PAGO-TEST-001')");
            // 2: congelación antigua sin fin ni motivo. 3: con una congelación cancelada que aplicó 2 días.
            s.execute("""
                    insert into memberships(id,client_id,plan_id,start_date,end_date,status,price_at_purchase)
                    values (2,1,2,'2026-09-01','2026-09-30','EXPIRED',80000)""");
            s.execute("""
                    insert into memberships(id,client_id,plan_id,start_date,end_date,status,price_at_purchase)
                    values (3,1,2,'2026-10-01','2026-10-30','EXPIRED',85000)""");
            s.execute("""
                    insert into membership_freezes(membership_id,start_date,end_date,applied_days,reason,status)
                    values (3,'2026-10-10','2026-10-19',2,'Cancelada','CANCELLED')""");
            // Congelación sin fin ni motivo (V1 lo permitía) con 3 días aplicados.
            s.execute("""
                    insert into membership_freezes(membership_id,start_date,end_date,applied_days,reason,status)
                    values (2,'2026-09-05',null,3,null,'COMPLETED')""");
            // 4: anulada antes de V3, sin motivo (V1 no tenía la columna).
            s.execute("""
                    insert into memberships(id,client_id,plan_id,start_date,end_date,status,price_at_purchase)
                    values (4,1,2,'2026-08-01','2026-08-30','CANCELLED',90000)""");
        }
        flyway(null).migrate();

        try (Connection c = connection(); Statement s = c.createStatement()) {
            ResultSet m1 = s.executeQuery("select * from memberships where id = 1");
            m1.next();
            assertThat(m1.getString("plan_name")).isEqualTo("Mensual");
            assertThat(m1.getInt("plan_duration_days")).isEqualTo(30);
            assertThat(m1.getObject("base_end_date", LocalDate.class)).isEqualTo(LocalDate.of(2026, 12, 7));
            assertThat(m1.getObject("end_date", LocalDate.class)).isEqualTo(LocalDate.of(2026, 12, 12));
            assertThat(m1.getBigDecimal("price_at_purchase")).isEqualByComparingTo("90000");
            assertThat(m1.getBoolean("is_migrated")).isFalse();

            ResultSet f1 = s.executeQuery("select applied_days from membership_freezes where membership_id = 1");
            f1.next();
            assertThat(f1.getInt(1)).isEqualTo(5);

            ResultSet m2 = s.executeQuery("select base_end_date, end_date from memberships where id = 2");
            m2.next();
            assertThat(m2.getObject(1, LocalDate.class)).isEqualTo(LocalDate.of(2026, 9, 30));
            assertThat(m2.getObject(2, LocalDate.class)).isEqualTo(LocalDate.of(2026, 10, 3));

            ResultSet f2 = s.executeQuery("select end_date, reason, applied_days from membership_freezes where membership_id = 2");
            f2.next();
            assertThat(f2.getObject(1, LocalDate.class)).isEqualTo(LocalDate.of(2026, 9, 7));
            assertThat(f2.getString(2)).isEqualTo("Sin motivo registrado (antes de V3)");
            assertThat(f2.getInt(3)).isEqualTo(3);

            ResultSet m3 = s.executeQuery("select end_date from memberships where id = 3");
            m3.next();
            assertThat(m3.getObject(1, LocalDate.class)).isEqualTo(LocalDate.of(2026, 11, 1));

            ResultSet cancelled = s.executeQuery("""
                    select (select cancellation_reason from memberships where id = 4),
                           (select cancellation_reason from membership_freezes where membership_id = 3)""");
            cancelled.next();
            assertThat(cancelled.getString(1)).isEqualTo("Anulada antes de V3 (sin motivo registrado)");
            assertThat(cancelled.getString(2)).isEqualTo("Cancelada antes de V3 (sin motivo registrado)");

            ResultSet counts = s.executeQuery("""
                    select (select count(*) from memberships), (select count(*) from membership_freezes),
                           (select count(*) from payments), (select count(*) from clients)""");
            counts.next();
            assertThat(new int[] {counts.getInt(1), counts.getInt(2), counts.getInt(3), counts.getInt(4)})
                    .containsExactly(4, 3, 1, 1);
        }
    }

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
    }
}
