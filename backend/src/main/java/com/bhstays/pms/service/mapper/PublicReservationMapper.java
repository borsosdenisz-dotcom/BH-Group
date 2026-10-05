package com.bhstays.pms.service.mapper;

import com.bhstays.pms.domain.Payment;
import com.bhstays.pms.domain.PaymentProvider;
import com.bhstays.pms.domain.PaymentStatus;
import com.bhstays.pms.dto.publicapi.PublicReservationResponse;
import com.bhstays.pms.domain.Reservation;
import com.bhstays.pms.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PublicReservationMapper {

    private final PaymentRepository paymentRepository;

    public PublicReservationResponse toResponse(Reservation reservation) {
        return new PublicReservationResponse(
                reservation.getId(),
                reservation.getProperty().getName(),
                reservation.getProperty().getAddress().getCity(),
                reservation.getGuestFirstName(),
                reservation.getGuestLastName(),
                reservation.getGuestEmail(),
                reservation.getGuestPhone(),
                reservation.getCheckInDate(),
                reservation.getCheckOutDate(),
                reservation.getNumberOfGuests(),
                reservation.getStatus(),
                reservation.getTotalAmount(),
                reservation.getCurrency(),
                reservation.getManagementToken(),
                reservation.getProperty().isLateCheckoutEnabled(),
                reservation.getProperty().getLateCheckoutTime(),
                reservation.getProperty().getLateCheckoutFee(),
                reservation.getHoldExpiresAt(),
                latestCardPaymentStatus(reservation)
        );
    }

    /**
     * Lets the "back from Stripe" page tell "still verifying" apart from
     * "declined" - read-only, it never moves the booking forward.
     */
    private PaymentStatus latestCardPaymentStatus(Reservation reservation) {
        if (reservation.getId() == null) {
            return null;
        }
        return paymentRepository.findByReservationIdOrderByCreatedAtDesc(reservation.getId()).stream()
                .filter(p -> p.getProvider() == PaymentProvider.STRIPE)
                .findFirst()
                .map(Payment::getStatus)
                .orElse(null);
    }
}
