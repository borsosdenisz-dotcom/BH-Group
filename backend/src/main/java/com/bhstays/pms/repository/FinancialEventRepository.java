package com.bhstays.pms.repository;

import com.bhstays.pms.repository.projection.ReservationMoneyEvent;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Reads dated captures and refunds from the payment ledger
 * ({@code payment_transactions}), whose rows are written when the gateway
 * confirms the movement. The amounts come from the payment rows and the
 * successful ledger entries only - never from webhook events - so a
 * duplicate webhook delivery cannot count anything twice, and a payment
 * with several CHARGE entries is still captured once (at its first one).
 *
 * <p>Plain SQL on purpose: the window logic does not fit JPQL well, and it
 * keeps the query off the HQL parser.
 */
@Repository
@RequiredArgsConstructor
public class FinancialEventRepository {

    private static final String SQL = """
            with window_reservations as (
                select distinct p.reservation_id
                from payment_transactions t
                join payments p on p.id = t.payment_id
                join reservations r on r.id = p.reservation_id
                where t.status = 'SUCCEEDED'
                  and t.type in ('CHARGE', 'REFUND')
                  and t.created_at >= :start and t.created_at < :end
                  and r.property_id in (:propertyIds)
            )
            select r.property_id, r.id as reservation_id, p.id as payment_id,
                   p.currency as payment_currency, r.currency as reservation_currency,
                   r.total_amount, r.accommodation_amount, r.management_commission_percent_snapshot,
                   'CAPTURE' as kind, p.amount as amount, min(t.created_at) as occurred_at
            from payments p
            join reservations r on r.id = p.reservation_id
            join payment_transactions t on t.payment_id = p.id and t.type = 'CHARGE' and t.status = 'SUCCEEDED'
            where p.status in ('SUCCEEDED', 'PARTIALLY_REFUNDED', 'REFUNDED')
              and r.id in (select reservation_id from window_reservations)
            group by r.id, p.id
            having min(t.created_at) < :end
            union all
            select r.property_id, r.id, p.id, p.currency, r.currency,
                   r.total_amount, r.accommodation_amount, r.management_commission_percent_snapshot,
                   'REFUND', t.amount, t.created_at
            from payment_transactions t
            join payments p on p.id = t.payment_id
            join reservations r on r.id = p.reservation_id
            where t.type = 'REFUND' and t.status = 'SUCCEEDED'
              and t.created_at < :end
              and r.id in (select reservation_id from window_reservations)
            """;

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Every capture and refund before {@code end} of the reservations of the
     * given properties that had at least one of them in [start, end) - the
     * earlier movements are needed to know each reservation's state at the
     * start of the period.
     */
    public List<ReservationMoneyEvent> findEvents(Collection<UUID> propertyIds, Instant start, Instant end) {
        if (propertyIds.isEmpty()) {
            return List.of();
        }
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("propertyIds", propertyIds)
                .addValue("start", OffsetDateTime.ofInstant(start, ZoneOffset.UTC))
                .addValue("end", OffsetDateTime.ofInstant(end, ZoneOffset.UTC));
        return jdbc.query(SQL, params, (rs, rowNum) -> new ReservationMoneyEvent(
                rs.getObject("property_id", UUID.class),
                rs.getObject("reservation_id", UUID.class),
                rs.getObject("payment_id", UUID.class),
                rs.getString("payment_currency"),
                rs.getString("reservation_currency"),
                rs.getBigDecimal("total_amount"),
                rs.getBigDecimal("accommodation_amount"),
                rs.getBigDecimal("management_commission_percent_snapshot"),
                ReservationMoneyEvent.Kind.valueOf(rs.getString("kind")),
                rs.getBigDecimal("amount"),
                rs.getObject("occurred_at", OffsetDateTime.class).toInstant()));
    }
}
