package com.bhgroup.pms.service;

import com.bhgroup.pms.common.exception.BadRequestException;
import com.bhgroup.pms.config.AppProperties;
import com.bhgroup.pms.domain.Payment;
import com.bhgroup.pms.domain.Reservation;
import com.bhgroup.pms.domain.ReservationStatus;
import com.bhgroup.pms.dto.payment.CheckoutSessionResponse;
import com.bhgroup.pms.dto.property.PriceQuoteResponse;
import com.bhgroup.pms.payment.StripeGateway;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Opens a hosted Stripe Checkout session for a booking that is still on
 * hold. The guest is only ever handed a redirect URL - the card form, and
 * therefore all card data, stays on Stripe's domain.
 *
 * <p>The charged amount is recomputed here from {@link PricingService},
 * never taken from the request: the endpoint accepts nothing but the
 * booking's management token, so there is no client-supplied amount to
 * trust in the first place.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnExpression("'${app.stripe.secret-key:}' != ''")
public class StripeCheckoutService {

    /**
     * Stripe refuses a Checkout session expiring sooner than 30 minutes out,
     * which is longer than our 15-minute booking hold. The hold still governs:
     * BookingHoldExpiryScheduler cancels the reservation at 15 minutes, and a
     * payment that lands after that is refunded rather than honoured (see
     * StripeWebhookService).
     */
    private static final long SESSION_EXPIRY_MINUTES = 30;

    private final ReservationService reservationService;
    private final PricingService pricingService;
    private final PaymentService paymentService;
    private final StripeGateway stripeGateway;
    private final AppProperties appProperties;

    @Transactional
    public CheckoutSessionResponse createCheckoutSession(String managementToken) {
        Reservation reservation = reservationService.getByManagementToken(managementToken);
        assertPayable(reservation);

        // Server-side recomputation: the authoritative price for these exact
        // dates and guest count, independent of anything the browser sent.
        PriceQuoteResponse quote = pricingService.quote(
                reservation.getProperty(),
                reservation.getCheckInDate(),
                reservation.getCheckOutDate(),
                reservation.getNumberOfGuests());

        if (!quote.available() || quote.totalAmount() == null) {
            throw new BadRequestException("Prețul nu poate fi calculat pentru această rezervare");
        }

        BigDecimal amount = quote.totalAmount();
        String currency = quote.currency();

        if (reservation.getTotalAmount() == null || reservation.getTotalAmount().compareTo(amount) != 0) {
            log.info("Reservation {} total {} realigned to the recomputed quote {} before checkout",
                    reservation.getId(), reservation.getTotalAmount(), amount);
            reservation.setTotalAmount(amount);
            reservation.setCurrency(currency);
        }

        Payment payment = paymentService.startOnlineCardPayment(reservation, amount, currency);

        StripeGateway.StripeCheckoutSession session = stripeGateway.createCheckoutSession(
                new StripeGateway.CheckoutSessionRequest(
                        reservation.getId().toString(),
                        reservation.getProperty().getName(),
                        "%s → %s · %d %s".formatted(
                                reservation.getCheckInDate(), reservation.getCheckOutDate(),
                                reservation.getNumberOfGuests(),
                                reservation.getNumberOfGuests() == 1 ? "oaspete" : "oaspeți"),
                        reservation.getGuestEmail(),
                        amount,
                        currency,
                        successUrl(managementToken),
                        cancelUrl(managementToken),
                        // Stable per (payment, amount): a retried request returns
                        // the same Stripe session instead of opening a second one.
                        "checkout-%s-%s".formatted(payment.getId(), amount.stripTrailingZeros().toPlainString()),
                        Instant.now().plus(SESSION_EXPIRY_MINUTES, ChronoUnit.MINUTES).getEpochSecond()));

        log.info("Opened Stripe Checkout session {} for reservation {}", session.sessionId(), reservation.getId());
        return new CheckoutSessionResponse(session.checkoutUrl(), amount, currency);
    }

    /**
     * Only a booking that is still holding the calendar may be paid for. A
     * confirmed one is already paid, and an expired/cancelled hold no longer
     * owns its dates - someone else may have taken them.
     */
    private void assertPayable(Reservation reservation) {
        if (reservation.getStatus() == ReservationStatus.CONFIRMED) {
            throw new BadRequestException("Rezervarea este deja confirmată și plătită");
        }
        if (reservation.getStatus() != ReservationStatus.PENDING) {
            throw new BadRequestException("Această rezervare nu mai poate fi plătită");
        }
        if (reservation.getHoldExpiresAt() != null && reservation.getHoldExpiresAt().isBefore(Instant.now())) {
            throw new BadRequestException("Rezervarea a expirat. Te rugăm să reiei rezervarea.");
        }
    }

    private String successUrl(String managementToken) {
        return appProperties.getBaseUrl() + "/plata/succes?token=" + managementToken;
    }

    private String cancelUrl(String managementToken) {
        return appProperties.getBaseUrl() + "/plata/anulat?token=" + managementToken;
    }
}
