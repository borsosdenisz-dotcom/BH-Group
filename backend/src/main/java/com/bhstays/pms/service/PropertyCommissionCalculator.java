package com.bhstays.pms.service;

import com.bhstays.pms.dto.report.CommissionSummaryCurrencyTotals;
import com.bhstays.pms.dto.report.PropertyCommissionCurrencyResponse;
import com.bhstays.pms.repository.projection.ReservationMoneyEvent;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Splits collected money into the owner's share and BH Stays's commission,
 * per property and currency (never converted, never added across
 * currencies), for a period dated by the transactions themselves.
 *
 * <pre>
 * netRevenue          = captures - successful refunds          (in the period)
 * commissionableBase  = accommodation part of that net money
 * bhStaysRevenue      = commissionableBase * reservation's percent snapshot / 100
 * ownerAmount         = netRevenue - bhStaysRevenue
 * </pre>
 *
 * <p><b>Per reservation</b>, after captures C and refunds R, the
 * commissionable base is {@code B = min(C * accommodation / total,
 * accommodation) * (C - R) / C}: the accommodation share of what was
 * captured (capped at the accommodation, so a separate late-checkout or
 * add-on payment on top of a fully paid stay is never commissioned),
 * reduced proportionally by refunds, which carry no allocation to price
 * components. Cleaning, extra-guest, late checkout, taxes and add-ons are
 * never part of it. The commission uses the percent snapshotted on the
 * reservation when it was created, never the property's current one.
 *
 * <p><b>Periods.</b> A capture belongs to the period of its capture date,
 * a refund to the period of its refund date. A period's figures for a
 * reservation are its cumulative state at the period end minus its state
 * at the period start, with the base and commission rounded (2 decimals,
 * HALF_UP; intermediate ratios at scale {@value #INTERMEDIATE_SCALE}) on
 * the cumulative values. So a later refund is a negative adjustment of the
 * later period, earlier periods never change, and consecutive periods add
 * up exactly to the whole.
 *
 * <p>A reservation without a verifiable price breakdown or percent snapshot
 * (historical bookings), or paid in another currency than its own, is not
 * commissioned: its money stays in the net revenue and is reported as
 * unallocated, with a count - nothing is estimated.
 *
 * <p>This is the only place the formula lives: the property report, the
 * dashboard, /finance, owner statements and the owner portal all take
 * their figures from {@link #calculate} and add them up with {@link #totals}.
 */
public final class PropertyCommissionCalculator {

    static final int MONEY_SCALE = 2;
    static final int INTERMEDIATE_SCALE = 10;
    static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    private PropertyCommissionCalculator() {
    }

    /**
     * Lines per property, each sorted by currency, for the movements in
     * [start, end). Events before {@code start} only set each reservation's
     * starting state; events at or after {@code end} are ignored.
     */
    public static Map<UUID, List<PropertyCommissionCurrencyResponse>> calculate(List<ReservationMoneyEvent> events,
                                                                               Instant start, Instant end) {
        Map<String, List<ReservationMoneyEvent>> byReservationAndCurrency = new LinkedHashMap<>();
        for (ReservationMoneyEvent event : events) {
            String key = event.reservationId() + "|" + normalizeCurrency(event.paymentCurrency());
            byReservationAndCurrency.computeIfAbsent(key, k -> new ArrayList<>()).add(event);
        }

        Map<UUID, Map<String, LineAccumulator>> lines = new LinkedHashMap<>();
        for (List<ReservationMoneyEvent> reservationEvents : byReservationAndCurrency.values()) {
            ReservationMoneyEvent first = reservationEvents.get(0);
            ReservationTerms terms = ReservationTerms.of(first);
            State before = State.at(reservationEvents, start, terms);
            State after = State.at(reservationEvents, end, terms);
            if (before.captured.compareTo(after.captured) == 0 && before.refunded.compareTo(after.refunded) == 0) {
                continue;
            }
            lines.computeIfAbsent(first.propertyId(), id -> new TreeMap<>())
                    .computeIfAbsent(normalizeCurrency(first.paymentCurrency()), currency -> new LineAccumulator())
                    .add(terms, before, after);
        }

        Map<UUID, List<PropertyCommissionCurrencyResponse>> result = new LinkedHashMap<>();
        lines.forEach((propertyId, byCurrency) -> {
            List<PropertyCommissionCurrencyResponse> propertyLines = new ArrayList<>();
            byCurrency.forEach((currency, acc) -> propertyLines.add(acc.toResponse(currency)));
            result.put(propertyId, propertyLines);
        });
        return result;
    }

    /** A currency with no movement (e.g. only expenses) - all zero. */
    public static PropertyCommissionCurrencyResponse empty(String currency) {
        return new LineAccumulator().toResponse(currency);
    }

    /** Adds up per-property lines of one currency; {@code net = bhStays + owners} always holds. */
    public static CommissionSummaryCurrencyTotals totals(String currency,
                                                         List<PropertyCommissionCurrencyResponse> lines) {
        BigDecimal captured = BigDecimal.ZERO;
        BigDecimal refunded = BigDecimal.ZERO;
        BigDecimal net = BigDecimal.ZERO;
        BigDecimal bhStays = BigDecimal.ZERO;
        BigDecimal owners = BigDecimal.ZERO;
        BigDecimal unallocatedNet = BigDecimal.ZERO;
        int unallocatedReservations = 0;
        for (PropertyCommissionCurrencyResponse line : lines) {
            captured = captured.add(line.capturedTotal());
            refunded = refunded.add(line.refundedTotal());
            net = net.add(line.netRevenue());
            bhStays = bhStays.add(line.bhStaysRevenue());
            owners = owners.add(line.ownerAmount());
            unallocatedNet = unallocatedNet.add(line.unallocatedNetRevenue());
            unallocatedReservations += line.unallocatedReservationCount();
        }
        return new CommissionSummaryCurrencyTotals(currency, money(captured), money(refunded), money(net),
                money(bhStays), money(owners), lines.size(), money(unallocatedNet), unallocatedReservations);
    }

    static BigDecimal money(BigDecimal value) {
        return value.setScale(MONEY_SCALE, ROUNDING);
    }

    private static String normalizeCurrency(String currency) {
        return currency == null ? "" : currency.trim().toUpperCase(Locale.ROOT);
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    /** What a reservation's money can be split with; {@code commissionable} only when all of it is verifiable. */
    private record ReservationTerms(boolean commissionable, BigDecimal total, BigDecimal accommodation,
                                    BigDecimal percent) {

        static ReservationTerms of(ReservationMoneyEvent event) {
            boolean commissionable = event.accommodationAmount() != null
                    && event.reservationTotal() != null
                    && event.reservationTotal().signum() > 0
                    && event.commissionPercentSnapshot() != null
                    && normalizeCurrency(event.paymentCurrency()).equals(normalizeCurrency(event.reservationCurrency()));
            return new ReservationTerms(commissionable, event.reservationTotal(), event.accommodationAmount(),
                    event.commissionPercentSnapshot());
        }
    }

    /** A reservation's cumulative position just before {@code cut}. */
    private record State(BigDecimal captured, BigDecimal refunded, BigDecimal base, BigDecimal commission) {

        static State at(List<ReservationMoneyEvent> events, Instant cut, ReservationTerms terms) {
            BigDecimal captured = BigDecimal.ZERO;
            BigDecimal refunded = BigDecimal.ZERO;
            for (ReservationMoneyEvent event : events) {
                if (!event.occurredAt().isBefore(cut)) {
                    continue;
                }
                if (event.kind() == ReservationMoneyEvent.Kind.CAPTURE) {
                    captured = captured.add(orZero(event.amount()));
                } else {
                    refunded = refunded.add(orZero(event.amount()));
                }
            }
            BigDecimal base = BigDecimal.ZERO.setScale(MONEY_SCALE);
            BigDecimal commission = BigDecimal.ZERO.setScale(MONEY_SCALE);
            if (terms.commissionable() && captured.signum() > 0) {
                BigDecimal capturedAccommodation = captured.multiply(terms.accommodation())
                        .divide(terms.total(), INTERMEDIATE_SCALE, ROUNDING)
                        .min(terms.accommodation());
                base = capturedAccommodation.multiply(captured.subtract(refunded))
                        .divide(captured, INTERMEDIATE_SCALE, ROUNDING)
                        .setScale(MONEY_SCALE, ROUNDING);
                commission = base.multiply(terms.percent()).divide(ONE_HUNDRED, MONEY_SCALE, ROUNDING);
            }
            return new State(captured, refunded, base, commission);
        }
    }

    private static final class LineAccumulator {
        private BigDecimal captured = BigDecimal.ZERO;
        private BigDecimal refunded = BigDecimal.ZERO;
        private BigDecimal base = BigDecimal.ZERO;
        private BigDecimal commission = BigDecimal.ZERO;
        private BigDecimal unallocatedNet = BigDecimal.ZERO;
        private final TreeSet<BigDecimal> percents = new TreeSet<>();
        private int reservations;
        private int unallocatedReservations;

        void add(ReservationTerms terms, State before, State after) {
            BigDecimal capturedDelta = after.captured().subtract(before.captured());
            BigDecimal refundedDelta = after.refunded().subtract(before.refunded());
            captured = captured.add(capturedDelta);
            refunded = refunded.add(refundedDelta);
            reservations++;
            if (terms.commissionable()) {
                base = base.add(after.base().subtract(before.base()));
                commission = commission.add(after.commission().subtract(before.commission()));
                percents.add(money(terms.percent()));
            } else {
                unallocatedNet = unallocatedNet.add(capturedDelta.subtract(refundedDelta));
                unallocatedReservations++;
            }
        }

        PropertyCommissionCurrencyResponse toResponse(String currency) {
            BigDecimal net = money(captured.subtract(refunded));
            BigDecimal bhStays = money(commission);
            return new PropertyCommissionCurrencyResponse(
                    currency,
                    money(captured),
                    money(refunded),
                    net,
                    money(base),
                    List.copyOf(percents),
                    bhStays,
                    money(net.subtract(bhStays)),
                    money(unallocatedNet),
                    unallocatedReservations,
                    reservations);
        }
    }
}
