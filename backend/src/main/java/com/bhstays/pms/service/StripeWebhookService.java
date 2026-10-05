package com.bhstays.pms.service;

import com.bhstays.pms.domain.AuditAction;
import com.bhstays.pms.domain.NotificationType;
import com.bhstays.pms.domain.Payment;
import com.bhstays.pms.domain.PaymentMethod;
import com.bhstays.pms.domain.PaymentProvider;
import com.bhstays.pms.domain.PaymentStatus;
import com.bhstays.pms.domain.Reservation;
import com.bhstays.pms.domain.ReservationSource;
import com.bhstays.pms.domain.ReservationStatus;
import com.bhstays.pms.dto.payment.RefundCreateRequest;
import com.bhstays.pms.payment.StripeGateway;
import com.bhstays.pms.repository.PaymentRepository;
import com.bhstays.pms.repository.PaymentWebhookEventRepository;
import com.bhstays.pms.repository.ReservationRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.stripe.model.Event;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Processes signature-verified Stripe webhooks. A direct booking paid by
 * card is confirmed here - and only here: the guest's browser returning to
 * the success page proves nothing and changes nothing.
 *
 * <p>Guarantees:
 * <ul>
 *   <li><b>Authenticity</b> - {@link StripeGateway#verifyAndParse} runs
 *       first, so an unsigned or tampered payload never reaches any of this.
 *   <li><b>Right payment, right booking</b> - the event is matched to its
 *       payment by the Checkout session id we stored when opening it, and
 *       the session's {@code reservationId} metadata must name that same
 *       booking. Nothing from the browser is consulted.
 *   <li><b>Money actually captured</b> - only {@code payment_status=paid}
 *       confirms; the session's {@code amount_total} and {@code currency}
 *       must equal what the server computed and stored on the payment.
 *   <li><b>All or nothing</b> - payment SUCCEEDED, CHARGE ledger entry,
 *       reservation CONFIRMED, audit row and admin notification are written
 *       in one transaction under row locks; the guest email is sent only
 *       after that transaction commits. An unexpected failure rolls all of
 *       it back (inbox row included) and answers 5xx, so Stripe retries.
 *   <li><b>Idempotency</b> - the event id goes into the
 *       {@code payment_webhook_events} inbox via {@code ON CONFLICT DO
 *       NOTHING}; a re-delivered event is a no-op. A different event for an
 *       already-captured payment is a no-op too, so nothing - payment,
 *       confirmation, email or notification - ever happens twice.
 *   <li><b>No double booking</b> - a payment that lands after its hold was
 *       released never resurrects the reservation (whose dates may already
 *       belong to someone else); it is refunded in full instead.
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnExpression("'${app.stripe.secret-key:}' != ''")
public class StripeWebhookService {

    static final String CHECKOUT_COMPLETED = "checkout.session.completed";
    static final String CHECKOUT_ASYNC_SUCCEEDED = "checkout.session.async_payment_succeeded";
    static final String CHECKOUT_ASYNC_FAILED = "checkout.session.async_payment_failed";
    static final String CHECKOUT_EXPIRED = "checkout.session.expired";
    static final String PAYMENT_FAILED = "payment_intent.payment_failed";

    private static final String PROVIDER = PaymentProvider.STRIPE.name();
    private static final Set<PaymentStatus> CAPTURED_STATUSES = Set.of(
            PaymentStatus.SUCCEEDED, PaymentStatus.PARTIALLY_REFUNDED, PaymentStatus.REFUNDED);

    private final StripeGateway stripeGateway;
    private final PaymentService paymentService;
    private final PaymentRepository paymentRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationService reservationService;
    private final PaymentWebhookEventRepository paymentWebhookEventRepository;
    private final EmailService emailService;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    /**
     * @throws StripeGateway.StripeSignatureException if the delivery cannot be
     *         proven to come from Stripe - nothing is recorded or processed.
     */
    @Transactional
    public void handle(String payload, String signatureHeader) {
        Event event = stripeGateway.verifyAndParse(payload, signatureHeader);
        JsonNode dataObject = dataObject(payload);

        int inserted = paymentWebhookEventRepository.insertIfAbsent(
                PROVIDER, event.getId(), event.getType(), inboxSummary(event, dataObject));
        if (inserted == 0) {
            log.info("Ignoring duplicate Stripe webhook event {} ({})", event.getId(), event.getType());
            return;
        }

        // A business-level rejection (wrong amount, unknown session...) is
        // returned as a note and recorded; retrying it could never succeed.
        // Anything thrown propagates instead: the whole transaction - inbox
        // row included - rolls back and Stripe redelivers the event later.
        String rejection = process(event, dataObject);
        if (rejection != null) {
            log.warn("Stripe event {} ({}) not applied: {}", event.getId(), event.getType(), rejection);
        }
        paymentWebhookEventRepository.markProcessed(PROVIDER, event.getId(), truncate(rejection));
    }

    private String process(Event event, JsonNode dataObject) {
        return switch (event.getType()) {
            case CHECKOUT_COMPLETED, CHECKOUT_ASYNC_SUCCEEDED -> capture(event.getId(), dataObject);
            case CHECKOUT_EXPIRED -> expire(dataObject);
            case CHECKOUT_ASYNC_FAILED -> failSession(dataObject);
            case PAYMENT_FAILED -> failPaymentIntent(dataObject);
            default -> {
                log.debug("Stripe event {} needs no action", event.getType());
                yield null;
            }
        };
    }

    // ------------------------------------------------------------------
    // Successful payment
    // ------------------------------------------------------------------

    private String capture(String eventId, JsonNode session) {
        String paymentStatus = session.path("payment_status").asText("");
        if (!"paid".equals(paymentStatus)) {
            // e.g. a delayed payment method: checkout completed, money not yet
            // captured. checkout.session.async_payment_succeeded follows if it is.
            return "Checkout completed without a captured payment (payment_status=" + paymentStatus + ")";
        }

        Optional<Payment> found = paymentRepository.findByCheckoutSessionIdForUpdate(session.path("id").asText(""));
        if (found.isEmpty()) {
            return "No card payment is linked to this Checkout session";
        }
        Payment payment = found.get();
        Reservation reservation = reservationRepository.findByIdForUpdate(payment.getReservation().getId())
                .orElse(null);
        if (reservation == null) {
            return "The payment's reservation no longer exists";
        }

        String mismatch = verifyMatchesServerRecord(session, payment, reservation);
        if (mismatch != null) {
            auditService.recordSystemEvent(AuditAction.BOOKING_PAYMENT_REJECTED, "Reservation", reservation.getId(),
                    "Plata Stripe nu a fost aplicată (eveniment " + eventId + "): " + mismatch);
            return mismatch;
        }

        if (CAPTURED_STATUSES.contains(payment.getStatus())) {
            // A different event for the same payment (e.g. completed followed
            // by async_payment_succeeded): already applied, nothing to repeat.
            return null;
        }

        paymentService.recordCardCapture(payment, paymentIntentId(session));

        if (reservation.getStatus() != ReservationStatus.PENDING) {
            // The hold was released (or the booking cancelled) before the money
            // landed. Those dates are no longer ours to sell, so give it back
            // rather than confirm over whoever holds them now.
            log.error("Stripe event {} paid reservation {} which was already {} - refunding in full",
                    eventId, reservation.getId(), reservation.getStatus());
            paymentService.refund(payment.getId(), new RefundCreateRequest(
                    payment.getAmount(), "Plata a ajuns după expirarea rezervării - rambursare automată"));
            auditService.recordSystemEvent(AuditAction.BOOKING_LATE_PAYMENT_REFUNDED, "Reservation",
                    reservation.getId(), "Plată Stripe primită după eliberarea perioadei - rambursată integral (eveniment "
                            + eventId + ")");
            return null;
        }

        // Still PENDING means the hold never lapsed: the GiST no-overlap
        // constraint has kept these dates ours the whole time.
        reservation.setStatus(ReservationStatus.CONFIRMED);
        reservation.setHoldExpiresAt(null);
        reservationRepository.save(reservation);

        auditService.recordSystemEvent(AuditAction.BOOKING_PAYMENT_CONFIRMED, "Reservation", reservation.getId(),
                "Plată Stripe confirmată prin webhook semnat (eveniment " + eventId + ", "
                        + payment.getAmount().toPlainString() + " " + payment.getCurrency()
                        + ") - rezervare confirmată automat");

        // No guest PII in the in-app notification; the link leads to the details.
        notificationService.notifyAdmins(NotificationType.NEW_PAID_BOOKING,
                "Rezervare nouă plătită și confirmată",
                "%s · %s → %s · %s %s".formatted(reservation.getProperty().getName(),
                        reservation.getCheckInDate(), reservation.getCheckOutDate(),
                        payment.getAmount().toPlainString(), payment.getCurrency()),
                "/dashboard/reservations/" + reservation.getId());

        // Dispatched by EmailService only after this transaction commits.
        emailService.sendPaymentConfirmedEmail(
                reservation.getGuestEmail(), reservation.getGuestFirstName(), reservation.getProperty().getName(),
                reservation.getCheckInDate().toString(), reservation.getCheckOutDate().toString(),
                payment.getAmount().toPlainString(), payment.getCurrency(), reservation.getManagementToken());

        log.info("Reservation {} confirmed after verified Stripe payment (event {})", reservation.getId(), eventId);
        return null;
    }

    /**
     * Everything Stripe reports about the money must agree with what this
     * server computed and stored before sending the guest to pay.
     */
    private String verifyMatchesServerRecord(JsonNode session, Payment payment, Reservation reservation) {
        if (payment.getProvider() != PaymentProvider.STRIPE || payment.getMethod() != PaymentMethod.ONLINE_CARD) {
            return "The linked payment is not an online card payment";
        }
        if (reservation.getSource() != ReservationSource.DIRECT) {
            return "Only direct bookings are confirmed by card payment";
        }
        UUID metadataReservationId = reservationId(session);
        if (!reservation.getId().equals(metadataReservationId)) {
            return "Checkout session metadata does not match the payment's reservation";
        }
        JsonNode amountTotal = session.path("amount_total");
        if (!amountTotal.canConvertToLong()
                || amountTotal.asLong() != StripeGateway.toMinorUnits(payment.getAmount())) {
            return "Paid amount does not match the server-computed amount";
        }
        if (!payment.getCurrency().equalsIgnoreCase(session.path("currency").asText(""))) {
            return "Paid currency does not match the server-computed currency";
        }
        if (reservation.getTotalAmount() == null
                || reservation.getTotalAmount().compareTo(payment.getAmount()) != 0
                || !payment.getCurrency().equalsIgnoreCase(reservation.getCurrency())) {
            return "Paid amount no longer matches the reservation total";
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Expired session / failed payment
    // ------------------------------------------------------------------

    /**
     * The session can no longer be paid: close the payment and, unless the
     * guest already has a newer session open, release the hold.
     */
    private String expire(JsonNode session) {
        Optional<Payment> found = paymentRepository.findByCheckoutSessionIdForUpdate(session.path("id").asText(""));
        if (found.isEmpty()) {
            // Typically a session that was already replaced by a newer one.
            return null;
        }
        Payment payment = found.get();
        if (CAPTURED_STATUSES.contains(payment.getStatus()) || payment.getStatus() == PaymentStatus.CANCELLED) {
            return null;
        }
        Reservation reservation = reservationRepository.findByIdForUpdate(payment.getReservation().getId())
                .orElse(null);

        payment.setStatus(PaymentStatus.CANCELLED);
        paymentRepository.save(payment);

        if (reservation != null
                && reservation.getStatus() == ReservationStatus.PENDING
                && reservation.getSource() == ReservationSource.DIRECT
                && paymentService.findOpenCardCheckout(reservation.getId(), Instant.now()).isEmpty()) {
            reservationService.releaseHold(reservation, "Sesiunea de plată Stripe a expirat fără plată");
            log.info("Released hold for reservation {} after its Checkout session expired", reservation.getId());
        }
        return null;
    }

    private String failSession(JsonNode session) {
        Optional<Payment> found = paymentRepository.findByCheckoutSessionIdForUpdate(session.path("id").asText(""));
        if (found.isEmpty()) {
            return "No card payment is linked to this Checkout session";
        }
        paymentService.markCardPaymentFailed(found.get(), "Plata nu a putut fi finalizată");
        return null;
    }

    /**
     * A declined attempt. The session stays open - Stripe lets the guest try
     * another card on the same page - so the booking keeps its hold.
     */
    private String failPaymentIntent(JsonNode paymentIntent) {
        UUID reservationId = reservationId(paymentIntent);
        if (reservationId == null) {
            return "Payment intent carries no reservationId metadata";
        }
        Optional<Payment> payment = paymentService.findReusableCardPayment(reservationId);
        if (payment.isEmpty()) {
            return null;
        }
        paymentService.markCardPaymentFailed(payment.get(), failureMessage(paymentIntent));
        log.info("Card payment declined for reservation {}", reservationId);
        return null;
    }

    // ------------------------------------------------------------------
    // Payload helpers - we only ever read ids, statuses and amounts.
    // ------------------------------------------------------------------

    /** Stripe wraps the entity under {@code data.object}. */
    private JsonNode dataObject(String payload) {
        try {
            return objectMapper.readTree(payload).path("data").path("object");
        } catch (Exception ex) {
            throw new IllegalStateException("Stripe event payload could not be parsed", ex);
        }
    }

    /**
     * What the inbox keeps of the delivery: identifiers, statuses and
     * amounts - never the full payload, which carries the guest's name,
     * email and billing details.
     */
    private String inboxSummary(Event event, JsonNode dataObject) {
        ObjectNode summary = objectMapper.createObjectNode();
        summary.put("id", event.getId());
        summary.put("type", event.getType());
        summary.put("objectId", dataObject.path("id").asText(null));
        summary.put("objectType", dataObject.path("object").asText(null));
        summary.put("status", dataObject.path("status").asText(null));
        summary.put("paymentStatus", dataObject.path("payment_status").asText(null));
        if (dataObject.has("amount_total")) {
            summary.set("amountTotal", dataObject.get("amount_total"));
        }
        summary.put("currency", dataObject.path("currency").asText(null));
        summary.put("reservationId", dataObject.path("metadata").path("reservationId").asText(null));
        return summary.toString();
    }

    private UUID reservationId(JsonNode dataObject) {
        String raw = dataObject.path("metadata").path("reservationId").asText(null);
        try {
            return raw != null ? UUID.fromString(raw) : null;
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /** On a checkout.session.* event the PaymentIntent is a nested id; refunds are issued against it. */
    private String paymentIntentId(JsonNode session) {
        String nested = session.path("payment_intent").asText(null);
        return nested != null && !nested.isBlank() ? nested : null;
    }

    private String failureMessage(JsonNode paymentIntent) {
        String message = paymentIntent.path("last_payment_error").path("message").asText(null);
        return message != null && !message.isBlank() ? truncate(message, 500) : "Plata a fost refuzată";
    }

    private String truncate(String message) {
        return message == null ? null : truncate(message, 1000);
    }

    private static String truncate(String message, int max) {
        return message.length() > max ? message.substring(0, max) : message;
    }
}
