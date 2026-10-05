package com.bhstays.pms.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bhstays.pms.domain.Payment;
import com.bhstays.pms.domain.PaymentMethod;
import com.bhstays.pms.domain.PaymentProvider;
import com.bhstays.pms.domain.PaymentStatus;
import com.bhstays.pms.domain.Property;
import com.bhstays.pms.domain.Refund;
import com.bhstays.pms.domain.RefundStatus;
import com.bhstays.pms.domain.Reservation;
import com.bhstays.pms.domain.ReservationStatus;
import com.bhstays.pms.dto.payment.RefundCreateRequest;
import com.bhstays.pms.payment.PaymentGateway;
import com.bhstays.pms.payment.PaymentGatewayResult;
import com.bhstays.pms.repository.PaymentRepository;
import com.bhstays.pms.repository.PaymentTransactionRepository;
import com.bhstays.pms.repository.PaymentWebhookEventRepository;
import com.bhstays.pms.repository.RefundRepository;
import com.bhstays.pms.repository.ReservationRepository;
import com.bhstays.pms.service.mapper.PaymentMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Card-payment bookkeeping around the Stripe gateway: starting a payment for
 * a checkout session, capturing it once, and refunding it. The gateway
 * itself is mocked - no Stripe call is made.
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceCardTest {

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private PaymentTransactionRepository paymentTransactionRepository;
    @Mock
    private RefundRepository refundRepository;
    @Mock
    private PaymentWebhookEventRepository paymentWebhookEventRepository;
    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private ReservationService reservationService;
    @Mock
    private PaymentMapper paymentMapper;
    @Mock
    private PaymentGateway stripeGateway;

    private PaymentService paymentService;
    private Reservation reservation;

    @BeforeEach
    void setUp() {
        when(stripeGateway.getProvider()).thenReturn(PaymentProvider.STRIPE);

        paymentService = new PaymentService(
                paymentRepository, paymentTransactionRepository, refundRepository,
                paymentWebhookEventRepository, reservationRepository, reservationService,
                paymentMapper, List.of(stripeGateway));
        paymentService.indexGateways();

        Property property = Property.builder().name("Casa Mare").build();
        property.setId(UUID.randomUUID());
        reservation = Reservation.builder()
                .property(property)
                .checkInDate(LocalDate.of(2026, 7, 1))
                .checkOutDate(LocalDate.of(2026, 7, 5))
                .status(ReservationStatus.PENDING)
                .totalAmount(new BigDecimal("500.00"))
                .currency("RON")
                .build();
        reservation.setId(UUID.randomUUID());
    }

    private Payment cardPayment(PaymentStatus status) {
        Payment payment = Payment.builder()
                .reservation(reservation)
                .provider(PaymentProvider.STRIPE)
                .method(PaymentMethod.ONLINE_CARD)
                .status(status)
                .amount(new BigDecimal("500.00"))
                .currency("RON")
                .providerPaymentId("pi_test_1")
                .build();
        payment.setId(UUID.randomUUID());
        return payment;
    }

    @Test
    void startOnlineCardPayment_createsAPendingCardPaymentForTheQuotedAmount() {
        when(paymentRepository.findByReservationIdOrderByCreatedAtDesc(reservation.getId())).thenReturn(List.of());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        Payment payment = paymentService.startOnlineCardPayment(reservation, new BigDecimal("500.00"), "RON");

        assertThat(payment.getProvider()).isEqualTo(PaymentProvider.STRIPE);
        assertThat(payment.getMethod()).isEqualTo(PaymentMethod.ONLINE_CARD);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getAmount()).isEqualByComparingTo("500.00");
        assertThat(payment.getCurrency()).isEqualTo("RON");
    }

    @Test
    void startOnlineCardPayment_reusesTheInFlightPayment_insteadOfPilingUpRows() {
        Payment existing = cardPayment(PaymentStatus.PENDING);
        when(paymentRepository.findByReservationIdOrderByCreatedAtDesc(reservation.getId()))
                .thenReturn(List.of(existing));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        Payment payment = paymentService.startOnlineCardPayment(reservation, new BigDecimal("540.00"), "RON");

        assertThat(payment.getId()).isEqualTo(existing.getId());
        assertThat(payment.getAmount()).isEqualByComparingTo("540.00");
    }

    @Test
    void recordCardCapture_capturesThePaymentWithoutTouchingTheReservation() {
        Payment payment = cardPayment(PaymentStatus.PENDING);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        paymentService.recordCardCapture(payment, "pi_test_2");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(payment.getProviderPaymentId()).isEqualTo("pi_test_2");
        verify(paymentTransactionRepository).save(any());
        // confirming the booking is the verified webhook's decision, not a side effect here
        verify(reservationService, never()).updateStatus(any(), any());
    }

    @Test
    void markCardPaymentFailed_leavesACapturedPaymentAlone() {
        Payment payment = cardPayment(PaymentStatus.SUCCEEDED);

        paymentService.markCardPaymentFailed(payment, "Card declined");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        verify(paymentTransactionRepository, never()).save(any());
    }

    @Test
    void findOpenCardCheckout_countsADeclinedPaymentWhoseSessionIsStillOpen() {
        Instant now = Instant.now();
        Payment declined = cardPayment(PaymentStatus.FAILED);
        declined.setCheckoutSessionId("cs_open");
        declined.setCheckoutExpiresAt(now.plus(10, ChronoUnit.MINUTES));
        when(paymentRepository.findByReservationIdOrderByCreatedAtDesc(reservation.getId()))
                .thenReturn(List.of(declined));

        assertThat(paymentService.findOpenCardCheckout(reservation.getId(), now)).contains(declined);
    }

    @Test
    void findOpenCardCheckout_ignoresAnExpiredSession() {
        Instant now = Instant.now();
        Payment stale = cardPayment(PaymentStatus.PENDING);
        stale.setCheckoutSessionId("cs_old");
        stale.setCheckoutExpiresAt(now.minus(1, ChronoUnit.MINUTES));
        when(paymentRepository.findByReservationIdOrderByCreatedAtDesc(reservation.getId()))
                .thenReturn(List.of(stale));

        assertThat(paymentService.findOpenCardCheckout(reservation.getId(), now)).isEmpty();
    }

    @Test
    void cancelOpenCardPayments_closesOnlyUncapturedCardPayments() {
        Payment open = cardPayment(PaymentStatus.PENDING);
        Payment captured = cardPayment(PaymentStatus.SUCCEEDED);
        when(paymentRepository.findByReservationIdOrderByCreatedAtDesc(reservation.getId()))
                .thenReturn(List.of(open, captured));

        paymentService.cancelOpenCardPayments(reservation.getId());

        assertThat(open.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(captured.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
    }

    @Test
    void refund_marksTheCardPaymentRefunded_whenStripeRefundsInFull() {
        Payment payment = cardPayment(PaymentStatus.SUCCEEDED);
        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));
        when(refundRepository.save(any(Refund.class))).thenAnswer(inv -> inv.getArgument(0));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(stripeGateway.refund(any(), any(), any()))
                .thenReturn(PaymentGatewayResult.succeeded("re_test_1"));

        paymentService.refund(payment.getId(), new RefundCreateRequest(new BigDecimal("500.00"), "Anulare"));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(payment.getRefundedAmount()).isEqualByComparingTo("500.00");
    }

    @Test
    void refund_marksThePaymentPartiallyRefunded_forAPartialAmount() {
        Payment payment = cardPayment(PaymentStatus.SUCCEEDED);
        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));
        when(refundRepository.save(any(Refund.class))).thenAnswer(inv -> inv.getArgument(0));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(stripeGateway.refund(any(), any(), any()))
                .thenReturn(PaymentGatewayResult.succeeded("re_test_1"));

        paymentService.refund(payment.getId(), new RefundCreateRequest(new BigDecimal("200.00"), "Anulare parțială"));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);
        assertThat(payment.getRefundedAmount()).isEqualByComparingTo("200.00");
    }

    @Test
    void refund_leavesThePaymentUntouched_whenStripeRefusesTheRefund() {
        Payment payment = cardPayment(PaymentStatus.SUCCEEDED);
        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));
        when(refundRepository.save(any(Refund.class))).thenAnswer(inv -> {
            Refund refund = inv.getArgument(0);
            return refund;
        });
        when(stripeGateway.refund(any(), any(), any()))
                .thenReturn(PaymentGatewayResult.failed("pi_test_1", "insufficient funds"));

        paymentService.refund(payment.getId(), new RefundCreateRequest(new BigDecimal("500.00"), "Anulare"));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(payment.getRefundedAmount()).isEqualByComparingTo("0");
    }

    @Test
    void refund_recordsTheRefundAsFailed_whenStripeRefusesIt() {
        Payment payment = cardPayment(PaymentStatus.SUCCEEDED);
        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));
        when(refundRepository.save(any(Refund.class))).thenAnswer(inv -> inv.getArgument(0));
        when(stripeGateway.refund(any(), any(), any()))
                .thenReturn(PaymentGatewayResult.failed("pi_test_1", "insufficient funds"));

        paymentService.refund(payment.getId(), new RefundCreateRequest(new BigDecimal("500.00"), "Anulare"));

        verify(refundRepository, org.mockito.Mockito.atLeastOnce()).save(
                org.mockito.ArgumentMatchers.argThat(r -> r.getStatus() == RefundStatus.FAILED));
    }
}
