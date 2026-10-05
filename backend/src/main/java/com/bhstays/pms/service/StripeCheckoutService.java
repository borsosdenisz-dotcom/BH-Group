package com.bhstays.pms.service;

import com.bhstays.pms.common.exception.BadRequestException;
import com.bhstays.pms.config.AppProperties;
import com.bhstays.pms.domain.Payment;
import com.bhstays.pms.domain.Reservation;
import com.bhstays.pms.domain.ReservationStatus;
import com.bhstays.pms.dto.payment.CheckoutSessionResponse;
import com.bhstays.pms.dto.property.PriceQuoteResponse;
import com.bhstays.pms.payment.StripeGateway;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
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
 *
 * <p>One booking has at most one payable session at a time. The reservation
 * row is locked for the whole call, so a double click (or two tabs) gets the
 * same open session back instead of a second one the guest could also pay.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnExpression("'${app.stripe.secret-key:}' != ''")
public class StripeCheckoutService {

    /** Stripe refuses a Checkout session expiring sooner than 30 minutes out. */
    static final Duration SESSION_LIFETIME = Duration.ofMinutes(30);

    /**
     * The hold outlives its session by this much, so a payment completed in
     * the session's last seconds is confirmed rather than racing the expiry
     * job. Stripe itself refuses payment once the session has expired.
     */
    static final Duration HOLD_GRACE_AFTER_SESSION = Duration.ofMinutes(5);

    /**
     * Upper bound on how long one booking can keep its dates off the market
     * by reopening checkout, counted from when the hold was created.
     */
    static final Duration MAX_HOLD = Duration.ofHours(2);

    /** An open session closer than this to expiry is replaced rather than handed out again. */
    private static final Duration MIN_REUSABLE_SESSION_TIME = Duration.ofMinutes(5);

    private final ReservationService reservationService;
    private final PricingService pricingService;
    private final PaymentService paymentService;
    private final StripeGateway stripeGateway;
    private final AppProperties appProperties;

    @Transactional
    public CheckoutSessionResponse createCheckoutSession(String managementToken) {
        Reservation reservation = reservationService.lockByManagementToken(managementToken);
        Instant now = Instant.now();
        assertPayable(reservation, now);

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

        Optional<Payment> open = paymentService.findOpenCardCheckout(reservation.getId(), now);
        if (open.isPresent()) {
            Payment existing = open.get();
            boolean samePrice = existing.getAmount().compareTo(amount) == 0
                    && existing.getCurrency().equalsIgnoreCase(currency);
            if (samePrice && existing.getCheckoutExpiresAt().isAfter(now.plus(MIN_REUSABLE_SESSION_TIME))) {
                return new CheckoutSessionResponse(existing.getCheckoutUrl(), existing.getAmount(), existing.getCurrency());
            }
            // About to lapse, or priced differently: close it first so there is
            // never more than one session the guest could pay.
            stripeGateway.expireCheckoutSession(existing.getCheckoutSessionId());
        }

        Instant sessionExpiresAt = now.plus(SESSION_LIFETIME).truncatedTo(ChronoUnit.SECONDS);
        Instant holdUntil = sessionExpiresAt.plus(HOLD_GRACE_AFTER_SESSION);
        if (reservation.getCreatedAt() != null && holdUntil.isAfter(reservation.getCreatedAt().plus(MAX_HOLD))) {
            throw new BadRequestException("Timpul pentru plata acestei rezervări a expirat. Te rugăm să reiei rezervarea.");
        }

        if (reservation.getTotalAmount() == null || reservation.getTotalAmount().compareTo(amount) != 0) {
            log.info("Reservation {} total {} realigned to the recomputed quote {} before checkout",
                    reservation.getId(), reservation.getTotalAmount(), amount);
            reservation.setTotalAmount(amount);
        }
        reservation.setCurrency(currency);

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
                        // Unique per session attempt: a network-level retry of this
                        // exact request returns the same session, while a later
                        // replacement session is never answered from Stripe's cache.
                        "checkout-%s-%d".formatted(payment.getId(), sessionExpiresAt.getEpochSecond()),
                        sessionExpiresAt.getEpochSecond()));

        payment.setCheckoutSessionId(session.sessionId());
        payment.setCheckoutUrl(session.checkoutUrl());
        payment.setCheckoutExpiresAt(sessionExpiresAt);

        // Keep the calendar hold in step with the session: the dates stay
        // ours for as long as the guest can still pay for them.
        if (reservation.getHoldExpiresAt() == null || reservation.getHoldExpiresAt().isBefore(holdUntil)) {
            reservation.setHoldExpiresAt(holdUntil);
        }

        log.info("Opened Stripe Checkout session {} for reservation {}", session.sessionId(), reservation.getId());
        return new CheckoutSessionResponse(session.checkoutUrl(), amount, currency);
    }

    /**
     * Only a booking that is still holding the calendar may be paid for. A
     * confirmed one is already paid, and an expired/cancelled hold no longer
     * owns its dates - someone else may have taken them.
     */
    private void assertPayable(Reservation reservation, Instant now) {
        if (reservation.getStatus() == ReservationStatus.CONFIRMED) {
            throw new BadRequestException("Rezervarea este deja confirmată și plătită");
        }
        if (reservation.getStatus() != ReservationStatus.PENDING) {
            throw new BadRequestException("Această rezervare nu mai poate fi plătită");
        }
        if (reservation.getHoldExpiresAt() != null && reservation.getHoldExpiresAt().isBefore(now)) {
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
