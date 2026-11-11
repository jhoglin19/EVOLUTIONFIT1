package com.evolutionfit.modules.dashboard;

import java.sql.Timestamp;
import java.util.List;

import com.evolutionfit.modules.dashboard.dto.DashboardResponse.TodayPayment;
import com.evolutionfit.modules.payments.PaymentMethod;
import com.evolutionfit.modules.payments.PaymentStatus;
import com.evolutionfit.modules.reports.DateRange;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class DashboardRepository {

    private final JdbcTemplate jdbc;

    DashboardRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Pagos registrados en el día (por la hora del pago), los más recientes primero. */
    List<TodayPayment> payments(DateRange day, int limit) {
        return jdbc.query("""
                        select p.id, p.paid_at, c.id client_id, concat(c.first_name, ' ', c.last_name) client_name,
                               m.plan_name, p.payment_method, p.amount, p.status, r.receipt_number
                          from payments p
                          join memberships m on m.id = p.membership_id
                          join clients c on c.id = m.client_id
                          left join payment_receipts r on r.payment_id = p.id
                         where p.status in ('COMPLETED','VOIDED') and p.paid_at >= ? and p.paid_at < ?
                         order by p.paid_at desc, p.id desc
                         limit ?""",
                (rs, i) -> new TodayPayment(rs.getLong("id"), rs.getTimestamp("paid_at").toLocalDateTime(),
                        rs.getLong("client_id"), rs.getString("client_name"), rs.getString("plan_name"),
                        PaymentMethod.valueOf(rs.getString("payment_method")), rs.getBigDecimal("amount"),
                        PaymentStatus.valueOf(rs.getString("status")), rs.getString("receipt_number")),
                Timestamp.valueOf(day.start()), Timestamp.valueOf(day.endExclusive()), limit);
    }

    long countPayments(DateRange day, PaymentStatus status) {
        Long n = jdbc.queryForObject("select count(*) from payments p where p.status = ? and p.paid_at >= ? and p.paid_at < ?",
                Long.class, status.name(), Timestamp.valueOf(day.start()), Timestamp.valueOf(day.endExclusive()));
        return n == null ? 0 : n;
    }
}
