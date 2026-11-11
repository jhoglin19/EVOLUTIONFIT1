package com.evolutionfit.migrations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Aplica V1–V4 en una MySQL 8.4 vacía, inserta datos como los de la base real (el pago de prueba en efectivo
 * con su movimiento INCOME y pagos de la fase 5) y aplica V5: no cambia ninguna fila, la caja queda sin
 * fecha de inicio y las restricciones nuevas rechazan cierres y retiros inconsistentes.
 */
@Testcontainers(disabledWithoutDocker = true)
class V5MigrationTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

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
    void v5KeepsPaymentsAndTheTestMovementUntouchedAndStartsTheCashWithoutAStartDate() throws SQLException {
        flyway("4").migrate();

        try (Connection c = connection(); Statement s = c.createStatement()) {
            s.execute("insert into clients(id,document_type,document_number,first_name,last_name) values (1,'CC','1','Ana','Pérez')");
            s.execute("insert into plans(id,name,duration_days,price) values (2,'Mensual',30,90000)");
            s.execute("""
                    insert into memberships(id,client_id,plan_id,plan_name,plan_duration_days,start_date,end_date,
                                            base_end_date,status,price_at_purchase)
                    values (1,1,2,'Mensual',30,'2026-11-08','2026-12-07','2026-12-07','ACTIVE',90000),
                           (2,1,2,'Mensual',30,'2026-12-08','2027-01-06','2027-01-06','SCHEDULED',90000)""");
            s.execute("""
                    insert into payments(id,membership_id,amount,list_price,payment_method,status,paid_at)
                    values (1,1,90000,90000,'CASH','COMPLETED','2026-11-08 23:07:12'),
                           (2,2,90000,90000,'NEQUI','COMPLETED','2026-11-10 20:02:16')""");
            s.execute("""
                    insert into cash_movements(id,payment_id,movement_type,amount,description,occurred_at)
                    values (1,1,'INCOME',90000,'Pago mensual de prueba','2026-11-08 23:07:22')""");
        }

        flyway("5").migrate(); // solo V5: V6 cambia los días de atención (lo verifica V6MigrationTest)

        try (Connection c = connection(); Statement s = c.createStatement()) {
            ResultSet m = s.executeQuery("select * from cash_movements where id = 1");
            m.next();
            assertThat(m.getLong("payment_id")).isEqualTo(1L);
            assertThat(m.getString("movement_type")).isEqualTo("INCOME");
            assertThat(m.getBigDecimal("amount")).isEqualByComparingTo("90000");
            assertThat(m.getString("description")).isEqualTo("Pago mensual de prueba");
            assertThat(m.getObject("cash_closing_id")).isNull();
            assertThat(m.getObject("category")).isNull();

            ResultSet p = s.executeQuery("select count(*), sum(cash_closing_id is null and void_cash_closing_id is null),"
                    + " sum(amount) from payments");
            p.next();
            assertThat(p.getLong(1)).isEqualTo(2);
            assertThat(p.getLong(2)).isEqualTo(2);
            assertThat(p.getBigDecimal(3)).isEqualByComparingTo("180000");

            ResultSet settings = s.executeQuery("select * from cash_settings");
            settings.next();
            assertThat(settings.getInt("id")).isEqualTo(1);
            assertThat(settings.getBigDecimal("opening_base")).isEqualByComparingTo("50000");
            assertThat(settings.getObject("start_date")).isNull();
            assertThat(settings.getString("open_days")).isEqualTo("MON,TUE,WED,THU,FRI,SAT,SUN");
            assertThat(settings.next()).isFalse();

            ResultSet counts = s.executeQuery("""
                    select (select count(*) from cash_closings), (select count(*) from cash_closing_totals),
                           (select count(*) from clients), (select count(*) from memberships),
                           (select max(version) from flyway_schema_history where success)""");
            counts.next();
            assertThat(counts.getLong(1)).isZero();
            assertThat(counts.getLong(2)).isZero();
            assertThat(counts.getLong(3)).isEqualTo(1);
            assertThat(counts.getLong(4)).isEqualTo(2);
            assertThat(counts.getString(5)).isEqualTo("5");

            // Un cierre consistente entra; uno con el esperado mal calculado o con diferencia mal hecha no.
            s.execute(closing("2026-11-10", "50000", "90000", "0", "0", "140000"));
            assertThatThrownBy(() -> s.execute(closing("2026-11-11", "50000", "90000", "0", "0", "150000")))
                    .hasMessageContaining("chk_closings_expected");
            assertThatThrownBy(() -> s.execute(closing("2026-11-10", "50000", "0", "0", "0", "50000")))
                    .hasMessageContaining("Duplicate");
            assertThatThrownBy(() -> s.execute("""
                    update cash_closings set status = 'COUNTED', counted_cash = 130000, post_cut_income = 0,
                           post_cut_outflow = 0, adjusted_expected_cash = 140000, difference = -10000, counted_at = now()
                     where cash_date = '2026-11-10'""")).hasMessageContaining("chk_closings_review");
            assertThatThrownBy(() -> s.execute("""
                    insert into cash_movements(movement_type,amount,description) values ('EXPENSE',1000,'Sin categoría')"""))
                    .hasMessageContaining("chk_movements_expense_category");
            assertThatThrownBy(() -> s.execute("update payments set void_cash_closing_id = "
                    + "(select id from (select id from cash_closings) x) where id = 2"))
                    .hasMessageContaining("chk_payments_void_cash_closing");
        }
    }

    private static String closing(String day, String base, String income, String voided, String withdrawals,
                                  String expected) {
        return String.format("""
                insert into cash_closings(cash_date,period_start,period_end,window_start,window_end,status,closing_mode,
                                          was_open_day,has_cash_activity,opening_base,cash_income,cash_voided,
                                          cash_withdrawals,expected_cash)
                values ('%1$s','%1$s','%1$s', date_sub('%1$s 22:00:00', interval 1 day), '%1$s 22:00:00', 'PENDING_COUNT',
                        'ON_TIME', 1, 1, %2$s, %3$s, %4$s, %5$s, %6$s)""", day, base, income, voided, withdrawals, expected);
    }

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
    }
}
