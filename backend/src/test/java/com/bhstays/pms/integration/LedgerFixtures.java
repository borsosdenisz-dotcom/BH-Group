package com.bhstays.pms.integration;

import com.bhstays.pms.domain.Payment;
import com.bhstays.pms.domain.PaymentMethod;
import com.bhstays.pms.domain.PaymentProvider;
import com.bhstays.pms.domain.PaymentStatus;
import com.bhstays.pms.domain.PaymentTransaction;
import com.bhstays.pms.domain.PaymentTransactionStatus;
import com.bhstays.pms.domain.PaymentTransactionType;
import com.bhstays.pms.domain.Reservation;
import com.bhstays.pms.repository.PaymentRepository;
import com.bhstays.pms.repository.PaymentTransactionRepository;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Writes payments the way the application does - a payment row plus its
 * ledger entries - but at chosen instants, so financial periods can be
 * tested month by month.
 */
final class LedgerFixtures {

    private final PaymentRepository paymentRepository;
    private final PaymentTransactionRepository transactionRepository;

    LedgerFixtures(PaymentRepository paymentRepository, PaymentTransactionRepository transactionRepository) {
        this.paymentRepository = paymentRepository;
        this.transactionRepository = transactionRepository;
    }

    /** A payment captured at {@code at}: SUCCEEDED, with its successful CHARGE ledger entry. */
    Payment capture(Reservation reservation, String amount, Instant at) {
        Payment payment = paymentRepository.saveAndFlush(Payment.builder()
                .reservation(reservation)
                .provider(PaymentProvider.MANUAL)
                .method(PaymentMethod.BANK_TRANSFER)
                .status(PaymentStatus.SUCCEEDED)
                .amount(new BigDecimal(amount))
                .currency(reservation.getCurrency())
                .build());
        ledger(payment, PaymentTransactionType.CHARGE, PaymentTransactionStatus.SUCCEEDED, amount, at);
        return payment;
    }

    /** A charge attempt that never captured anything (pending, processing, failed or cancelled). */
    Payment attempt(Reservation reservation, PaymentStatus status, String amount, Instant at) {
        Payment payment = paymentRepository.saveAndFlush(Payment.builder()
                .reservation(reservation)
                .provider(PaymentProvider.MANUAL)
                .method(PaymentMethod.BANK_TRANSFER)
                .status(status)
                .amount(new BigDecimal(amount))
                .currency(reservation.getCurrency())
                .build());
        PaymentTransactionStatus txStatus = status == PaymentStatus.FAILED || status == PaymentStatus.CANCELLED
                ? PaymentTransactionStatus.FAILED : PaymentTransactionStatus.PENDING;
        ledger(payment, PaymentTransactionType.CHARGE, txStatus, amount, at);
        return payment;
    }

    /** A successful refund at {@code at}, updating the payment like PaymentService does. */
    Payment refund(Payment payment, String amount, Instant at) {
        Payment current = paymentRepository.findById(payment.getId()).orElseThrow();
        current.setRefundedAmount(current.getRefundedAmount().add(new BigDecimal(amount)));
        current.setStatus(current.getRefundedAmount().compareTo(current.getAmount()) >= 0
                ? PaymentStatus.REFUNDED : PaymentStatus.PARTIALLY_REFUNDED);
        current = paymentRepository.saveAndFlush(current);
        ledger(current, PaymentTransactionType.REFUND, PaymentTransactionStatus.SUCCEEDED, amount, at);
        return current;
    }

    /** A refund the gateway declined: recorded in the ledger, never counted. */
    void failedRefund(Payment payment, String amount, Instant at) {
        ledger(payment, PaymentTransactionType.REFUND, PaymentTransactionStatus.FAILED, amount, at);
    }

    private void ledger(Payment payment, PaymentTransactionType type, PaymentTransactionStatus status, String amount,
                        Instant at) {
        transactionRepository.saveAndFlush(PaymentTransaction.builder()
                .payment(payment)
                .type(type)
                .status(status)
                .amount(new BigDecimal(amount))
                .createdAt(at)
                .build());
    }
}
