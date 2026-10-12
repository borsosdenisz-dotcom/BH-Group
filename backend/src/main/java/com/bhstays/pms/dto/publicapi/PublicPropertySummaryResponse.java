package com.bhstays.pms.dto.publicapi;

import com.bhstays.pms.domain.Facility;
import com.bhstays.pms.domain.PropertyType;
import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

public record PublicPropertySummaryResponse(
        UUID id,
        String name,
        String city,
        String county,
        Double latitude,
        Double longitude,
        PropertyType propertyType,
        int bedrooms,
        int bathrooms,
        int maxGuests,
        BigDecimal basePricePerNight,
        String currency,
        String coverPhotoUrl,
        Set<Facility> facilities,
        /** Showcase listing: shown for illustration only, cannot be quoted, booked or paid. */
        boolean demo
) {
}
