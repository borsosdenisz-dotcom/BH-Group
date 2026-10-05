package com.bhstays.pms.service;

import com.bhstays.pms.common.exception.BadRequestException;
import com.bhstays.pms.common.exception.ResourceNotFoundException;
import com.bhstays.pms.domain.Payment;
import com.bhstays.pms.domain.PaymentMethod;
import com.bhstays.pms.domain.PaymentProvider;
import com.bhstays.pms.domain.PaymentStatus;
import com.bhstays.pms.domain.PaymentTransaction;
import com.bhstays.pms.domain.PaymentTransactionStatus;
import com.bhstays.pms.domain.PaymentTransactionType;
import com.bhstays.pms.domain.PaymentWebhookEvent;
import com.bhstays.pms.domain.Refund;
import com.bhstays.pms.domain.RefundStatus;
import com.bhstays.pms.domain.Reservation;
import com.bhstays.pms.domain.ReservationStatus;
import com.bhstays.pms.dto.payment.ManualPaymentCreateRequest;
import com.bhstays.pms.dto.payment.PaymentResponse;
import com.bhstays.pms.dto.payment.RefundCreateRequest;
import com.bhstays.pms.dto.reservation.ReservationStatusUpdateRequest;
import com.bhstays.pms.payment.PaymentGateway;
import com.bhstays.pms.payment.PaymentGatewayResult;
import com.bhstays.pms.repository.PaymentRepository;
import com.bhstays.pms.repository.PaymentTransactionRepository;
import com.bhstays.pms.repository.PaymentWebhookEventRepository;
import com.bhstays.pms.repository.RefundRepository;
import com.bhstays.pms.repository.ReservationRepository;
import com.bhstays.pms.service.mapper.PaymentMapper;
import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final Set<PaymentStatus> NET_PAID_STATUSES = Set.of(
            PaymentStatus.SUCCEEDED, PaymentStatus.PARTIALLY_REFUNDED);

    /** A card payment in one of these states has not been captured and may still be paid or abandoned. */
    private static final Set<PaymentStatus> OPEN_CARD_STATUSES = Set.of(
            PaymentStatus.PENDING, PaymentStatus.PROCESSING, PaymentStatus.FAILED);

    private final PaymentRepository paymentRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final RefundRepository refundRepository;
    private final PaymentWebhookEventRepository paymentWebhookEventRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationService reservationService;
    private final PaymentMapper paymentMapper;
    private final List<PaymentGateway> gateways;

    private Map<PaymentProvider, PaymentGateway> gatewaysByProvider;

    @PostConstruct
    void indexGateways() {
        gatewaysByProvider = new EnumMap<>(PaymentProvider.class);
        for (PaymentGateway gateway : gateways) {
            gatewaysByProvider.put(gateway.getProvider(), gateway);
        }
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> listForReservation(UUID reservationId) {
        return paymentRepository.findByReservationIdOrderByCreatedAtDesc(reservationId).stream()
                .map(this::toResponse)
                .toList();
    }

    /** Dry-run of {@link #autoRefundForCancellation}: what would be refunded, without doing it. */
    @Transactional(readOnly = true)
    public BigDecimal estimateRefund(UUID reservationId, BigDecimal refundPercent) {
        if (refundPercent == null || refundPercent.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal total = BigDecimal.ZERO;
        for (Payment payment : paymentRepository.findByReservationIdOrderByCreatedAtDesc(reservationId)) {
            if (payment.getStatus() != PaymentStatus.SUCCEEDED
                    && payment.getStatus() != PaymentStatus.PARTIALLY_REFUNDED) {
                continue;
            }
            BigDecimal refundableBalance = payment.getAmount().subtract(payment.getRefundedAmount());
            if (refundableBalance.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            total = total.add(refundableBalance
                    .multiply(refundPercent)
                    .divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP));
        }
        return total;
    }

    /**
     * Refunds {@code refundPercent}% of every payment's still-refundable
     * balance for a reservation, per its property's cancellation policy.
     * A no-op when the percentage is zero (NON_REFUNDABLE, or cancelled too
     * close to check-in).
     */
    @Transactional
    public void autoRefundForCancellation(UUID reservationId, BigDecimal refundPercent) {
        if (refundPercent == null || refundPercent.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        List<Payment> payments = paymentRepository.findByReservationIdOrderByCreatedAtDesc(reservationId);
        for (Payment payment : payments) {
            if (payment.getStatus() != PaymentStatus.SUCCEEDED
                    && payment.getStatus() != PaymentStatus.PARTIALLY_REFUNDED) {
                continue;
            }
            BigDecimal refundableBalance = payment.getAmount().subtract(payment.getRefundedAmount());
            if (refundableBalance.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal amountToRefund = refundableBalance
                    .multiply(refundPercent)
                    .divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
            if (amountToRefund.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            refund(payment.getId(), new com.bhstays.pms.dto.payment.RefundCreateRequest(
                    amountToRefund, "Rambursare automată la anulare (" + refundPercent + "% conform politicii)"));
        }
    }

    @Transactional
    public PaymentResponse recordManualPayment(ManualPaymentCreateRequest request) {
        Reservation reservation = reservationRepository.findById(request.reservationId())
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found"));

        Payment payment = Payment.builder()
                .reservation(reservation)
                .provider(PaymentProvider.MANUAL)
                .method(request.method())
                .status(PaymentStatus.PENDING)
                .amount(request.amount())
                .currency(reservation.getCurrency())
                .notes(request.notes())
                .build();
        payment = paymentRepository.save(payment);

        PaymentGatewayResult result = gateway(PaymentProvider.MANUAL).charge(payment);
        applyChargeResult(payment, result);

        return toResponse(payment);
    }

    /**
     * Creates - or reuses - the PENDING card payment that a hosted Checkout
     * session is opened against. Reusing keeps a retried checkout (double
     * click, expired session, guest returning later) from piling up payment
     * rows for one booking. {@code amount} is always the server-recomputed
     * quote; nothing here ever takes an amount from a client.
     */
    @Transactional
    public Payment startOnlineCardPayment(Reservation reservation, BigDecimal amount, String currency) {
        return findReusableCardPayment(reservation.getId())
                .map(existing -> {
                    existing.setAmount(amount);
                    existing.setCurrency(currency);
                    return paymentRepository.save(existing);
                })
                .orElseGet(() -> paymentRepository.save(Payment.builder()
                        .reservation(reservation)
                        .provider(PaymentProvider.STRIPE)
                        .method(PaymentMethod.ONLINE_CARD)
                        .status(PaymentStatus.PENDING)
                        .amount(amount)
                        .currency(currency)
                        .build()));
    }

    /** The most recent card payment still waiting on Stripe (no session yet, or a session in flight). */
    @Transactional(readOnly = true)
    public Optional<Payment> findReusableCardPayment(UUID reservationId) {
        return paymentRepository.findByReservationIdOrderByCreatedAtDesc(reservationId).stream()
                .filter(p -> p.getProvider() == PaymentProvider.STRIPE)
                .filter(p -> p.getStatus() == PaymentStatus.PENDING || p.getStatus() == PaymentStatus.PROCESSING)
                .findFirst();
    }

    /**
     * The card payment whose hosted Checkout session the guest can still pay
     * on right now. A declined card leaves the session open (Stripe lets the
     * guest try another card on the same page), so FAILED counts as open too
     * until the session itself expires.
     */
    @Transactional(readOnly = true)
    public Optional<Payment> findOpenCardCheckout(UUID reservationId, Instant now) {
        return paymentRepository.findByReservationIdOrderByCreatedAtDesc(reservationId).stream()
                .filter(p -> p.getProvider() == PaymentProvider.STRIPE)
                .filter(p -> OPEN_CARD_STATUSES.contains(p.getStatus()))
                .filter(p -> p.getCheckoutSessionId() != null
                        && p.getCheckoutExpiresAt() != null
                        && p.getCheckoutExpiresAt().isAfter(now))
                .findFirst();
    }

    /** The latest card payment for a booking - what the guest-facing status page reports on. */
    @Transactional(readOnly = true)
    public Optional<Payment> findLatestCardPayment(UUID reservationId) {
        return paymentRepository.findByReservationIdOrderByCreatedAtDesc(reservationId).stream()
                .filter(p -> p.getProvider() == PaymentProvider.STRIPE)
                .findFirst();
    }

    /**
     * Records a card payment Stripe has confirmed as captured: stores the
     * PaymentIntent id (refunds are issued against it), moves the payment to
     * SUCCEEDED and writes the CHARGE ledger entry.
     *
     * <p>Unlike {@link #recordManualPayment} this deliberately does not touch
     * the reservation - the caller (the verified Stripe webhook) decides,
     * under a row lock, whether the booking is confirmed or the money goes
     * back, and raises the guest/admin notices exactly once.
     */
    @Transactional
    public Payment recordCardCapture(Payment payment, String paymentIntentId) {
        payment.setStatus(PaymentStatus.SUCCEEDED);
        if (paymentIntentId != null && !paymentIntentId.isBlank()) {
            payment.setProviderPaymentId(paymentIntentId);
        }
        paymentRepository.save(payment);
        paymentTransactionRepository.save(PaymentTransaction.builder()
                .payment(payment)
                .type(PaymentTransactionType.CHARGE)
                .status(PaymentTransactionStatus.SUCCEEDED)
                .amount(payment.getAmount())
                .providerTransactionId(paymentIntentId)
                .build());
        return payment;
    }

    /**
     * Records a declined card attempt. The booking stays on hold: the guest
     * may still pay with another card on the same Checkout page until the
     * session (and with it the hold) expires.
     */
    @Transactional
    public Payment markCardPaymentFailed(Payment payment, String failureReason) {
        if (!OPEN_CARD_STATUSES.contains(payment.getStatus())) {
            return payment;
        }
        applyChargeResult(payment, PaymentGatewayResult.failed(payment.getProviderPaymentId(), failureReason));
        return payment;
    }

    /** Closes card payments whose session can no longer be paid - an expired session or a released hold. */
    @Transactional
    public void cancelOpenCardPayments(UUID reservationId) {
        for (Payment payment : paymentRepository.findByReservationIdOrderByCreatedAtDesc(reservationId)) {
            if (payment.getProvider() == PaymentProvider.STRIPE && OPEN_CARD_STATUSES.contains(payment.getStatus())) {
                payment.setStatus(PaymentStatus.CANCELLED);
                paymentRepository.save(payment);
            }
        }
    }

    @Transactional
    public PaymentResponse refund(UUID paymentId, RefundCreateRequest request) {
        Payment payment = findPaymentOrThrow(paymentId);
        BigDecimal refundable = payment.getAmount().subtract(payment.getRefundedAmount());
        if (request.amount().compareTo(refundable) > 0) {
            throw new BadRequestException("Refund amount exceeds the refundable balance (" + refundable + ")");
        }

        Refund refund = Refund.builder()
                .payment(payment)
                .amount(request.amount())
                .reason(request.reason())
                .status(RefundStatus.REQUESTED)
                .build();
        refund = refundRepository.save(refund);

        PaymentGatewayResult result = gateway(payment.getProvider()).refund(payment, request.amount(), request.reason());

        paymentTransactionRepository.save(PaymentTransaction.builder()
                .payment(payment)
                .type(PaymentTransactionType.REFUND)
                .status(toTransactionStatus(result.status()))
                .amount(request.amount())
                .providerTransactionId(result.providerReference())
                .failureReason(result.failureReason())
                .build());

        if (result.status() == PaymentStatus.SUCCEEDED) {
            refund.setStatus(RefundStatus.SUCCEEDED);
            refund.setProviderRefundId(result.providerReference());
            payment.setRefundedAmount(payment.getRefundedAmount().add(request.amount()));
            payment.setStatus(payment.getRefundedAmount().compareTo(payment.getAmount()) >= 0
                    ? PaymentStatus.REFUNDED : PaymentStatus.PARTIALLY_REFUNDED);
            paymentRepository.save(payment);
        } else {
            refund.setStatus(RefundStatus.FAILED);
        }
        refundRepository.save(refund);

        return toResponse(payment);
    }

    private void applyChargeResult(Payment payment, PaymentGatewayResult result) {
        payment.setStatus(result.status());
        if (result.providerReference() != null) {
            payment.setProviderPaymentId(result.providerReference());
        }
        paymentRepository.save(payment);

        paymentTransactionRepository.save(PaymentTransaction.builder()
                .payment(payment)
                .type(PaymentTransactionType.CHARGE)
                .status(toTransactionStatus(result.status()))
                .amount(payment.getAmount())
                .providerTransactionId(result.providerReference())
                .failureReason(result.failureReason())
                .build());

        if (result.status() == PaymentStatus.SUCCEEDED) {
            confirmReservationIfFullyPaid(payment.getReservation());
        }
    }

    private void confirmReservationIfFullyPaid(Reservation reservation) {
        if (reservation.getStatus() != ReservationStatus.PENDING || reservation.getTotalAmount() == null) {
            return;
        }
        BigDecimal netPaid = paymentRepository.sumNetPaidForReservation(reservation.getId(), NET_PAID_STATUSES);
        if (netPaid.compareTo(reservation.getTotalAmount()) >= 0) {
            reservationService.updateStatus(reservation.getId(),
                    new ReservationStatusUpdateRequest(ReservationStatus.CONFIRMED));
        }
    }

    private PaymentTransactionStatus toTransactionStatus(PaymentStatus status) {
        return switch (status) {
            case SUCCEEDED, PARTIALLY_REFUNDED, REFUNDED -> PaymentTransactionStatus.SUCCEEDED;
            case FAILED, CANCELLED -> PaymentTransactionStatus.FAILED;
            default -> PaymentTransactionStatus.PENDING;
        };
    }

    private PaymentGateway gateway(PaymentProvider provider) {
        PaymentGateway gateway = gatewaysByProvider.get(provider);
        if (gateway == null) {
            throw new BadRequestException("No payment gateway configured for provider " + provider);
        }
        return gateway;
    }

    private PaymentResponse toResponse(Payment payment) {
        var transactions = paymentTransactionRepository.findByPaymentIdOrderByCreatedAtAsc(payment.getId());
        var refunds = refundRepository.findByPaymentIdOrderByCreatedAtDesc(payment.getId());
        return paymentMapper.toResponse(payment, transactions, refunds);
    }

    private Payment findPaymentOrThrow(UUID id) {
        return paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));
    }
}
