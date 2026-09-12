package com.bhgroup.pms.payment;

import com.bhgroup.pms.config.AppProperties;
import com.bhgroup.pms.domain.Payment;
import com.bhgroup.pms.domain.PaymentProvider;
import com.bhgroup.pms.domain.PaymentStatus;
import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
import com.stripe.param.RefundCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

/**
 * Stripe adapter built on hosted Checkout: the card form lives on Stripe's
 * own domain, so no card data ever reaches this server - we only handle
 * session/payment-intent identifiers and amounts.
 *
 * <p>Registered only when {@code app.stripe.secret-key} is set. With no key
 * the bean is absent, {@code PaymentService.gateway(STRIPE)} fails fast, and
 * the site keeps working with {@link ManualPaymentGateway} alone.
 *
 * <p>Charging is asynchronous here: {@link #charge} does not move money (the
 * guest pays on Stripe's page), so it reports PROCESSING and the real
 * outcome arrives later through a signature-verified webhook.
 */
@Slf4j
@Component
@ConditionalOnExpression("'${app.stripe.secret-key:}' != ''")
public class StripeGateway implements PaymentGateway {

    /**
     * Currencies Stripe bills in minor units (bani/cents) that this app
     * actually quotes in. Anything else is refused rather than guessed at -
     * sending a wrong exponent would charge 100x the intended amount.
     */
    private static final Set<String> SUPPORTED_CURRENCIES = Set.of("RON", "EUR");

    private final AppProperties appProperties;
    private final StripeClient stripeClient;

    public StripeGateway(AppProperties appProperties) {
        this.appProperties = appProperties;
        this.stripeClient = StripeClient.builder()
                .setApiKey(appProperties.getStripe().getSecretKey())
                .build();
    }

    @PostConstruct
    void warnIfLiveKeyOrMissingWebhookSecret() {
        String secretKey = appProperties.getStripe().getSecretKey();
        if (secretKey != null && secretKey.startsWith("sk_live_")) {
            log.warn("Stripe is running with a LIVE secret key - real cards will be charged");
        }
        if (isBlank(appProperties.getStripe().getWebhookSecret())) {
            log.warn("STRIPE_WEBHOOK_SECRET is not set - webhook deliveries will be rejected, "
                    + "so paid bookings will never be confirmed automatically");
        }
    }

    @Override
    public PaymentProvider getProvider() {
        return PaymentProvider.STRIPE;
    }

    /**
     * Creates a hosted Checkout session for an amount the caller has already
     * recalculated server-side. {@code idempotencyKey} makes a retried
     * request (double click, network retry) return the same session instead
     * of opening a second one for the same booking.
     */
    public StripeCheckoutSession createCheckoutSession(CheckoutSessionRequest request) {
        String currency = normalizeCurrency(request.currency());
        long amountInMinorUnits = toMinorUnits(request.amount());

        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(request.successUrl())
                .setCancelUrl(request.cancelUrl())
                .setCustomerEmail(request.guestEmail())
                .setExpiresAt(request.expiresAt())
                .putMetadata("reservationId", request.reservationId())
                .setPaymentIntentData(SessionCreateParams.PaymentIntentData.builder()
                        // Mirrored onto the PaymentIntent so payment_intent.*
                        // events can be tied back to the reservation too, not
                        // just checkout.session.* ones.
                        .putMetadata("reservationId", request.reservationId())
                        .setDescription(request.description())
                        .build())
                .addLineItem(SessionCreateParams.LineItem.builder()
                        .setQuantity(1L)
                        .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                                .setCurrency(currency)
                                .setUnitAmount(amountInMinorUnits)
                                .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                        .setName(request.propertyName())
                                        .setDescription(request.description())
                                        .build())
                                .build())
                        .build())
                .build();

        RequestOptions options = RequestOptions.builder()
                .setIdempotencyKey(request.idempotencyKey())
                .build();

        try {
            Session session = stripeClient.checkout().sessions().create(params, options);
            return new StripeCheckoutSession(session.getId(), session.getUrl());
        } catch (StripeException ex) {
            log.error("Failed to create Stripe Checkout session for reservation {}", request.reservationId(), ex);
            throw new StripePaymentException("Sesiunea de plată nu a putut fi creată. Încearcă din nou.", ex);
        }
    }

    /**
     * A Stripe charge is never completed inline: the guest pays on Stripe's
     * hosted page and the outcome comes back over the webhook, so this only
     * reports that the payment is in flight.
     */
    @Override
    public PaymentGatewayResult charge(Payment payment) {
        return new PaymentGatewayResult(PaymentStatus.PROCESSING, payment.getProviderPaymentId(), null, null);
    }

    @Override
    public PaymentGatewayResult refund(Payment payment, BigDecimal amount, String reason) {
        String paymentIntentId = payment.getProviderPaymentId();
        if (isBlank(paymentIntentId)) {
            return PaymentGatewayResult.failed(null, null,
                    "Plata nu are un PaymentIntent Stripe asociat, deci nu poate fi rambursată automat");
        }

        RefundCreateParams params = RefundCreateParams.builder()
                .setPaymentIntent(paymentIntentId)
                .setAmount(toMinorUnits(amount))
                .putMetadata("reason", reason != null ? reason : "")
                .build();

        try {
            com.stripe.model.Refund refund = stripeClient.refunds().create(params);
            // Stripe returns "succeeded" immediately for card refunds, but
            // "pending" for methods that settle asynchronously - only the
            // former may reduce the reservation's paid balance right away.
            if ("succeeded".equals(refund.getStatus())) {
                return PaymentGatewayResult.succeeded(refund.getId(), refund.getStatus());
            }
            return new PaymentGatewayResult(PaymentStatus.PROCESSING, refund.getId(), refund.getStatus(), null);
        } catch (StripeException ex) {
            log.error("Stripe refund failed for payment {}", payment.getId(), ex);
            return PaymentGatewayResult.failed(paymentIntentId, null, ex.getMessage());
        }
    }

    /**
     * Interprets an already signature-verified Stripe event. The caller
     * ({@code StripeWebhookService}) owns verification and the idempotency
     * inbox; this only maps event type to outcome.
     */
    @Override
    public PaymentGatewayResult handleWebhookEvent(String eventType, String payload) {
        return switch (eventType) {
            case "checkout.session.completed", "payment_intent.succeeded" ->
                    PaymentGatewayResult.succeeded(null, eventType);
            case "payment_intent.payment_failed" ->
                    PaymentGatewayResult.failed(null, eventType, "Plata a fost refuzată de procesator");
            case "checkout.session.expired" ->
                    new PaymentGatewayResult(PaymentStatus.CANCELLED, null, eventType, "Sesiunea de plată a expirat");
            // Anything else is recorded in the inbox but changes no payment.
            default -> new PaymentGatewayResult(PaymentStatus.PENDING, null, eventType, null);
        };
    }

    /**
     * Verifies the {@code Stripe-Signature} header against the configured
     * webhook secret. Throws for a missing secret, a missing/invalid
     * signature, or a tampered payload - so an unsigned payload can never
     * reach any processing.
     */
    public Event verifyAndParse(String payload, String signatureHeader) {
        String webhookSecret = appProperties.getStripe().getWebhookSecret();
        if (isBlank(webhookSecret)) {
            throw new StripeSignatureException("Webhook-ul Stripe nu este configurat");
        }
        if (isBlank(signatureHeader)) {
            throw new StripeSignatureException("Lipsește semnătura Stripe");
        }
        try {
            return Webhook.constructEvent(payload, signatureHeader, webhookSecret);
        } catch (Exception ex) {
            throw new StripeSignatureException("Semnătură Stripe invalidă", ex);
        }
    }

    public String publishableKey() {
        return appProperties.getStripe().getPublishableKey();
    }

    /**
     * RON and EUR are both two-decimal currencies, so the amount is scaled by
     * 100. {@link #SUPPORTED_CURRENCIES} deliberately excludes everything
     * else rather than assuming an exponent.
     */
    private long toMinorUnits(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact();
    }

    private String normalizeCurrency(String currency) {
        if (currency == null || !SUPPORTED_CURRENCIES.contains(currency.toUpperCase())) {
            throw new StripePaymentException("Moneda " + currency + " nu este acceptată la plata cu cardul", null);
        }
        return currency.toLowerCase();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** What the caller needs to redirect the guest to Stripe's hosted page. */
    public record StripeCheckoutSession(String sessionId, String checkoutUrl) {
    }

    /**
     * Everything the gateway needs to open a session. The amount is passed
     * in already recomputed server-side - this type carries no client input.
     */
    public record CheckoutSessionRequest(
            String reservationId,
            String propertyName,
            String description,
            String guestEmail,
            BigDecimal amount,
            String currency,
            String successUrl,
            String cancelUrl,
            String idempotencyKey,
            Long expiresAt
    ) {
    }

    /** Stripe refused the operation (bad key, API error, unsupported currency). */
    public static class StripePaymentException extends RuntimeException {
        public StripePaymentException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** The delivery could not be proven to come from Stripe. */
    public static class StripeSignatureException extends RuntimeException {
        public StripeSignatureException(String message) {
            super(message);
        }

        public StripeSignatureException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** Reads {@code reservationId} back out of an event's object metadata. */
    public static String reservationIdFromMetadata(Map<String, String> metadata) {
        return metadata != null ? metadata.get("reservationId") : null;
    }
}
