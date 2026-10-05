package com.bhstays.pms.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bhstays.pms.domain.AuditAction;
import com.bhstays.pms.domain.NotificationType;
import com.bhstays.pms.domain.Payment;
import com.bhstays.pms.domain.PaymentMethod;
import com.bhstays.pms.domain.PaymentProvider;
import com.bhstays.pms.domain.PaymentStatus;
import com.bhstays.pms.domain.Property;
import com.bhstays.pms.domain.Reservation;
import com.bhstays.pms.domain.ReservationSource;
import com.bhstays.pms.domain.ReservationStatus;
import com.bhstays.pms.payment.StripeGateway;
import com.bhstays.pms.repository.PaymentRepository;
import com.bhstays.pms.repository.PaymentWebhookEventRepository;
import com.bhstays.pms.repository.ReservationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.model.Event;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Covers what makes the webhook safe to expose publicly and the only thing
 * that may confirm a card-paid booking: an unverifiable delivery is refused
 * outright; a verified one confirms the booking only when the money was
 * actually captured, for the right session, amount and currency; and every
 * side effect (capture, confirmation, audit, admin notice, guest email)
 * happens exactly once.
 *
 * <p>No Stripe call is made anywhere here - the gateway is mocked, and the
 * service reads ids/statuses/amounts from the raw payload JSON.
 */
@ExtendWith(MockitoExtension.class)
class StripeWebhookServiceTest {

    @Mock
    private StripeGateway stripeGateway;
    @Mock
    private PaymentService paymentService;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private ReservationService reservationService;
    @Mock
    private PaymentWebhookEventRepository paymentWebhookEventRepository;
    @Mock
    private EmailService emailService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private AuditService auditService;

    private StripeWebhookService stripeWebhookService;
    private Reservation reservation;
    private Payment payment;

    private static final String EVENT_ID = "evt_test_1";
    private static final String SESSION_ID = "cs_test_1";
    private static final String SIGNATURE = "t=1,v1=validsignature";

    @BeforeEach
    void setUp() {
        stripeWebhookService = new StripeWebhookService(
                stripeGateway, paymentService, paymentRepository, reservationRepository, reservationService,
                paymentWebhookEventRepository, emailService, notificationService, auditService, new ObjectMapper());

        Property property = Property.builder().name("Casa Mare").build();
        property.setId(UUID.randomUUID());

        reservation = Reservation.builder()
                .property(property)
                .guestFirstName("Ion")
                .guestEmail("ion@example.com")
                .checkInDate(LocalDate.of(2026, 7, 1))
                .checkOutDate(LocalDate.of(2026, 7, 5))
                .status(ReservationStatus.PENDING)
                .source(ReservationSource.DIRECT)
                .managementToken("tok-1")
                .totalAmount(new BigDecimal("500.00"))
                .currency("RON")
                .holdExpiresAt(Instant.now().plusSeconds(600))
                .build();
        reservation.setId(UUID.randomUUID());

        payment = Payment.builder()
                .reservation(reservation)
                .provider(PaymentProvider.STRIPE)
                .method(PaymentMethod.ONLINE_CARD)
                .status(PaymentStatus.PENDING)
                .amount(new BigDecimal("500.00"))
                .currency("RON")
                .checkoutSessionId(SESSION_ID)
                .build();
        payment.setId(UUID.randomUUID());
    }

    // ------------------------------------------------------------------
    // fixtures
    // ------------------------------------------------------------------

    private String sessionPayload(String type, String paymentStatus, long amountTotal, String currency,
                                  UUID metadataReservationId) {
        return """
                {
                  "id": "%s",
                  "type": "%s",
                  "data": { "object": {
                    "id": "%s",
                    "object": "checkout.session",
                    "payment_intent": "pi_test_1",
                    "payment_status": "%s",
                    "amount_total": %d,
                    "currency": "%s",
                    "customer_details": { "email": "ion@example.com", "name": "Ion Popescu" },
                    "metadata": { "reservationId": "%s" }
                  }}
                }
                """.formatted(EVENT_ID, type, SESSION_ID, paymentStatus, amountTotal, currency, metadataReservationId);
    }

    private String paidPayload() {
        return sessionPayload(StripeWebhookService.CHECKOUT_COMPLETED, "paid", 50000, "ron", reservation.getId());
    }

    private void stubVerifiedEvent(String payload, String type) {
        Event event = mock(Event.class);
        lenient().when(event.getId()).thenReturn(EVENT_ID);
        lenient().when(event.getType()).thenReturn(type);
        when(stripeGateway.verifyAndParse(payload, SIGNATURE)).thenReturn(event);
    }

    private void stubFirstDelivery() {
        when(paymentWebhookEventRepository.insertIfAbsent(eq("STRIPE"), eq(EVENT_ID), anyString(), anyString()))
                .thenReturn(1);
    }

    private void stubLockedRows() {
        when(paymentRepository.findByCheckoutSessionIdForUpdate(SESSION_ID)).thenReturn(Optional.of(payment));
        when(reservationRepository.findByIdForUpdate(reservation.getId())).thenReturn(Optional.of(reservation));
    }

    private void stubCapture() {
        when(paymentService.recordCardCapture(payment, "pi_test_1")).thenAnswer(inv -> {
            payment.setStatus(PaymentStatus.SUCCEEDED);
            return payment;
        });
    }

    private void verifyNothingConfirmed() {
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PENDING);
        verify(paymentService, never()).recordCardCapture(any(), any());
        verify(notificationService, never()).notifyAdmins(any(), any(), any(), any());
        verify(emailService, never()).sendPaymentConfirmedEmail(
                any(), any(), any(), any(), any(), any(), any(), any());
    }

    // ------------------------------------------------------------------
    // authenticity / idempotency
    // ------------------------------------------------------------------

    @Test
    void handle_rejectsADeliveryWithAnInvalidSignature_withoutRecordingOrProcessingAnything() {
        String payload = paidPayload();
        doThrow(new StripeGateway.StripeSignatureException("Semnătură Stripe invalidă"))
                .when(stripeGateway).verifyAndParse(payload, "t=1,v1=forged");

        assertThatThrownBy(() -> stripeWebhookService.handle(payload, "t=1,v1=forged"))
                .isInstanceOf(StripeGateway.StripeSignatureException.class);

        verify(paymentWebhookEventRepository, never()).insertIfAbsent(any(), any(), any(), any());
        verifyNothingConfirmed();
    }

    @Test
    void handle_ignoresADuplicateDeliveryOfTheSameEvent() {
        String payload = paidPayload();
        stubVerifiedEvent(payload, StripeWebhookService.CHECKOUT_COMPLETED);
        when(paymentWebhookEventRepository.insertIfAbsent(any(), any(), any(), any())).thenReturn(0);

        stripeWebhookService.handle(payload, SIGNATURE);

        verify(paymentRepository, never()).findByCheckoutSessionIdForUpdate(any());
        verify(paymentWebhookEventRepository, never()).markProcessed(any(), any(), any());
        verifyNothingConfirmed();
    }

    @Test
    void handle_storesOnlyASummaryOfThePayload_neverTheGuestsPersonalData() {
        String payload = paidPayload();
        stubVerifiedEvent(payload, StripeWebhookService.CHECKOUT_COMPLETED);
        stubFirstDelivery();
        stubLockedRows();
        stubCapture();

        stripeWebhookService.handle(payload, SIGNATURE);

        ArgumentCaptor<String> stored = ArgumentCaptor.forClass(String.class);
        verify(paymentWebhookEventRepository).insertIfAbsent(eq("STRIPE"), eq(EVENT_ID), anyString(), stored.capture());
        assertThat(stored.getValue()).contains(SESSION_ID).contains("paid")
                .doesNotContain("ion@example.com").doesNotContain("Ion Popescu");
    }

    // ------------------------------------------------------------------
    // successful payment
    // ------------------------------------------------------------------

    @Test
    void handle_confirmsTheBookingOnceAVerifiedPaidSessionMatchesTheServerAmount() {
        String payload = paidPayload();
        stubVerifiedEvent(payload, StripeWebhookService.CHECKOUT_COMPLETED);
        stubFirstDelivery();
        stubLockedRows();
        stubCapture();

        stripeWebhookService.handle(payload, SIGNATURE);

        verify(paymentService).recordCardCapture(payment, "pi_test_1");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(reservation.getHoldExpiresAt()).isNull();
        verify(auditService).recordSystemEvent(eq(AuditAction.BOOKING_PAYMENT_CONFIRMED), eq("Reservation"),
                eq(reservation.getId()), anyString());
        verify(notificationService, times(1)).notifyAdmins(eq(NotificationType.NEW_PAID_BOOKING), anyString(),
                anyString(), eq("/dashboard/reservations/" + reservation.getId()));
        verify(emailService, times(1)).sendPaymentConfirmedEmail(
                eq("ion@example.com"), eq("Ion"), eq("Casa Mare"), anyString(), anyString(),
                eq("500.00"), eq("RON"), eq("tok-1"));
        verify(paymentWebhookEventRepository).markProcessed("STRIPE", EVENT_ID, null);
    }

    @Test
    void handle_aSecondEventForAnAlreadyCapturedPayment_repeatsNothing() {
        payment.setStatus(PaymentStatus.SUCCEEDED);
        reservation.setStatus(ReservationStatus.CONFIRMED);
        String payload = sessionPayload(StripeWebhookService.CHECKOUT_ASYNC_SUCCEEDED, "paid", 50000, "ron",
                reservation.getId());
        stubVerifiedEvent(payload, StripeWebhookService.CHECKOUT_ASYNC_SUCCEEDED);
        stubFirstDelivery();
        stubLockedRows();

        stripeWebhookService.handle(payload, SIGNATURE);

        verify(paymentService, never()).recordCardCapture(any(), any());
        verify(notificationService, never()).notifyAdmins(any(), any(), any(), any());
        verify(emailService, never()).sendPaymentConfirmedEmail(
                any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void handle_doesNotConfirm_whenCheckoutCompletedButThePaymentIsNotCaptured() {
        String payload = sessionPayload(StripeWebhookService.CHECKOUT_COMPLETED, "unpaid", 50000, "ron",
                reservation.getId());
        stubVerifiedEvent(payload, StripeWebhookService.CHECKOUT_COMPLETED);
        stubFirstDelivery();

        stripeWebhookService.handle(payload, SIGNATURE);

        verifyNothingConfirmed();
        verify(paymentWebhookEventRepository).markProcessed(eq("STRIPE"), eq(EVENT_ID), anyString());
    }

    @Test
    void handle_rejectsAPaidAmountThatDiffersFromTheServerComputedOne() {
        String payload = sessionPayload(StripeWebhookService.CHECKOUT_COMPLETED, "paid", 100, "ron",
                reservation.getId());
        stubVerifiedEvent(payload, StripeWebhookService.CHECKOUT_COMPLETED);
        stubFirstDelivery();
        stubLockedRows();

        stripeWebhookService.handle(payload, SIGNATURE);

        verifyNothingConfirmed();
        verify(auditService).recordSystemEvent(eq(AuditAction.BOOKING_PAYMENT_REJECTED), any(), any(), anyString());
        verify(paymentWebhookEventRepository).markProcessed(eq("STRIPE"), eq(EVENT_ID), anyString());
    }

    @Test
    void handle_rejectsAPaidCurrencyThatDiffersFromTheServerComputedOne() {
        String payload = sessionPayload(StripeWebhookService.CHECKOUT_COMPLETED, "paid", 50000, "eur",
                reservation.getId());
        stubVerifiedEvent(payload, StripeWebhookService.CHECKOUT_COMPLETED);
        stubFirstDelivery();
        stubLockedRows();

        stripeWebhookService.handle(payload, SIGNATURE);

        verifyNothingConfirmed();
    }

    @Test
    void handle_rejectsASessionWhoseMetadataNamesADifferentReservation() {
        String payload = sessionPayload(StripeWebhookService.CHECKOUT_COMPLETED, "paid", 50000, "ron",
                UUID.randomUUID());
        stubVerifiedEvent(payload, StripeWebhookService.CHECKOUT_COMPLETED);
        stubFirstDelivery();
        stubLockedRows();

        stripeWebhookService.handle(payload, SIGNATURE);

        verifyNothingConfirmed();
    }

    @Test
    void handle_rejectsASessionNotLinkedToAnyPayment() {
        String payload = paidPayload();
        stubVerifiedEvent(payload, StripeWebhookService.CHECKOUT_COMPLETED);
        stubFirstDelivery();
        when(paymentRepository.findByCheckoutSessionIdForUpdate(SESSION_ID)).thenReturn(Optional.empty());

        stripeWebhookService.handle(payload, SIGNATURE);

        verifyNothingConfirmed();
    }

    @Test
    void handle_neverConfirmsAManualPayment() {
        payment.setProvider(PaymentProvider.MANUAL);
        payment.setMethod(PaymentMethod.BANK_TRANSFER);
        String payload = paidPayload();
        stubVerifiedEvent(payload, StripeWebhookService.CHECKOUT_COMPLETED);
        stubFirstDelivery();
        stubLockedRows();

        stripeWebhookService.handle(payload, SIGNATURE);

        verifyNothingConfirmed();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void handle_refundsInsteadOfConfirming_whenTheHoldWasReleasedBeforeThePaymentLanded() {
        reservation.setStatus(ReservationStatus.CANCELLED);
        String payload = paidPayload();
        stubVerifiedEvent(payload, StripeWebhookService.CHECKOUT_COMPLETED);
        stubFirstDelivery();
        stubLockedRows();
        stubCapture();

        stripeWebhookService.handle(payload, SIGNATURE);

        verify(paymentService).refund(eq(payment.getId()), any());
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        verify(notificationService, never()).notifyAdmins(any(), any(), any(), any());
        verify(emailService, never()).sendPaymentConfirmedEmail(
                any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void handle_letsAnUnexpectedFailurePropagate_soTheTransactionRollsBackAndStripeRetries() {
        String payload = paidPayload();
        stubVerifiedEvent(payload, StripeWebhookService.CHECKOUT_COMPLETED);
        stubFirstDelivery();
        stubLockedRows();
        when(paymentService.recordCardCapture(any(), any())).thenThrow(new IllegalStateException("db down"));

        assertThatThrownBy(() -> stripeWebhookService.handle(payload, SIGNATURE))
                .isInstanceOf(IllegalStateException.class);

        verify(paymentWebhookEventRepository, never()).markProcessed(any(), any(), any());
        verify(emailService, never()).sendPaymentConfirmedEmail(
                any(), any(), any(), any(), any(), any(), any(), any());
    }

    // ------------------------------------------------------------------
    // expiry / failure
    // ------------------------------------------------------------------

    @Test
    void handle_expiredSession_closesThePaymentAndReleasesTheHold() {
        String payload = sessionPayload(StripeWebhookService.CHECKOUT_EXPIRED, "unpaid", 50000, "ron",
                reservation.getId());
        stubVerifiedEvent(payload, StripeWebhookService.CHECKOUT_EXPIRED);
        stubFirstDelivery();
        stubLockedRows();
        when(paymentService.findOpenCardCheckout(eq(reservation.getId()), any())).thenReturn(Optional.empty());

        stripeWebhookService.handle(payload, SIGNATURE);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        verify(reservationService).releaseHold(eq(reservation), anyString());
        verifyNothingConfirmed();
    }

    @Test
    void handle_expiredSession_keepsTheHold_whenANewerSessionIsStillOpen() {
        String payload = sessionPayload(StripeWebhookService.CHECKOUT_EXPIRED, "unpaid", 50000, "ron",
                reservation.getId());
        stubVerifiedEvent(payload, StripeWebhookService.CHECKOUT_EXPIRED);
        stubFirstDelivery();
        stubLockedRows();
        Payment newer = Payment.builder().reservation(reservation).provider(PaymentProvider.STRIPE).build();
        when(paymentService.findOpenCardCheckout(eq(reservation.getId()), any())).thenReturn(Optional.of(newer));

        stripeWebhookService.handle(payload, SIGNATURE);

        verify(reservationService, never()).releaseHold(any(), any());
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PENDING);
    }

    @Test
    void handle_recordsADeclinedPaymentWithoutConfirmingTheBooking() {
        String payload = """
                {
                  "id": "%s",
                  "type": "payment_intent.payment_failed",
                  "data": { "object": {
                    "id": "pi_test_1",
                    "last_payment_error": { "message": "Card declined" },
                    "metadata": { "reservationId": "%s" }
                  }}
                }
                """.formatted(EVENT_ID, reservation.getId());
        stubVerifiedEvent(payload, StripeWebhookService.PAYMENT_FAILED);
        stubFirstDelivery();
        when(paymentService.findReusableCardPayment(reservation.getId())).thenReturn(Optional.of(payment));

        stripeWebhookService.handle(payload, SIGNATURE);

        verify(paymentService).markCardPaymentFailed(payment, "Card declined");
        verify(paymentService, never()).recordCardCapture(any(), any());
        verify(paymentWebhookEventRepository).markProcessed(eq("STRIPE"), eq(EVENT_ID), isNull());
    }
}
