package com.bhstays.pms.dto.publicapi;

import java.math.BigDecimal;

/**
 * A public booking is card-only: creating it holds the dates and opens the
 * hosted Stripe Checkout session in one step, so the guest is sent straight
 * to {@code checkoutUrl}. Amount and currency are the server's own figures.
 */
public record PublicBookingCheckoutResponse(
        PublicReservationResponse reservation,
        String checkoutUrl,
        BigDecimal amount,
        String currency
) {
}
