package com.bhgroup.pms.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bhgroup.pms.domain.Payment;
import com.bhgroup.pms.domain.PaymentMethod;
import com.bhgroup.pms.domain.PaymentProvider;
import com.bhgroup.pms.domain.PaymentStatus;
import com.bhgroup.pms.domain.Property;
import com.bhgroup.pms.domain.Refund;
import com.bhgroup.pms.domain.RefundStatus;
import com.bhgroup.pms.domain.Reservation;
import com.bhgroup.pms.domain.ReservationStatus;
import com.bhgroup.pms.dto.payment.RefundCreateRequest;
import com.bhgroup.pms.payment.PaymentGateway;
import com.bhgroup.pms.payment.PaymentGatewayResult;
import com.bhgroup.pms.repository.PaymentRepository;
import com.bhgroup.pms.repository.PaymentTransactionRepository;
import com.bhgroup.pms.repository.PaymentWebhookEventRepository;
import com.bhgroup.pms.repository.RefundRepository;
import com.bhgroup.pms.repository.ReservationRepository;
import com.bhgroup.pms.service.mapper.PaymentMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
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
    void markCardPaymentSucceeded_capturesAndConfirmsTheReservation() {
        Payment payment = cardPayment(PaymentStatus.PENDING);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(paymentRepository.sumNetPaidForReservation(any(), any())).thenReturn(new BigDecimal("500.00"));

        paymentService.markCardPaymentSucceeded(payment, "pi_test_1");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(payment.getProviderPaymentId()).isEqualTo("pi_test_1");
        verify(reservationService).updateStatus(any(), any());
    }

    @Test
    void markCardPaymentSucceeded_isANoOpWhenThePaymentWasAlreadyCaptured() {
        Payment payment = cardPayment(PaymentStatus.SUCCEEDED);

        paymentService.markCardPaymentSucceeded(payment, "pi_test_1");

        verify(reservationService, never()).updateStatus(any(), any());
        verify(paymentTransactionRepository, never()).save(any());
    }

    @Test
    void refund_marksTheCardPaymentRefunded_whenStripeRefundsInFull() {
        Payment payment = cardPayment(PaymentStatus.SUCCEEDED);
        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));
        when(refundRepository.save(any(Refund.class))).thenAnswer(inv -> inv.getArgument(0));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(stripeGateway.refund(any(), any(), any()))
                .thenReturn(PaymentGatewayResult.succeeded("re_test_1", "succeeded"));

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
                .thenReturn(PaymentGatewayResult.succeeded("re_test_1", "succeeded"));

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
                .thenReturn(PaymentGatewayResult.failed("pi_test_1", null, "insufficient funds"));

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
                .thenReturn(PaymentGatewayResult.failed("pi_test_1", null, "insufficient funds"));

        paymentService.refund(payment.getId(), new RefundCreateRequest(new BigDecimal("500.00"), "Anulare"));

        verify(refundRepository, org.mockito.Mockito.atLeastOnce()).save(
                org.mockito.ArgumentMatchers.argThat(r -> r.getStatus() == RefundStatus.FAILED));
    }
}
