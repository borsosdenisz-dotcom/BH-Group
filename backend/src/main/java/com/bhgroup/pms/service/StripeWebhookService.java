package com.bhgroup.pms.service;

import com.bhgroup.pms.domain.Payment;
import com.bhgroup.pms.domain.PaymentProvider;
import com.bhgroup.pms.domain.PaymentWebhookEvent;
import com.bhgroup.pms.domain.Reservation;
import com.bhgroup.pms.domain.ReservationStatus;
import com.bhgroup.pms.dto.payment.RefundCreateRequest;
import com.bhgroup.pms.payment.StripeGateway;
import com.bhgroup.pms.repository.PaymentWebhookEventRepository;
import com.bhgroup.pms.repository.ReservationRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.model.Event;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Processes signature-verified Stripe webhooks: captures the payment,
 * confirms the booking that was waiting on it, and emails the guest.
 *
 * <p>Three guarantees hold here:
 * <ul>
 *   <li><b>Authenticity</b> - {@link StripeGateway#verifyAndParse} runs
 *       first, so an unsigned or tampered payload never reaches any of this.
 *   <li><b>Idempotency</b> - the event id is inserted into the
 *       {@code payment_webhook_events} inbox before processing. Its unique
 *       (provider, external_event_id) index, not a read-then-write check, is
 *       what makes a re-delivered event a no-op even under concurrency.
 *   <li><b>No double booking</b> - a payment that lands after its hold
 *       expired never resurrects the reservation (whose dates may already
 *       belong to someone else); it is refunded in full instead.
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnExpression("'${app.stripe.secret-key:}' != ''")
public class StripeWebhookService {

    private static final String CHECKOUT_COMPLETED = "checkout.session.completed";
    private static final String PAYMENT_SUCCEEDED = "payment_intent.succeeded";
    private static final String PAYMENT_FAILED = "payment_intent.payment_failed";

    private final StripeGateway stripeGateway;
    private final PaymentService paymentService;
    private final ReservationRepository reservationRepository;
    private final PaymentWebhookEventRepository paymentWebhookEventRepository;
    private final EmailService emailService;
    private final ObjectMapper objectMapper;

    /**
     * @throws StripeGateway.StripeSignatureException if the delivery cannot be
     *         proven to come from Stripe - nothing is recorded or processed.
     */
    @Transactional
    public void handle(String payload, String signatureHeader) {
        Event event = stripeGateway.verifyAndParse(payload, signatureHeader);

        PaymentWebhookEvent inboxEntry;
        try {
            inboxEntry = paymentWebhookEventRepository.saveAndFlush(PaymentWebhookEvent.builder()
                    .provider(PaymentProvider.STRIPE)
                    .externalEventId(event.getId())
                    .eventType(event.getType())
                    .payload(payload)
                    .build());
        } catch (DataIntegrityViolationException duplicate) {
            // The unique index rejected it: this exact event was already
            // recorded, so it has already been processed. Nothing to do.
            log.info("Ignoring duplicate Stripe webhook event {} ({})", event.getId(), event.getType());
            return;
        }

        try {
            process(event, payload);
        } catch (Exception ex) {
            // Recorded, not rethrown: Stripe retries on a non-2xx, and a
            // retry would hit the idempotency guard above and do nothing.
            // The inbox row keeps the failure visible for staff instead.
            log.error("Failed to process Stripe webhook event {} ({})", event.getId(), event.getType(), ex);
            inboxEntry.setProcessingError(truncate(ex.getMessage()));
        }

        inboxEntry.setProcessedAt(Instant.now());
        paymentWebhookEventRepository.save(inboxEntry);
    }

    private void process(Event event, String payload) {
        String eventType = event.getType();
        if (!CHECKOUT_COMPLETED.equals(eventType)
                && !PAYMENT_SUCCEEDED.equals(eventType)
                && !PAYMENT_FAILED.equals(eventType)) {
            log.debug("Stripe event {} needs no action", eventType);
            return;
        }

        JsonNode dataObject = dataObject(payload);
        UUID reservationId = reservationId(dataObject);
        if (reservationId == null) {
            log.warn("Stripe event {} carries no reservationId metadata - nothing to tie it to", event.getId());
            return;
        }

        Reservation reservation = reservationRepository.findById(reservationId).orElse(null);
        if (reservation == null) {
            log.warn("Stripe event {} references unknown reservation {}", event.getId(), reservationId);
            return;
        }

        Optional<Payment> cardPayment = paymentService.findReusableCardPayment(reservationId);
        if (cardPayment.isEmpty()) {
            log.warn("No in-flight card payment for reservation {} (event {})", reservationId, event.getId());
            return;
        }

        if (PAYMENT_FAILED.equals(eventType)) {
            paymentService.markCardPaymentFailed(cardPayment.get(), failureMessage(dataObject));
            log.info("Card payment declined for reservation {}", reservationId);
            return;
        }

        capture(reservation, cardPayment.get(), paymentIntentId(dataObject), event.getId());
    }

    private void capture(Reservation reservation, Payment payment, String paymentIntentId, String eventId) {
        boolean wasOnHold = reservation.getStatus() == ReservationStatus.PENDING;

        // Marks SUCCEEDED, records the CHARGE transaction, and - when the
        // booking is fully paid and still on hold - flips it to CONFIRMED.
        paymentService.markCardPaymentSucceeded(payment, paymentIntentId);

        if (!wasOnHold) {
            // The hold expired (or the booking was cancelled) before the money
            // landed. Those dates are no longer ours to sell, so give it back
            // rather than confirm over whoever holds them now.
            log.error("Stripe event {} paid reservation {} which was already {} - refunding in full",
                    eventId, reservation.getId(), reservation.getStatus());
            paymentService.refund(payment.getId(), new RefundCreateRequest(
                    payment.getAmount(), "Plata a ajuns după expirarea rezervării - rambursare automată"));
            return;
        }

        reservationRepository.findById(reservation.getId()).ifPresent(fresh -> {
            if (fresh.getStatus() == ReservationStatus.CONFIRMED) {
                emailService.sendPaymentConfirmedEmail(
                        fresh.getGuestEmail(), fresh.getGuestFirstName(), fresh.getProperty().getName(),
                        fresh.getCheckInDate().toString(), fresh.getCheckOutDate().toString(),
                        payment.getAmount().toPlainString(), payment.getCurrency(), fresh.getManagementToken());
                log.info("Reservation {} confirmed after successful card payment", fresh.getId());
            }
        });
    }

    /** Stripe wraps the entity under {@code data.object}; we only read ids and metadata from it. */
    private JsonNode dataObject(String payload) {
        try {
            return objectMapper.readTree(payload).path("data").path("object");
        } catch (Exception ex) {
            throw new IllegalStateException("Stripe event payload could not be parsed", ex);
        }
    }

    private UUID reservationId(JsonNode dataObject) {
        String raw = dataObject.path("metadata").path("reservationId").asText(null);
        try {
            return raw != null ? UUID.fromString(raw) : null;
        } catch (IllegalArgumentException ex) {
            log.warn("Stripe metadata carried an unparsable reservationId: {}", raw);
            return null;
        }
    }

    /**
     * On a checkout.session.* event the PaymentIntent is a nested id; on a
     * payment_intent.* event the object itself is the PaymentIntent. Either
     * way this is the reference refunds are issued against.
     */
    private String paymentIntentId(JsonNode dataObject) {
        String nested = dataObject.path("payment_intent").asText(null);
        return nested != null && !nested.isBlank() ? nested : dataObject.path("id").asText(null);
    }

    private String failureMessage(JsonNode dataObject) {
        String message = dataObject.path("last_payment_error").path("message").asText(null);
        return message != null && !message.isBlank() ? message : "Plata a fost refuzată";
    }

    private String truncate(String message) {
        if (message == null) {
            return "Unknown error";
        }
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }
}
