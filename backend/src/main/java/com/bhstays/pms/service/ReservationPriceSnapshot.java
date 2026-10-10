package com.bhstays.pms.service;

import com.bhstays.pms.domain.Reservation;
import com.bhstays.pms.dto.property.PriceQuoteResponse;
import java.math.BigDecimal;

/**
 * Keeps a reservation's price breakdown in step with its
 * {@code totalAmount}: accommodation (the only commissionable part) plus
 * one column per non-commissionable kind - cleaning fee, extra-guest fee,
 * late checkout, taxes, add-ons. The pricing engine quotes no late
 * checkout, tax or add-on into a booking total (a late checkout fee is
 * charged separately, after the booking), so those are 0 here.
 *
 * <p>The breakdown is only ever copied from a server-side quote whose total
 * is exactly the reservation's total in the same currency, and whose own
 * nightly subtotal minus discount is exactly the accommodation left once
 * the fees are taken out - so the parts reconcile to the cent and no
 * difference is ever absorbed by rounding. In every other case it is
 * cleared, so the reports treat the split as unknown instead of trusting a
 * stale or guessed one.
 */
final class ReservationPriceSnapshot {

    private ReservationPriceSnapshot() {
    }

    static void apply(Reservation reservation, PriceQuoteResponse quote) {
        if (!matches(reservation, quote)) {
            clear(reservation);
            return;
        }
        BigDecimal cleaningFee = orZero(quote.cleaningFee());
        BigDecimal extraGuestFee = orZero(quote.extraGuestFee());
        BigDecimal accommodation = reservation.getTotalAmount().subtract(cleaningFee).subtract(extraGuestFee);
        if (accommodation.signum() < 0 || !accommodationMatchesQuote(accommodation, quote)) {
            clear(reservation);
            return;
        }
        reservation.setAccommodationAmount(accommodation);
        reservation.setCleaningFeeAmount(cleaningFee);
        reservation.setExtraGuestFeeAmount(extraGuestFee);
        reservation.setLateCheckoutFeeAmount(BigDecimal.ZERO);
        reservation.setTaxAmount(BigDecimal.ZERO);
        reservation.setAddonAmount(BigDecimal.ZERO);
    }

    private static boolean matches(Reservation reservation, PriceQuoteResponse quote) {
        return quote != null
                && quote.available()
                && quote.totalAmount() != null
                && reservation.getTotalAmount() != null
                && quote.totalAmount().compareTo(reservation.getTotalAmount()) == 0
                && quote.currency() != null
                && quote.currency().equalsIgnoreCase(reservation.getCurrency());
    }

    /** Nightly subtotal (base/weekend/seasonal/dynamic) minus the stay discount, as quoted. */
    private static boolean accommodationMatchesQuote(BigDecimal accommodation, PriceQuoteResponse quote) {
        if (quote.subtotal() == null) {
            return false;
        }
        BigDecimal quoted = quote.subtotal().subtract(orZero(quote.discountAmount()));
        return quoted.compareTo(accommodation) == 0;
    }

    private static void clear(Reservation reservation) {
        reservation.setAccommodationAmount(null);
        reservation.setCleaningFeeAmount(null);
        reservation.setExtraGuestFeeAmount(null);
        reservation.setLateCheckoutFeeAmount(null);
        reservation.setTaxAmount(null);
        reservation.setAddonAmount(null);
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
