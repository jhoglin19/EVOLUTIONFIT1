package com.evolutionfit.modules.cash;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.evolutionfit.modules.cash.CashLedger.PaymentRow;
import com.evolutionfit.modules.cash.CashLedger.WithdrawalRow;
import com.evolutionfit.modules.cash.CashSummary.MethodTotal;
import com.evolutionfit.modules.cash.dto.MonthlyReportResponse;
import com.evolutionfit.modules.payments.PaymentMethod;
import com.evolutionfit.modules.payments.PaymentStatus;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Lecturas y marcas de caja en SQL.
 * Regla central: un cierre solo marca filas sin marcar. Así cada pago, anulación y retiro se cuenta una
 * sola vez, aunque se haya guardado justo en el corte, y un día cerrado nunca cambia.
 * Solo cuenta lo que pasó desde el inicio de la caja (cashStart): lo anterior queda fuera.
 */
@Repository
public class CashLedgerRepository {

    private static final String PAYMENTS = """
            select p.id, p.payment_method, p.amount, p.paid_at, p.status, p.voided_at,
                   concat(c.first_name, ' ', c.last_name) client_name, r.receipt_number
              from payments p
              join memberships m on m.id = p.membership_id
              join clients c on c.id = m.client_id
              left join payment_receipts r on r.payment_id = p.id
             where %s
             order by %s, p.id""";

    private static final String WITHDRAWALS = """
            select w.id, w.amount, w.category, w.description, w.occurred_at, w.voided_at, w.void_reason,
                   concat(u.first_name, ' ', u.last_name) user_name
              from cash_movements w
              left join users u on u.id = w.user_id
             where w.movement_type = 'EXPENSE' and %s
             order by w.occurred_at, w.id""";

    /** Pagos que cuentan como ingreso: los anulados también (su anulación se descuenta aparte). */
    private static final String RECEIVED = "p.status in ('COMPLETED','VOIDED')";

    private final JdbcTemplate jdbc;

    public CashLedgerRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ---- marcas del cierre ----

    /** Marca con el cierre todo lo que no tiene cierre y ocurrió entre el inicio de la caja y el corte. */
    public void stamp(Long closingId, LocalDateTime cashStart, LocalDateTime cutoff) {
        jdbc.update("update payments p set p.cash_closing_id = ? where p.cash_closing_id is null and " + RECEIVED
                + " and p.paid_at >= ? and p.paid_at < ?", closingId, ts(cashStart), ts(cutoff));
        jdbc.update("update payments p set p.void_cash_closing_id = ? where p.void_cash_closing_id is null"
                + " and p.status = 'VOIDED' and p.paid_at >= ? and p.voided_at < ?", closingId, ts(cashStart), ts(cutoff));
        jdbc.update("update cash_movements w set w.cash_closing_id = ? where w.cash_closing_id is null"
                + " and w.movement_type = 'EXPENSE' and w.occurred_at >= ? and w.occurred_at < ?",
                closingId, ts(cashStart), ts(cutoff));
    }

    public void insertTotals(Long closingId, List<MethodTotal> totals) {
        for (MethodTotal t : totals) {
            jdbc.update("""
                    insert into cash_closing_totals(cash_closing_id, payment_method, received_count, received_amount,
                                                    voided_count, voided_amount) values (?,?,?,?,?,?)""",
                    closingId, t.method().name(), t.receivedCount(), t.receivedAmount(), t.voidedCount(), t.voidedAmount());
        }
    }

    public List<MethodTotal> totals(List<Long> closingIds) {
        if (closingIds.isEmpty()) {
            return List.of();
        }
        String in = String.join(",", closingIds.stream().map(id -> "?").toList());
        return jdbc.query("select payment_method, sum(received_count) rc, sum(received_amount) ra, sum(voided_count) vc,"
                        + " sum(voided_amount) va from cash_closing_totals where cash_closing_id in (" + in + ")"
                        + " group by payment_method",
                (rs, i) -> new MethodTotal(PaymentMethod.valueOf(rs.getString(1)), rs.getInt(2), rs.getBigDecimal(3),
                        rs.getInt(4), rs.getBigDecimal(5)), closingIds.toArray());
    }

    // ---- lecturas ----

    /** Lo que contó un cierre. */
    public CashLedger forClosing(Long closingId) {
        return new CashLedger(
                payments("p.cash_closing_id = ?", "p.paid_at", closingId),
                payments("p.void_cash_closing_id = ?", "p.voided_at", closingId),
                withdrawals("w.cash_closing_id = ?", closingId));
    }

    /** Lo que todavía no tiene cierre: el día en curso. */
    public CashLedger open(LocalDateTime cashStart) {
        return new CashLedger(
                payments("p.cash_closing_id is null and " + RECEIVED + " and p.paid_at >= ?", "p.paid_at", ts(cashStart)),
                payments("p.void_cash_closing_id is null and p.status = 'VOIDED' and p.paid_at >= ?", "p.voided_at",
                        ts(cashStart)),
                withdrawals("w.cash_closing_id is null and w.occurred_at >= ?", ts(cashStart)));
    }

    /**
     * Lo que se contó (o se va a contar) en días posteriores a cashDate: lo que entró o salió de la caja
     * después de su corte. Cada consulta es una sola sentencia, así no cuenta dos veces una fila que un
     * cierre esté marcando en ese momento.
     */
    public CashLedger after(LocalDate cashDate, LocalDateTime cashStart) {
        String later = "(select id from cash_closings where cash_date > ?)";
        return new CashLedger(
                payments("((p.cash_closing_id is null and " + RECEIVED + " and p.paid_at >= ?) or p.cash_closing_id in "
                        + later + ")", "p.paid_at", ts(cashStart), cashDate),
                payments("((p.void_cash_closing_id is null and p.status = 'VOIDED' and p.paid_at >= ?)"
                        + " or p.void_cash_closing_id in " + later + ")", "p.voided_at", ts(cashStart), cashDate),
                withdrawals("((w.cash_closing_id is null and w.occurred_at >= ?) or w.cash_closing_id in " + later + ")",
                        ts(cashStart), cashDate));
    }

    /** Retiros (activos o anulados) en un rango de fechas, para el listado del ADMIN. */
    public List<WithdrawalRow> withdrawalsBetween(LocalDateTime from, LocalDateTime to, WithdrawalCategory category) {
        if (category == null) {
            return withdrawals("w.occurred_at >= ? and w.occurred_at < ?", ts(from), ts(to));
        }
        return withdrawals("w.occurred_at >= ? and w.occurred_at < ? and w.category = ?", ts(from), ts(to), category.name());
    }

    public String userName(Long userId) {
        if (userId == null) {
            return null;
        }
        List<String> names = jdbc.queryForList("select concat(first_name, ' ', last_name) from users where id = ?",
                String.class, userId);
        return names.isEmpty() ? null : names.get(0);
    }

    /** Retiros activos por categoría de los cierres dados (reporte mensual). */
    public List<MonthlyReportResponse.CategoryTotal> withdrawalsByCategory(List<Long> closingIds) {
        if (closingIds.isEmpty()) {
            return List.of();
        }
        String in = String.join(",", closingIds.stream().map(id -> "?").toList());
        return jdbc.query("select coalesce(category,'OTHER') cat, count(*) n, sum(amount) total from cash_movements"
                        + " where movement_type = 'EXPENSE' and voided_at is null and cash_closing_id in (" + in + ")"
                        + " group by coalesce(category,'OTHER') order by total desc",
                (rs, i) -> {
                    WithdrawalCategory c = WithdrawalCategory.valueOf(rs.getString(1));
                    return new MonthlyReportResponse.CategoryTotal(c, c.label(), rs.getInt(2), rs.getBigDecimal(3));
                }, closingIds.toArray());
    }

    private List<PaymentRow> payments(String where, String orderBy, Object... args) {
        return jdbc.query(String.format(PAYMENTS, where, orderBy), CashLedgerRepository::paymentRow, args);
    }

    private List<WithdrawalRow> withdrawals(String where, Object... args) {
        return jdbc.query(String.format(WITHDRAWALS, where), CashLedgerRepository::withdrawalRow, args);
    }

    private static PaymentRow paymentRow(ResultSet rs, int i) throws SQLException {
        return new PaymentRow(rs.getLong("id"), PaymentMethod.valueOf(rs.getString("payment_method")),
                rs.getBigDecimal("amount"), local(rs.getTimestamp("paid_at")),
                PaymentStatus.valueOf(rs.getString("status")), local(rs.getTimestamp("voided_at")),
                rs.getString("client_name"), rs.getString("receipt_number"));
    }

    private static WithdrawalRow withdrawalRow(ResultSet rs, int i) throws SQLException {
        String category = rs.getString("category");
        return new WithdrawalRow(rs.getLong("id"), rs.getBigDecimal("amount"),
                category == null ? WithdrawalCategory.OTHER : WithdrawalCategory.valueOf(category),
                rs.getString("description"), local(rs.getTimestamp("occurred_at")), rs.getString("user_name"),
                local(rs.getTimestamp("voided_at")), rs.getString("void_reason"));
    }

    private static Timestamp ts(LocalDateTime value) {
        return Timestamp.valueOf(value);
    }

    private static LocalDateTime local(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }
}
