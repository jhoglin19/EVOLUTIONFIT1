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
 * Aplica V1–V5 con la caja ya iniciada y un cierre guardado (como el del domingo con la configuración
 * anterior) y aplica V6: lunes a sábado, festivos cerrados, y el cierre guardado no cambia.
 */
@Testcontainers(disabledWithoutDocker = true)
class V6MigrationTest {

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
    void v6SetsMondayToSaturdayClosesHolidaysAndKeepsSavedClosings() throws SQLException {
        flyway("5").migrate();
        try (Connection c = connection(); Statement s = c.createStatement()) {
            s.execute("update cash_settings set start_date = '2026-11-15'");
            s.execute("""
                    insert into cash_closings(cash_date,period_start,period_end,window_start,window_end,status,closing_mode,
                                              was_open_day,has_cash_activity,opening_base,cash_income,cash_voided,
                                              cash_withdrawals,expected_cash)
                    values ('2026-11-15','2026-11-15','2026-11-15','2026-11-14 22:00:00','2026-11-15 22:00:00',
                            'NO_ACTIVITY','ON_TIME',1,0,50000,0,0,0,50000)""");
        }

        flyway(null).migrate();

        try (Connection c = connection(); Statement s = c.createStatement()) {
            ResultSet settings = s.executeQuery("select open_days, closed_on_holidays, start_date, opening_base from cash_settings");
            settings.next();
            assertThat(settings.getString(1)).isEqualTo("MON,TUE,WED,THU,FRI,SAT");
            assertThat(settings.getBoolean(2)).isTrue();
            assertThat(settings.getString(3)).isEqualTo("2026-11-15");
            assertThat(settings.getBigDecimal(4)).isEqualByComparingTo("50000");

            ResultSet closing = s.executeQuery("select was_open_day, status, expected_cash from cash_closings");
            closing.next();
            assertThat(closing.getBoolean(1)).isTrue(); // el domingo ya cerrado queda como se guardó
            assertThat(closing.getString(2)).isEqualTo("NO_ACTIVITY");
            assertThat(closing.getBigDecimal(3)).isEqualByComparingTo("50000");

            ResultSet overrides = s.executeQuery("select count(*) from gym_calendar_overrides");
            overrides.next();
            assertThat(overrides.getLong(1)).isZero();
        }
    }

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
    }
}
