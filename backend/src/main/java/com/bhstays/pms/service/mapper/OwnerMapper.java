package com.bhstays.pms.service.mapper;

import com.bhstays.pms.domain.Property;
import com.bhstays.pms.domain.PropertyDocument;
import com.bhstays.pms.domain.PropertyPhoto;
import com.bhstays.pms.dto.owner.OwnerPropertyResponse;
import com.bhstays.pms.dto.owner.OwnerRevenueLine;
import com.bhstays.pms.dto.property.AddressDto;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class OwnerMapper {

    private final PropertyMapper propertyMapper;

    public OwnerMapper(PropertyMapper propertyMapper) {
        this.propertyMapper = propertyMapper;
    }

    /** {@code revenueByCurrency} comes from OwnerFinancialsService - nothing is computed here. */
    public OwnerPropertyResponse toResponse(Property property, List<PropertyPhoto> photos,
                                             List<OwnerRevenueLine> revenueByCurrency, List<PropertyDocument> documents) {
        String coverUrl = photos.stream()
                .sorted(Comparator.comparing(PropertyPhoto::isCover, Comparator.reverseOrder())
                        .thenComparingInt(PropertyPhoto::getSortOrder))
                .map(PropertyPhoto::getUrl)
                .findFirst()
                .orElse(null);

        LegacyFields legacy = LegacyFields.of(revenueByCurrency);

        return new OwnerPropertyResponse(
                property.getId(),
                property.getName(),
                property.getPropertyType(),
                property.getStatus(),
                new AddressDto(
                        property.getAddress().getAddressLine(), property.getAddress().getCity(),
                        property.getAddress().getCounty(), property.getAddress().getPostalCode(),
                        property.getAddress().getCountry(), property.getAddress().getLatitude(),
                        property.getAddress().getLongitude()),
                property.getBedrooms(),
                property.getBathrooms(),
                property.getMaxGuests(),
                property.getCommissionPercent(),
                coverUrl,
                legacy.pick(OwnerRevenueLine::netRevenue),
                legacy.pick(OwnerRevenueLine::bhStaysCommission),
                legacy.pick(OwnerRevenueLine::ownerAmount),
                legacy.currency(),
                documents.stream().map(propertyMapper::toDocumentResponse).toList(),
                revenueByCurrency);
    }

    /**
     * Values for the deprecated single-currency fields: the only line when
     * there is exactly one currency; zero (no currency) when there is none;
     * null when there are several - never one currency picked over another
     * and never a mixed total.
     */
    public record LegacyFields(OwnerRevenueLine only, boolean multiCurrency) {

        public static LegacyFields of(List<OwnerRevenueLine> revenueByCurrency) {
            return new LegacyFields(revenueByCurrency.size() == 1 ? revenueByCurrency.get(0) : null,
                    revenueByCurrency.size() > 1);
        }

        public BigDecimal pick(java.util.function.Function<OwnerRevenueLine, BigDecimal> field) {
            if (multiCurrency) {
                return null;
            }
            return only != null ? field.apply(only) : BigDecimal.ZERO.setScale(2);
        }

        public String currency() {
            return only != null ? only.currency() : null;
        }
    }
}
