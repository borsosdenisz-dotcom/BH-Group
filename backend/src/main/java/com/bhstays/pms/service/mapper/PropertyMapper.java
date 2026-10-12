package com.bhstays.pms.service.mapper;

import com.bhstays.pms.config.AppProperties;
import com.bhstays.pms.dto.property.AddressDto;
import com.bhstays.pms.dto.property.PropertyDocumentResponse;
import com.bhstays.pms.dto.property.PropertyPhotoResponse;
import com.bhstays.pms.dto.property.PropertyResponse;
import com.bhstays.pms.dto.property.PropertySummaryResponse;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import com.bhstays.pms.domain.Address;
import com.bhstays.pms.domain.Property;
import com.bhstays.pms.domain.PropertyDocument;
import com.bhstays.pms.domain.PropertyPhoto;
@Component
@RequiredArgsConstructor
public class PropertyMapper {

    private final AppProperties appProperties;

    public PropertyResponse toResponse(Property property, List<PropertyPhoto> photos, List<PropertyDocument> documents) {
        return new PropertyResponse(
                property.getId(),
                property.getName(),
                property.getDescription(),
                property.getPropertyType(),
                property.getStatus(),
                toAddressDto(property.getAddress()),
                property.isShowExactAddressPublicly(),
                property.getBedrooms(),
                property.getBathrooms(),
                property.getMaxGuests(),
                property.getSizeSqm(),
                property.getBasePricePerNight(),
                property.getWeekendPricePerNight(),
                property.getCleaningFee(),
                property.getExtraGuestFee(),
                property.getBaseGuestsIncluded(),
                property.getWeeklyDiscountPercent(),
                property.getMonthlyDiscountPercent(),
                property.getMinStayNights(),
                property.getMaxStayNights(),
                property.getCancellationPolicy(),
                property.getOwner() != null ? property.getOwner().getId() : null,
                property.getOwner() != null
                        ? property.getOwner().getFirstName() + " " + property.getOwner().getLastName() : null,
                property.getCommissionPercent(),
                property.getCleaningChecklist(),
                property.getCheckInTime(),
                property.getCheckOutTime(),
                property.getFacilities(),
                property.isSmartLockEnabled(),
                property.getSmartLockProvider(),
                property.getSmartLockDeviceId(),
                property.isLateCheckoutEnabled(),
                property.getLateCheckoutTime(),
                property.getLateCheckoutFee(),
                property.getIcalExportToken() != null
                        ? appProperties.getApiBaseUrl() + "/public/properties/" + property.getId()
                                + "/calendar.ics?token=" + property.getIcalExportToken()
                        : null,
                property.getIntegrationMode(),
                photos.stream().map(this::toPhotoResponse).toList(),
                documents.stream().map(this::toDocumentResponse).toList(),
                property.getCreatedAt(),
                property.getUpdatedAt(),
                property.isDemo()
        );
    }

    public PropertySummaryResponse toSummaryResponse(Property property, List<PropertyPhoto> photos) {
        String coverUrl = photos.stream()
                .sorted(Comparator.comparing(PropertyPhoto::isCover, Comparator.reverseOrder())
                        .thenComparingInt(PropertyPhoto::getSortOrder))
                .map(PropertyPhoto::getUrl)
                .findFirst()
                .orElse(null);

        return new PropertySummaryResponse(
                property.getId(),
                property.getName(),
                property.getAddress().getCity(),
                property.getPropertyType(),
                property.getStatus(),
                property.getBedrooms(),
                property.getBathrooms(),
                property.getMaxGuests(),
                coverUrl,
                property.getCreatedAt(),
                property.isDemo()
        );
    }

    public PropertyPhotoResponse toPhotoResponse(PropertyPhoto photo) {
        return new PropertyPhotoResponse(
                photo.getId(), photo.getUrl(), photo.getCaption(), photo.getSortOrder(), photo.isCover());
    }

    public PropertyDocumentResponse toDocumentResponse(PropertyDocument document) {
        return new PropertyDocumentResponse(
                document.getId(), document.getFileName(), document.getUrl(),
                document.getDocumentType(), document.getExpiresAt(), document.getCreatedAt());
    }

    public Address toAddress(AddressDto dto) {
        return new Address(
                dto.addressLine(), dto.city(), dto.county(), dto.postalCode(),
                dto.country(), dto.latitude(), dto.longitude());
    }

    private AddressDto toAddressDto(Address address) {
        return new AddressDto(
                address.getAddressLine(), address.getCity(), address.getCounty(), address.getPostalCode(),
                address.getCountry(), address.getLatitude(), address.getLongitude());
    }
}
