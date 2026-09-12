package com.bhgroup.pms.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bhgroup.pms.domain.Payment;
import com.bhgroup.pms.domain.PaymentProvider;
import com.bhgroup.pms.domain.PaymentStatus;
import com.bhgroup.pms.domain.PaymentWebhookEvent;
import com.bhgroup.pms.domain.Property;
import com.bhgroup.pms.domain.Reservation;
import com.bhgroup.pms.domain.ReservationStatus;
import com.bhgroup.pms.payment.StripeGateway;
import com.bhgroup.pms.repository.PaymentWebhookEventRepository;
import com.bhgroup.pms.repository.ReservationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.model.Event;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Covers what makes the webhook safe to expose publicly: an unverifiable
 * delivery is refused outright, a verified one captures the payment and
 * confirms the booking exactly once, and a payment that arrives after the
 * hold died is refunded instead of overwriting someone else's dates.
 *
 * <p>No Stripe call is made anywhere here - the gateway is mocked, and the
 * service reads ids/metadata from the raw payload JSON.
 */
@ExtendWith(MockitoExtension.class)
class StripeWebhookServiceTest {

    @Mock
    private StripeGateway stripeGateway;
    @Mock
    private PaymentService paymentService;
    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private PaymentWebhookEventRepository paymentWebhookEventRepository;
    @Mock
    private EmailService emailService;

    private StripeWebhookService stripeWebhookService;
    private Reservation reservation;
    private Payment payment;

    private static final String EVENT_ID = "evt_test_1";
    private static final String SIGNATURE = "t=1,v1=validsignature";

    @BeforeEach
    void setUp() {
        stripeWebhookService = new StripeWebhookService(
                stripeGateway, paymentService, reservationRepository,
                paymentWebhookEventRepository, emailService, new ObjectMapper());

        Property property = Property.builder().name("Casa Mare").build();
        property.setId(UUID.randomUUID());

        reservation = Reservation.builder()
                .property(property)
                .guestFirstName("Ion")
                .guestEmail("ion@example.com")
                .checkInDate(LocalDate.of(2026, 7, 1))
                .checkOutDate(LocalDate.of(2026, 7, 5))
                .status(ReservationStatus.PENDING)
                .managementToken("tok-1")
                .currency("RON")
                .build();
        reservation.setId(UUID.randomUUID());

        payment = Payment.builder()
                .reservation(reservation)
                .provider(PaymentProvider.STRIPE)
                .status(PaymentStatus.PENDING)
                .amount(new BigDecimal("500.00"))
                .currency("RON")
                .build();
        payment.setId(UUID.randomUUID());
    }

    private String checkoutCompletedPayload() {
        return """
                {
                  "id": "%s",
                  "type": "checkout.session.completed",
                  "data": { "object": {
                    "id": "cs_test_1",
                    "payment_intent": "pi_test_1",
                    "metadata": { "reservationId": "%s" }
                  }}
                }
                """.formatted(EVENT_ID, reservation.getId());
    }

    private Event stubVerifiedEvent(String payload, String type) {
        Event event = mock(Event.class);
        when(event.getId()).thenReturn(EVENT_ID);
        when(event.getType()).thenReturn(type);
        when(stripeGateway.verifyAndParse(payload, SIGNATURE)).thenReturn(event);
        return event;
    }

    private void stubInboxAccepts() {
        when(paymentWebhookEventRepository.saveAndFlush(any(PaymentWebhookEvent.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void handle_rejectsADeliveryWithAnInvalidSignature_withoutRecordingOrProcessingAnything() {
        String payload = checkoutCompletedPayload();
        doThrow(new StripeGateway.StripeSignatureException("Semnătură Stripe invalidă"))
                .when(stripeGateway).verifyAndParse(payload, "t=1,v1=forged");

        assertThatThrownBy(() -> stripeWebhookService.handle(payload, "t=1,v1=forged"))
                .isInstanceOf(StripeGateway.StripeSignatureException.class);

        verify(paymentWebhookEventRepository, never()).saveAndFlush(any());
        verify(paymentService, never()).markCardPaymentSucceeded(any(), anyString());
        verify(emailService, never()).sendPaymentConfirmedEmail(
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void handle_capturesThePaymentAndEmailsTheGuest_whenTheBookingGetsConfirmed() {
        String payload = checkoutCompletedPayload();
        stubVerifiedEvent(payload, "checkout.session.completed");
        stubInboxAccepts();
        when(reservationRepository.findById(reservation.getId())).thenReturn(Optional.of(reservation));
        when(paymentService.findReusableCardPayment(reservation.getId())).thenReturn(Optional.of(payment));
        when(paymentService.markCardPaymentSucceeded(eq(payment), eq("pi_test_1"))).thenAnswer(inv -> {
            // PaymentService confirms the reservation as part of capturing.
            reservation.setStatus(ReservationStatus.CONFIRMED);
            return payment;
        });

        stripeWebhookService.handle(payload, SIGNATURE);

        verify(paymentService).markCardPaymentSucceeded(payment, "pi_test_1");
        verify(emailService).sendPaymentConfirmedEmail(
                eq("ion@example.com"), eq("Ion"), eq("Casa Mare"),
                eq("2026-07-01"), eq("2026-07-05"), eq("500.00"), eq("RON"), eq("tok-1"));
        verify(paymentService, never()).refund(any(), any());
    }

    @Test
    void handle_ignoresARedeliveredEvent_soNothingIsCapturedOrConfirmedTwice() {
        String payload = checkoutCompletedPayload();
        stubVerifiedEvent(payload, "checkout.session.completed");
        // The unique (provider, external_event_id) index rejects the second insert.
        when(paymentWebhookEventRepository.saveAndFlush(any(PaymentWebhookEvent.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        stripeWebhookService.handle(payload, SIGNATURE);

        verify(paymentService, never()).markCardPaymentSucceeded(any(), anyString());
        verify(emailService, never()).sendPaymentConfirmedEmail(
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void handle_capturesOnceAcrossTwoDeliveriesOfTheSameEvent() {
        String payload = checkoutCompletedPayload();
        stubVerifiedEvent(payload, "checkout.session.completed");
        when(paymentWebhookEventRepository.saveAndFlush(any(PaymentWebhookEvent.class)))
                .thenAnswer(inv -> inv.getArgument(0))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));
        when(reservationRepository.findById(reservation.getId())).thenReturn(Optional.of(reservation));
        when(paymentService.findReusableCardPayment(reservation.getId())).thenReturn(Optional.of(payment));
        when(paymentService.markCardPaymentSucceeded(eq(payment), eq("pi_test_1"))).thenAnswer(inv -> {
            reservation.setStatus(ReservationStatus.CONFIRMED);
            return payment;
        });

        stripeWebhookService.handle(payload, SIGNATURE);
        stripeWebhookService.handle(payload, SIGNATURE);

        verify(paymentService, times(1)).markCardPaymentSucceeded(payment, "pi_test_1");
        verify(emailService, times(1)).sendPaymentConfirmedEmail(
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void handle_refundsAPaymentThatLandsAfterTheHoldExpired_ratherThanConfirmingOverIt() {
        reservation.setStatus(ReservationStatus.CANCELLED);
        String payload = checkoutCompletedPayload();
        stubVerifiedEvent(payload, "checkout.session.completed");
        stubInboxAccepts();
        when(reservationRepository.findById(reservation.getId())).thenReturn(Optional.of(reservation));
        when(paymentService.findReusableCardPayment(reservation.getId())).thenReturn(Optional.of(payment));
        when(paymentService.markCardPaymentSucceeded(eq(payment), eq("pi_test_1"))).thenReturn(payment);

        stripeWebhookService.handle(payload, SIGNATURE);

        verify(paymentService).refund(eq(payment.getId()), any());
        verify(emailService, never()).sendPaymentConfirmedEmail(
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
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
        stubVerifiedEvent(payload, "payment_intent.payment_failed");
        stubInboxAccepts();
        when(reservationRepository.findById(reservation.getId())).thenReturn(Optional.of(reservation));
        when(paymentService.findReusableCardPayment(reservation.getId())).thenReturn(Optional.of(payment));

        stripeWebhookService.handle(payload, SIGNATURE);

        verify(paymentService).markCardPaymentFailed(payment, "Card declined");
        verify(paymentService, never()).markCardPaymentSucceeded(any(), anyString());
    }
}
