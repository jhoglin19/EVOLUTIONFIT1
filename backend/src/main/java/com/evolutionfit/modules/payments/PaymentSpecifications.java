package com.evolutionfit.modules.payments;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.evolutionfit.modules.receipts.PaymentReceipt;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;

import org.springframework.data.jpa.domain.Specification;

final class PaymentSpecifications {

    private PaymentSpecifications() {
    }

    /** Fechas por día de pago (incluidas). {@code q}: documento, nombre del cliente o número de comprobante. */
    static Specification<Payment> filter(LocalDate from, LocalDate to, PaymentMethod method, PaymentStatus status,
                                         String query) {
        return (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("paidAt"), from.atStartOfDay()));
            }
            if (to != null) {
                predicates.add(cb.lessThan(root.get("paidAt"), to.plusDays(1).atStartOfDay()));
            }
            if (method != null) {
                predicates.add(cb.equal(root.get("method"), method));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (query != null && !query.isBlank()) {
                String q = query.trim();
                String like = "%" + q.toLowerCase(Locale.ROOT) + "%";
                Join<Object, Object> client = root.join("membership").join("client");
                Subquery<Long> receipt = cq.subquery(Long.class);
                var r = receipt.from(PaymentReceipt.class);
                receipt.select(r.get("paymentId")).where(cb.like(cb.lower(r.get("receiptNumber")), like));
                predicates.add(cb.or(
                        cb.like(client.get("documentNumber"), "%" + q + "%"),
                        cb.like(cb.lower(cb.concat(cb.concat(client.get("firstName"), " "), client.get("lastName"))), like),
                        root.get("id").in(receipt)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
