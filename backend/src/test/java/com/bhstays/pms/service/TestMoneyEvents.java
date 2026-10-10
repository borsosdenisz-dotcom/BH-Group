package com.bhstays.pms.service;

import com.bhstays.pms.repository.projection.PropertyCommissionSettings;
import com.bhstays.pms.repository.projection.ReservationMoneyEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Ledger events for service tests: a reservation captured (and optionally refunded) at a fixed instant. */
final class TestMoneyEvents {

    static final Instant AT = Instant.parse("2030-05-10T10:00:00Z");

    private TestMoneyEvents() {
    }

    /** {@code accommodation} / {@code percent} null = no verifiable snapshot. */
    static List<ReservationMoneyEvent> paid(PropertyCommissionSettings property, String currency, String total,
                                            String accommodation, String percent, String captured, String refunded) {
        UUID reservation = UUID.randomUUID();
        UUID payment = UUID.randomUUID();
        BigDecimal acc = accommodation != null ? new BigDecimal(accommodation) : null;
        BigDecimal pct = percent != null ? new BigDecimal(percent) : null;
        List<ReservationMoneyEvent> events = new ArrayList<>();
        events.add(new ReservationMoneyEvent(property.id(), reservation, payment, currency, currency,
                new BigDecimal(total), acc, pct, ReservationMoneyEvent.Kind.CAPTURE, new BigDecimal(captured), AT));
        if (new BigDecimal(refunded).signum() != 0) {
            events.add(new ReservationMoneyEvent(property.id(), reservation, payment, currency, currency,
                    new BigDecimal(total), acc, pct, ReservationMoneyEvent.Kind.REFUND, new BigDecimal(refunded),
                    AT.plusSeconds(60)));
        }
        return events;
    }

    @SafeVarargs
    static List<ReservationMoneyEvent> all(List<ReservationMoneyEvent>... groups) {
        List<ReservationMoneyEvent> result = new ArrayList<>();
        for (List<ReservationMoneyEvent> group : groups) {
            result.addAll(group);
        }
        return result;
    }
}
