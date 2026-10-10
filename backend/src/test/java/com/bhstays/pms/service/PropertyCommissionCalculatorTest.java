package com.bhstays.pms.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.bhstays.pms.dto.report.CommissionSummaryCurrencyTotals;
import com.bhstays.pms.dto.report.PropertyCommissionCurrencyResponse;
import com.bhstays.pms.repository.projection.ReservationMoneyEvent;
import com.bhstays.pms.repository.projection.ReservationMoneyEvent.Kind;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PropertyCommissionCalculatorTest {

    private static final UUID PROPERTY_ID = UUID.randomUUID();
    private static final FinancialPeriod ALL_TIME = FinancialPeriod.of(null, null);
    private static final FinancialPeriod JANUARY = FinancialPeriod.of(LocalDate.of(2030, 1, 1), LocalDate.of(2030, 1, 31));
    private static final FinancialPeriod FEBRUARY = FinancialPeriod.of(LocalDate.of(2030, 2, 1), LocalDate.of(2030, 2, 28));
    private static final Instant JAN_10 = Instant.parse("2030-01-10T10:00:00Z");
    private static final Instant JAN_20 = Instant.parse("2030-01-20T10:00:00Z");
    private static final Instant FEB_05 = Instant.parse("2030-02-05T10:00:00Z");

    private final List<ReservationMoneyEvent> events = new ArrayList<>();

    /** A reservation's terms; {@code accommodation} or {@code percent} null = no verifiable snapshot. */
    private final class Booking {
        private final UUID id = UUID.randomUUID();
        private final String currency;
        private final String paymentCurrency;
        private final String total;
        private final String accommodation;
        private final String percent;

        Booking(String currency, String paymentCurrency, String total, String accommodation, String percent) {
            this.currency = currency;
            this.paymentCurrency = paymentCurrency;
            this.total = total;
            this.accommodation = accommodation;
            this.percent = percent;
        }

        Booking capture(String amount, Instant at) {
            return add(Kind.CAPTURE, amount, at);
        }

        Booking refund(String amount, Instant at) {
            return add(Kind.REFUND, amount, at);
        }

        private Booking add(Kind kind, String amount, Instant at) {
            events.add(new ReservationMoneyEvent(PROPERTY_ID, id, UUID.randomUUID(), paymentCurrency, currency,
                    total != null ? new BigDecimal(total) : null,
                    accommodation != null ? new BigDecimal(accommodation) : null,
                    percent != null ? new BigDecimal(percent) : null,
                    kind, new BigDecimal(amount), at));
            return this;
        }
    }

    private Booking booking(String currency, String total, String accommodation, String percent) {
        return new Booking(currency, currency, total, accommodation, percent);
    }

    private List<PropertyCommissionCurrencyResponse> lines(FinancialPeriod period) {
        Map<UUID, List<PropertyCommissionCurrencyResponse>> result =
                PropertyCommissionCalculator.calculate(events, period.start(), period.end());
        return result.getOrDefault(PROPERTY_ID, List.of());
    }

    private PropertyCommissionCurrencyResponse single(FinancialPeriod period) {
        List<PropertyCommissionCurrencyResponse> lines = lines(period);
        assertThat(lines).hasSize(1);
        return lines.get(0);
    }

    @Test
    void capture_isSplitIntoCommissionOnAccommodationAndOwnerAmount() {
        // 500 = 400 accommodation + 100 cleaning, 20% snapshot
        booking("RON", "500.00", "400.00", "20").capture("500.00", JAN_10);

        PropertyCommissionCurrencyResponse line = single(ALL_TIME);

        assertThat(line.capturedTotal()).isEqualByComparingTo("500.00");
        assertThat(line.netRevenue()).isEqualByComparingTo("500.00");
        assertThat(line.commissionableBase()).isEqualByComparingTo("400.00");
        assertThat(line.commissionPercents()).containsExactly(new BigDecimal("20.00"));
        assertThat(line.bhStaysRevenue()).isEqualByComparingTo("80.00");
        assertThat(line.ownerAmount()).isEqualByComparingTo("420.00");
        assertThat(line.reservationCount()).isEqualTo(1);
    }

    @Test
    void nonAccommodationPartsAreNeverCommissioned() {
        // 1000 = 700 accommodation + 300 of cleaning / extra guest / late checkout / taxes / add-ons
        booking("RON", "1000.00", "700.00", "10").capture("1000.00", JAN_10);

        PropertyCommissionCurrencyResponse line = single(ALL_TIME);

        assertThat(line.commissionableBase()).isEqualByComparingTo("700.00");
        assertThat(line.bhStaysRevenue()).isEqualByComparingTo("70.00");
        assertThat(line.ownerAmount()).isEqualByComparingTo("930.00");
    }

    @Test
    void fullRefund_leavesNothingToShare() {
        booking("RON", "500.00", "400.00", "20").capture("500.00", JAN_10).refund("500.00", JAN_20);

        PropertyCommissionCurrencyResponse line = single(ALL_TIME);

        assertThat(line.refundedTotal()).isEqualByComparingTo("500.00");
        assertThat(line.netRevenue()).isEqualByComparingTo("0.00");
        assertThat(line.commissionableBase()).isEqualByComparingTo("0.00");
        assertThat(line.bhStaysRevenue()).isEqualByComparingTo("0.00");
        assertThat(line.ownerAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    void partialRefund_reducesTheBaseProportionally() {
        booking("RON", "500.00", "400.00", "20").capture("500.00", JAN_10).refund("200.00", JAN_20);

        PropertyCommissionCurrencyResponse line = single(ALL_TIME);

        assertThat(line.netRevenue()).isEqualByComparingTo("300.00");
        assertThat(line.commissionableBase()).isEqualByComparingTo("240.00");
        assertThat(line.bhStaysRevenue()).isEqualByComparingTo("48.00");
        assertThat(line.ownerAmount()).isEqualByComparingTo("252.00");
    }

    @Test
    void laterRefundIsAnAdjustmentOfItsOwnMonth_theEarlierMonthIsUnchanged() {
        booking("RON", "500.00", "400.00", "20").capture("500.00", JAN_10).refund("200.00", FEB_05);

        PropertyCommissionCurrencyResponse january = single(JANUARY);
        assertThat(january.capturedTotal()).isEqualByComparingTo("500.00");
        assertThat(january.refundedTotal()).isEqualByComparingTo("0.00");
        assertThat(january.bhStaysRevenue()).isEqualByComparingTo("80.00");
        assertThat(january.ownerAmount()).isEqualByComparingTo("420.00");

        PropertyCommissionCurrencyResponse february = single(FEBRUARY);
        assertThat(february.capturedTotal()).isEqualByComparingTo("0.00");
        assertThat(february.refundedTotal()).isEqualByComparingTo("200.00");
        assertThat(february.netRevenue()).isEqualByComparingTo("-200.00");
        assertThat(february.commissionableBase()).isEqualByComparingTo("-160.00");
        assertThat(february.bhStaysRevenue()).isEqualByComparingTo("-32.00");
        assertThat(february.ownerAmount()).isEqualByComparingTo("-168.00");

        // the two months add up exactly to the whole
        PropertyCommissionCurrencyResponse whole = single(ALL_TIME);
        assertThat(january.bhStaysRevenue().add(february.bhStaysRevenue())).isEqualByComparingTo(whole.bhStaysRevenue());
        assertThat(january.ownerAmount().add(february.ownerAmount())).isEqualByComparingTo(whole.ownerAmount());
    }

    @Test
    void eachReservationKeepsItsOwnSnapshotPercent() {
        booking("RON", "500.00", "500.00", "20").capture("500.00", JAN_10);
        booking("RON", "500.00", "500.00", "25").capture("500.00", JAN_20);

        PropertyCommissionCurrencyResponse line = single(ALL_TIME);

        assertThat(line.commissionPercents()).containsExactly(new BigDecimal("20.00"), new BigDecimal("25.00"));
        assertThat(line.bhStaysRevenue()).isEqualByComparingTo("225.00");   // 100 + 125
        assertThat(line.ownerAmount()).isEqualByComparingTo("775.00");
    }

    @Test
    void zeroAndHundredPercent() {
        booking("RON", "500.00", "400.00", "0").capture("500.00", JAN_10);
        assertThat(single(ALL_TIME).bhStaysRevenue()).isEqualByComparingTo("0.00");

        events.clear();
        booking("RON", "500.00", "400.00", "100").capture("500.00", JAN_10);
        assertThat(single(ALL_TIME).bhStaysRevenue()).isEqualByComparingTo("400.00");
        assertThat(single(ALL_TIME).ownerAmount()).isEqualByComparingTo("100.00");
    }

    @Test
    void reservationWithoutPercentSnapshot_isFlaggedAndNeverCommissioned() {
        booking("RON", "500.00", "400.00", "20").capture("500.00", JAN_10);
        booking("RON", "300.00", "300.00", null).capture("300.00", JAN_20);

        PropertyCommissionCurrencyResponse line = single(ALL_TIME);

        assertThat(line.netRevenue()).isEqualByComparingTo("800.00");
        assertThat(line.bhStaysRevenue()).isEqualByComparingTo("80.00");
        assertThat(line.ownerAmount()).isEqualByComparingTo("720.00");
        assertThat(line.unallocatedNetRevenue()).isEqualByComparingTo("300.00");
        assertThat(line.unallocatedReservationCount()).isEqualTo(1);
        assertThat(line.commissionPercents()).containsExactly(new BigDecimal("20.00"));
    }

    @Test
    void reservationWithoutPriceBreakdown_isFlaggedAndNeverCommissioned() {
        booking("RON", "250.00", null, "20").capture("250.00", JAN_10).refund("50.00", JAN_20);

        PropertyCommissionCurrencyResponse line = single(ALL_TIME);

        assertThat(line.netRevenue()).isEqualByComparingTo("200.00");
        assertThat(line.bhStaysRevenue()).isEqualByComparingTo("0.00");
        assertThat(line.unallocatedNetRevenue()).isEqualByComparingTo("200.00");
        assertThat(line.unallocatedReservationCount()).isEqualTo(1);
        assertThat(line.commissionPercents()).isEmpty();
    }

    @Test
    void paymentInAnotherCurrencyThanTheReservation_isUnallocated() {
        new Booking("RON", "EUR", "500.00", "400.00", "20").capture("100.00", JAN_10);

        PropertyCommissionCurrencyResponse line = single(ALL_TIME);

        assertThat(line.currency()).isEqualTo("EUR");
        assertThat(line.unallocatedNetRevenue()).isEqualByComparingTo("100.00");
        assertThat(line.bhStaysRevenue()).isEqualByComparingTo("0.00");
    }

    @Test
    void currenciesAreNeverAddedTogether() {
        booking("RON", "500.00", "400.00", "10").capture("500.00", JAN_10);
        booking("EUR", "100.00", "80.00", "10").capture("100.00", JAN_10);

        List<PropertyCommissionCurrencyResponse> lines = lines(ALL_TIME);

        assertThat(lines).extracting(PropertyCommissionCurrencyResponse::currency).containsExactly("EUR", "RON");
        assertThat(lines.get(0).bhStaysRevenue()).isEqualByComparingTo("8.00");
        assertThat(lines.get(1).bhStaysRevenue()).isEqualByComparingTo("40.00");
    }

    @Test
    void separateLateCheckoutPaymentOnAFullyPaidStay_isNotCommissioned() {
        booking("RON", "500.00", "400.00", "20").capture("500.00", JAN_10).capture("50.00", JAN_20);

        PropertyCommissionCurrencyResponse line = single(ALL_TIME);

        assertThat(line.netRevenue()).isEqualByComparingTo("550.00");
        assertThat(line.commissionableBase()).isEqualByComparingTo("400.00");
        assertThat(line.bhStaysRevenue()).isEqualByComparingTo("80.00");
    }

    @Test
    void depositAndBalanceInDifferentMonths() {
        // 300 stay, 100 accommodation; deposit in January, balance in February
        booking("RON", "300.00", "100.00", "15").capture("100.00", JAN_10).capture("200.00", FEB_05);

        // January base = 100 * 100/300 = 33.33; commission 15% = 4.9995 -> 5.00
        PropertyCommissionCurrencyResponse january = single(JANUARY);
        assertThat(january.commissionableBase()).isEqualByComparingTo("33.33");
        assertThat(january.bhStaysRevenue()).isEqualByComparingTo("5.00");

        PropertyCommissionCurrencyResponse february = single(FEBRUARY);
        assertThat(february.commissionableBase()).isEqualByComparingTo("66.67");
        assertThat(february.bhStaysRevenue()).isEqualByComparingTo("10.00");
        assertThat(single(ALL_TIME).bhStaysRevenue()).isEqualByComparingTo("15.00");
    }

    @Test
    void rounding_isHalfUpToTwoDecimals() {
        // 12.5% of 233.33 = 29.16625 -> 29.17
        booking("RON", "333.33", "333.33", "12.5").capture("333.33", JAN_10).refund("100.00", JAN_20);

        PropertyCommissionCurrencyResponse line = single(ALL_TIME);

        assertThat(line.commissionableBase()).isEqualByComparingTo("233.33");
        assertThat(line.bhStaysRevenue()).isEqualByComparingTo("29.17");
        assertThat(line.ownerAmount()).isEqualByComparingTo("204.16");
        assertThat(line.bhStaysRevenue().scale()).isEqualTo(2);
        assertThat(line.netRevenue().scale()).isEqualTo(2);
    }

    @Test
    void reservationWithoutMovementInThePeriodIsLeftOut() {
        booking("RON", "500.00", "400.00", "20").capture("500.00", JAN_10);

        assertThat(lines(FEBRUARY)).isEmpty();
    }

    @Test
    void totals_reconcileNetWithBothShares() {
        booking("RON", "500.00", "400.00", "20").capture("500.00", JAN_10).refund("100.00", JAN_20);
        booking("RON", "200.00", null, null).capture("200.00", JAN_10);

        CommissionSummaryCurrencyTotals totals = PropertyCommissionCalculator.totals("RON", lines(ALL_TIME));

        assertThat(totals.capturedTotal()).isEqualByComparingTo("700.00");
        assertThat(totals.refundedTotal()).isEqualByComparingTo("100.00");
        assertThat(totals.propertiesNetRevenue()).isEqualByComparingTo("600.00");
        assertThat(totals.bhStaysRevenue()).isEqualByComparingTo("64.00");
        assertThat(totals.ownersAmount()).isEqualByComparingTo("536.00");
        assertThat(totals.unallocatedNetRevenue()).isEqualByComparingTo("200.00");
        assertThat(totals.unallocatedReservationCount()).isEqualTo(1);
        assertThat(totals.bhStaysRevenue().add(totals.ownersAmount())).isEqualByComparingTo(totals.propertiesNetRevenue());
    }

    @Test
    void periodsUseRomanianCalendarDays() {
        // 31 January 23:30 in Bucharest is still January, though it is 21:30 UTC
        booking("RON", "100.00", "100.00", "10").capture("100.00", Instant.parse("2030-01-31T21:30:00Z"));
        assertThat(lines(JANUARY)).hasSize(1);
        assertThat(lines(FEBRUARY)).isEmpty();

        events.clear();
        // 1 February 00:30 in Bucharest is 31 January 22:30 UTC - February
        booking("RON", "100.00", "100.00", "10").capture("100.00", Instant.parse("2030-01-31T22:30:00Z"));
        assertThat(lines(JANUARY)).isEmpty();
        assertThat(lines(FEBRUARY)).hasSize(1);
    }
}
