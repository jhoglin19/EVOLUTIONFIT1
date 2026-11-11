package com.evolutionfit.modules.reports;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.evolutionfit.modules.cash.CashSummary.MethodTotal;
import com.evolutionfit.modules.payments.PaymentMethod;
import com.evolutionfit.modules.payments.PaymentStatus;
import com.evolutionfit.modules.reports.dto.DiscountsReportResponse;
import com.evolutionfit.modules.reports.dto.EnrollmentCounts;
import com.evolutionfit.modules.reports.dto.NewClientsReportResponse;
import com.evolutionfit.modules.reports.dto.PlanSalesReportResponse;
import com.evolutionfit.modules.reports.dto.VoidedPaymentsReportResponse;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Consultas de solo lectura del panel y los reportes. Agrupan y suman en SQL; los rangos se comparan
 * directamente contra la columna ([inicio, fin)) para usar sus índices (V7). Las fechas se guardan en hora
 * de Bogotá, así que DATE(columna) en el SELECT da el día de Bogotá.
 */
@Repository
public class ReportRepository {

    /** Membresía que cuenta: pagada o migrada, ni pendiente de pago ni anulada. */
    static final String COUNTS = "m.status not in ('PENDING','CANCELLED')";

    /** Cuándo empezó a contar: al activarla el pago o, si es migrada o anterior a la fase 5, al registrarla. */
    static final String EVENT_AT = "coalesce(m.activated_at, m.created_at)";

    /**
     * La membresía m es la primera del cliente (docs/REGLAS_NEGOCIO.md, sección 11): el cliente no tiene
     * ninguna migrada (de ningún estado) y ninguna otra que cuente empezó antes. Si es así, m es un cliente
     * nuevo; si no, m es una renovación.
     */
    static final String FIRST_OF_CLIENT = """
            not exists (select 1 from memberships mx where mx.client_id = m.client_id and mx.is_migrated = 1)
            and not exists (select 1 from memberships mp
                             where mp.client_id = m.client_id and mp.id <> m.id
                               and mp.status not in ('PENDING','CANCELLED')
                               and (coalesce(mp.activated_at, mp.created_at) < coalesce(m.activated_at, m.created_at)
                                    or (coalesce(mp.activated_at, mp.created_at) = coalesce(m.activated_at, m.created_at)
                                        and mp.id < m.id)))""";

    /** Pagos que cuentan como recibidos: los anulados también (su anulación se descuenta aparte), como en Caja. */
    private static final String RECEIVED = "p.status in ('COMPLETED','VOIDED')";

    private static final String CLIENT_NAME = "concat(c.first_name, ' ', c.last_name)";
    private static final String USER_NAME = "concat(u.first_name, ' ', u.last_name)";

    private final JdbcTemplate jdbc;

    public ReportRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ---- ingresos ----

    /** Un día y método de pago: lo recibido ese día o lo anulado ese día. */
    public record DayAmount(LocalDate day, PaymentMethod method, int count, BigDecimal amount) {
    }

    /** Recibido por día de pago y método. */
    public List<DayAmount> receivedByDay(DateRange range) {
        return jdbc.query("select date(p.paid_at) d, p.payment_method, count(*) n, sum(p.amount) total from payments p"
                        + " where " + RECEIVED + " and p.paid_at >= ? and p.paid_at < ?"
                        + " group by date(p.paid_at), p.payment_method order by d",
                ReportRepository::dayAmount, ts(range.start()), ts(range.endExclusive()));
    }

    /** Anulado por día de anulación y método. */
    public List<DayAmount> voidedByDay(DateRange range) {
        return jdbc.query("select date(p.voided_at) d, p.payment_method, count(*) n, sum(p.amount) total from payments p"
                        + " where p.status = 'VOIDED' and p.voided_at >= ? and p.voided_at < ?"
                        + " group by date(p.voided_at), p.payment_method order by d",
                ReportRepository::dayAmount, ts(range.start()), ts(range.endExclusive()));
    }

    /** Totales por método del rango (recibido por fecha de pago, anulado por fecha de anulación). */
    public List<MethodTotal> methodTotals(DateRange range) {
        return ReportCalculator.methodTotals(receivedByDay(range), voidedByDay(range));
    }

    // ---- inscripciones y clientes nuevos ----

    public EnrollmentCounts enrollments(DateRange range) {
        Object[] args = {ts(range.start()), ts(range.endExclusive())};
        long[] paid = jdbc.query("select coalesce(sum(case when " + FIRST_OF_CLIENT + " then 1 else 0 end), 0) new_clients,"
                        + " count(*) total from memberships m"
                        + " where " + COUNTS + " and m.activated_at >= ? and m.activated_at < ?",
                rs -> {
                    rs.next();
                    return new long[] {rs.getLong(1), rs.getLong(2)};
                }, args);
        long migrated = count("select count(*) from memberships m where m.is_migrated = 1 and m.status <> 'CANCELLED'"
                + " and m.created_at >= ? and m.created_at < ?", args);
        long pending = count("select count(*) from memberships m where m.status = 'PENDING'"
                + " and m.created_at >= ? and m.created_at < ?", args);
        return new EnrollmentCounts(paid[0], paid[1] - paid[0], migrated, pending);
    }

    /** Clientes cuya primera membresía (no migrada) empezó a contar en el rango. */
    public List<NewClientsReportResponse.Row> newClients(DateRange range) {
        return jdbc.query("select " + CLIENT_NAME + " client_name, c.id client_id, c.document_number, c.phone, c.is_active,"
                        + " m.id membership_id, m.plan_name, " + EVENT_AT + " at, p.amount"
                        + " from memberships m join clients c on c.id = m.client_id"
                        + " left join payments p on p.membership_id = m.id and p.status = 'COMPLETED'"
                        + " where " + COUNTS + " and m.is_migrated = 0"
                        + " and ((m.activated_at >= ? and m.activated_at < ?)"
                        + "      or (m.activated_at is null and m.created_at >= ? and m.created_at < ?))"
                        + " and " + FIRST_OF_CLIENT
                        + " order by at, m.id",
                (rs, i) -> clientRow(rs, NewClientsReportResponse.Type.NEW),
                ts(range.start()), ts(range.endExclusive()), ts(range.start()), ts(range.endExclusive()));
    }

    /** Clientes cuya primera membresía migrada se registró en el rango. */
    public List<NewClientsReportResponse.Row> migratedClients(DateRange range) {
        return jdbc.query("select " + CLIENT_NAME + " client_name, c.id client_id, c.document_number, c.phone, c.is_active,"
                        + " m.id membership_id, m.plan_name, m.created_at at, null amount"
                        + " from memberships m join clients c on c.id = m.client_id"
                        + " where m.is_migrated = 1 and m.created_at >= ? and m.created_at < ?"
                        + " and not exists (select 1 from memberships mx where mx.client_id = m.client_id"
                        + "   and mx.is_migrated = 1 and (mx.created_at < m.created_at"
                        + "        or (mx.created_at = m.created_at and mx.id < m.id)))"
                        + " order by at, m.id",
                (rs, i) -> clientRow(rs, NewClientsReportResponse.Type.MIGRATED),
                ts(range.start()), ts(range.endExclusive()));
    }

    // ---- planes ----

    public List<PlanSalesReportResponse.Row> planSales(DateRange range) {
        return jdbc.query("select m.plan_id,"
                        + " group_concat(distinct m.plan_name order by m.plan_name separator ' / ') names,"
                        + " count(*) sold, coalesce(sum(case when " + FIRST_OF_CLIENT + " then 1 else 0 end), 0) new_clients,"
                        + " coalesce(sum(p.amount), 0) revenue, coalesce(sum(p.discount_amount), 0) discounts"
                        + " from memberships m"
                        + " left join payments p on p.membership_id = m.id and p.status = 'COMPLETED'"
                        + " where " + COUNTS + " and m.is_migrated = 0 and m.activated_at >= ? and m.activated_at < ?"
                        + " group by m.plan_id order by sold desc, revenue desc, m.plan_id",
                (rs, i) -> new PlanSalesReportResponse.Row(rs.getLong("plan_id"), rs.getString("names"),
                        rs.getLong("sold"), rs.getLong("new_clients"), rs.getLong("sold") - rs.getLong("new_clients"),
                        rs.getBigDecimal("revenue"), rs.getBigDecimal("discounts")),
                ts(range.start()), ts(range.endExclusive()));
    }

    // ---- anulaciones y descuentos ----

    public List<VoidedPaymentsReportResponse.Row> voidedPayments(DateRange range) {
        return jdbc.query("select p.id, r.receipt_number, c.id client_id, " + CLIENT_NAME + " client_name, c.document_number,"
                        + " m.plan_name, p.payment_method, p.amount, p.paid_at, p.voided_at, " + USER_NAME + " user_name,"
                        + " p.void_reason"
                        + " from payments p join memberships m on m.id = p.membership_id join clients c on c.id = m.client_id"
                        + " left join payment_receipts r on r.payment_id = p.id left join users u on u.id = p.voided_by"
                        + " where p.status = 'VOIDED' and p.voided_at >= ? and p.voided_at < ?"
                        + " order by p.voided_at, p.id",
                (rs, i) -> new VoidedPaymentsReportResponse.Row(rs.getLong("id"), rs.getString("receipt_number"),
                        rs.getLong("client_id"), rs.getString("client_name"), rs.getString("document_number"),
                        rs.getString("plan_name"), PaymentMethod.valueOf(rs.getString("payment_method")),
                        rs.getBigDecimal("amount"), time(rs, "paid_at"), time(rs, "voided_at"),
                        rs.getString("user_name"), rs.getString("void_reason")),
                ts(range.start()), ts(range.endExclusive()));
    }

    public List<DiscountsReportResponse.Row> discounts(DateRange range) {
        return jdbc.query("select p.id, r.receipt_number, c.id client_id, " + CLIENT_NAME + " client_name, c.document_number,"
                        + " m.plan_name, p.payment_method, p.status, p.list_price, p.discount_amount, p.amount,"
                        + " p.discount_reason, " + USER_NAME + " user_name, p.paid_at"
                        + " from payments p join memberships m on m.id = p.membership_id join clients c on c.id = m.client_id"
                        + " left join payment_receipts r on r.payment_id = p.id left join users u on u.id = p.discounted_by"
                        + " where " + RECEIVED + " and p.discount_amount > 0 and p.paid_at >= ? and p.paid_at < ?"
                        + " order by p.paid_at, p.id",
                (rs, i) -> new DiscountsReportResponse.Row(rs.getLong("id"), rs.getString("receipt_number"),
                        rs.getLong("client_id"), rs.getString("client_name"), rs.getString("document_number"),
                        rs.getString("plan_name"), PaymentMethod.valueOf(rs.getString("payment_method")),
                        PaymentStatus.valueOf(rs.getString("status")), rs.getBigDecimal("list_price"),
                        rs.getBigDecimal("discount_amount"), rs.getBigDecimal("amount"), rs.getString("discount_reason"),
                        rs.getString("user_name"), time(rs, "paid_at")),
                ts(range.start()), ts(range.endExclusive()));
    }

    /** Cantidad y suma de lo anulado en el rango (por fecha de anulación). */
    public CountAmount voidedTotals(DateRange range) {
        return countAmount("select count(*), coalesce(sum(p.amount), 0) from payments p"
                + " where p.status = 'VOIDED' and p.voided_at >= ? and p.voided_at < ?", range);
    }

    /** Cantidad y suma de los descuentos del rango, sin los pagos anulados. */
    public CountAmount discountTotals(DateRange range) {
        return countAmount("select count(*), coalesce(sum(p.discount_amount), 0) from payments p"
                + " where p.status = 'COMPLETED' and p.discount_amount > 0 and p.paid_at >= ? and p.paid_at < ?", range);
    }

    public record CountAmount(long count, BigDecimal amount) {
    }

    // ---- vencimientos ----

    /** Una membresía que vence en el rango y lo que pasó después: renovada, pendiente o sin renovar. */
    public record ExpirationRow(Long membershipId, Long clientId, String clientName, String clientDocument,
                                String clientPhone, boolean clientActive, String planName, LocalDate startDate,
                                LocalDate endDate, LocalDateTime renewedAt, Long pendingRenewalId) {
    }

    /**
     * Renovada: el cliente tiene otra membresía que cuenta y empieza después de este vencimiento (la misma
     * regla de las alertas). renewedAt: cuándo empezó a contar la primera de ellas.
     */
    public List<ExpirationRow> expirations(DateRange range) {
        return jdbc.query("select m.id, c.id client_id, " + CLIENT_NAME + " client_name, c.document_number, c.phone,"
                        + " c.is_active, m.plan_name, m.start_date, m.end_date,"
                        + " (select min(coalesce(o.activated_at, o.created_at)) from memberships o"
                        + "   where o.client_id = m.client_id and o.status not in ('PENDING','CANCELLED')"
                        + "     and o.start_date > m.end_date) renewed_at,"
                        + " (select min(o.id) from memberships o where o.client_id = m.client_id and o.status = 'PENDING')"
                        + "   pending_id"
                        + " from memberships m join clients c on c.id = m.client_id"
                        + " where " + COUNTS + " and m.end_date >= ? and m.end_date <= ?"
                        + " order by m.end_date, m.id",
                (rs, i) -> new ExpirationRow(rs.getLong("id"), rs.getLong("client_id"), rs.getString("client_name"),
                        rs.getString("document_number"), rs.getString("phone"), rs.getBoolean("is_active"),
                        rs.getString("plan_name"), rs.getObject("start_date", LocalDate.class),
                        rs.getObject("end_date", LocalDate.class), time(rs, "renewed_at"),
                        (Long) rs.getObject("pending_id", Long.class)),
                range.from(), range.to());
    }

    // ---- utilidades ----

    private long count(String sql, Object... args) {
        Long n = jdbc.queryForObject(sql, Long.class, args);
        return n == null ? 0 : n;
    }

    private CountAmount countAmount(String sql, DateRange range) {
        return jdbc.query(sql, rs -> {
            rs.next();
            return new CountAmount(rs.getLong(1), rs.getBigDecimal(2));
        }, ts(range.start()), ts(range.endExclusive()));
    }

    private static NewClientsReportResponse.Row clientRow(ResultSet rs, NewClientsReportResponse.Type type)
            throws SQLException {
        return new NewClientsReportResponse.Row(type, rs.getLong("client_id"), rs.getString("client_name"),
                rs.getString("document_number"), rs.getString("phone"), rs.getBoolean("is_active"),
                rs.getLong("membership_id"), rs.getString("plan_name"), time(rs, "at"), rs.getBigDecimal("amount"));
    }

    private static DayAmount dayAmount(ResultSet rs, int i) throws SQLException {
        return new DayAmount(rs.getObject("d", LocalDate.class), PaymentMethod.valueOf(rs.getString("payment_method")),
                rs.getInt("n"), rs.getBigDecimal("total"));
    }

    private static LocalDateTime time(ResultSet rs, String column) throws SQLException {
        Timestamp t = rs.getTimestamp(column);
        return t == null ? null : t.toLocalDateTime();
    }

    private static Timestamp ts(LocalDateTime t) {
        return Timestamp.valueOf(t);
    }
}
