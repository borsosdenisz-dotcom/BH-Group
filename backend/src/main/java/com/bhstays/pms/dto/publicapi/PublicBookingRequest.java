package com.bhstays.pms.dto.publicapi;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

import com.bhstays.pms.domain.Property;
public record PublicBookingRequest(

        @NotNull(message = "Property is required")
        UUID propertyId,

        @NotBlank(message = "First name is required")
        String guestFirstName,

        @NotBlank(message = "Last name is required")
        String guestLastName,

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        String guestEmail,

        @NotBlank(message = "Phone number is required")
        String guestPhone,

        @NotNull(message = "Check-in date is required")
        @FutureOrPresent(message = "Check-in date cannot be in the past")
        LocalDate checkInDate,

        @NotNull(message = "Check-out date is required")
        LocalDate checkOutDate,

        @Min(value = 1, message = "Number of guests must be at least 1")
        int numberOfGuests,

        String notes,

        String idempotencyKey,

        /*
         * Public bookings are paid online by card - the only accepted value
         * is ONLINE_CARD (or omitting it). Anything else, e.g. BANK_TRANSFER
         * or ON_ARRIVAL, is rejected server-side; manual payment methods
         * exist only in the authenticated admin flows. Kept as a plain
         * string so an unknown value gets the same clear 400 as a known
         * manual one instead of a deserialization error.
         */
        String paymentMethod
) {

    public static final String ONLINE_CARD = "ONLINE_CARD";
}
