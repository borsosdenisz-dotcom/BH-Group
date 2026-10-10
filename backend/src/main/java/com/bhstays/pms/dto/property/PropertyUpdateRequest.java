package com.bhstays.pms.dto.property;

import com.bhstays.pms.domain.CancellationPolicy;
import com.bhstays.pms.domain.Facility;
import com.bhstays.pms.domain.PropertyStatus;
import com.bhstays.pms.domain.PropertyType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.Set;
import java.util.UUID;

import com.bhstays.pms.domain.Address;
import com.bhstays.pms.domain.Property;
public record PropertyUpdateRequest(

        @NotBlank(message = "Name is required")
        @Size(max = 200)
        String name,

        @Size(max = 4000)
        String description,

        @NotNull(message = "Property type is required")
        PropertyType propertyType,

        @NotNull(message = "Status is required")
        PropertyStatus status,

        @NotNull(message = "Address is required")
        @Valid
        AddressDto address,

        boolean showExactAddressPublicly,

        @Min(value = 0, message = "Bedrooms cannot be negative")
        int bedrooms,

        @Min(value = 0, message = "Bathrooms cannot be negative")
        int bathrooms,

        @Min(value = 1, message = "Maximum guests must be at least 1")
        int maxGuests,

        BigDecimal sizeSqm,

        BigDecimal basePricePerNight,

        BigDecimal weekendPricePerNight,

        BigDecimal cleaningFee,

        BigDecimal extraGuestFee,

        Integer baseGuestsIncluded,

        BigDecimal weeklyDiscountPercent,

        BigDecimal monthlyDiscountPercent,

        @Min(value = 1, message = "Minimum stay must be at least 1 night")
        Integer minStayNights,

        Integer maxStayNights,

        CancellationPolicy cancellationPolicy,

        UUID ownerId,

        @DecimalMin(value = "0.00", message = "Commission must be between 0 and 100")
        @DecimalMax(value = "100.00", message = "Commission must be between 0 and 100")
        @Digits(integer = 3, fraction = 2, message = "Commission can have at most two decimals")
        BigDecimal commissionPercent,

        java.util.List<String> cleaningChecklist,

        LocalTime checkInTime,

        LocalTime checkOutTime,

        Set<Facility> facilities,

        boolean smartLockEnabled,

        @Size(max = 100)
        String smartLockProvider,

        @Size(max = 150)
        String smartLockDeviceId,

        boolean lateCheckoutEnabled,

        LocalTime lateCheckoutTime,

        BigDecimal lateCheckoutFee
) {
}
