package com.bhstays.pms.dto.publicapi;

import com.bhstays.pms.domain.PaymentStatus;
import com.bhstays.pms.domain.ReservationStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record PublicReservationResponse(
        UUID id,
        String propertyName,
        String propertyCity,
        String guestFirstName,
        String guestLastName,
        String guestEmail,
        String guestPhone,
        LocalDate checkInDate,
        LocalDate checkOutDate,
        int numberOfGuests,
        ReservationStatus status,
        BigDecimal totalAmount,
        String currency,
        String managementToken,
        boolean lateCheckoutAvailable,
        LocalTime lateCheckoutTime,
        BigDecimal lateCheckoutFee,
        /** Until when an unpaid booking keeps its dates; null once confirmed. */
        Instant holdExpiresAt,
        /** Latest online card payment's status, or null if the guest never started one. */
        PaymentStatus cardPaymentStatus
) {
}
