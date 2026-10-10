package com.bhstays.pms.repository.projection;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One dated money movement of a reservation, taken from the payment
 * ledger: a capture (the payment's successful CHARGE entry, for the
 * payment's amount) or a refund (a successful REFUND entry). Carries the
 * reservation's price and commission snapshots so the accommodation share
 * and the commission can be computed without another query.
 */
public record ReservationMoneyEvent(
        UUID propertyId,
        UUID reservationId,
        UUID paymentId,
        String paymentCurrency,
        String reservationCurrency,
        BigDecimal reservationTotal,
        BigDecimal accommodationAmount,
        BigDecimal commissionPercentSnapshot,
        Kind kind,
        BigDecimal amount,
        Instant occurredAt
) {

    public enum Kind {
        CAPTURE,
        REFUND
    }
}
