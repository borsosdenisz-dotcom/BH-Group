package com.bhstays.pms.dto.property;

import com.bhstays.pms.domain.PropertyStatus;
import com.bhstays.pms.domain.PropertyType;
import java.time.Instant;
import java.util.UUID;

public record PropertySummaryResponse(
        UUID id,
        String name,
        String city,
        PropertyType propertyType,
        PropertyStatus status,
        int bedrooms,
        int bathrooms,
        int maxGuests,
        String coverPhotoUrl,
        Instant createdAt,
        boolean demo
) {
}
