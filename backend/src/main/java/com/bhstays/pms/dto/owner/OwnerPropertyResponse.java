package com.bhstays.pms.dto.owner;

import com.bhstays.pms.domain.PropertyStatus;
import com.bhstays.pms.domain.PropertyType;
import com.bhstays.pms.dto.property.AddressDto;
import com.bhstays.pms.dto.property.PropertyDocumentResponse;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * {@code revenueByCurrency} is the official source: the property's
 * all-time collected money, one line per currency, on the same formula as
 * the statements. The flat revenue fields are deprecated: with exactly one
 * currency they mirror it (code in {@code currency}); with several they are
 * null - currencies are never added together.
 */
public record OwnerPropertyResponse(
        UUID id,
        String name,
        PropertyType propertyType,
        PropertyStatus status,
        AddressDto address,
        int bedrooms,
        int bathrooms,
        int maxGuests,
        BigDecimal commissionPercent,
        String coverPhotoUrl,
        /* Deprecated: net collected revenue of the only currency, else null. */
        @Deprecated BigDecimal grossRevenue,
        /* Deprecated: BH Stays commission of the only currency, else null. */
        @Deprecated BigDecimal commissionAmount,
        /* Deprecated: owner amount of the only currency, else null. */
        @Deprecated BigDecimal netRevenue,
        /* Deprecated: code of the only currency, else null. */
        @Deprecated String currency,
        List<PropertyDocumentResponse> documents,
        List<OwnerRevenueLine> revenueByCurrency
) {
}
