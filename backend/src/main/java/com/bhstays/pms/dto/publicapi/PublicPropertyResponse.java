package com.bhstays.pms.dto.publicapi;

import com.bhstays.pms.domain.Facility;
import com.bhstays.pms.domain.PropertyType;
import com.bhstays.pms.dto.property.PropertyPhotoResponse;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record PublicPropertyResponse(
        UUID id,
        String name,
        String description,
        PropertyType propertyType,
        /** Only populated when exactLocation is true - null otherwise, never a partial/misleading value. */
        String addressLine,
        String city,
        String county,
        String country,
        Double latitude,
        Double longitude,
        boolean exactLocation,
        int bedrooms,
        int bathrooms,
        int maxGuests,
        Integer minStayNights,
        Integer maxStayNights,
        BigDecimal sizeSqm,
        BigDecimal basePricePerNight,
        String currency,
        LocalTime checkInTime,
        LocalTime checkOutTime,
        Set<Facility> facilities,
        List<PropertyPhotoResponse> photos,
        /** Showcase listing: shown for illustration only, cannot be quoted, booked or paid. */
        boolean demo
) {
}
