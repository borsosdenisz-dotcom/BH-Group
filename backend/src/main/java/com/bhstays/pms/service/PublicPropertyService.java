package com.bhstays.pms.service;

import com.bhstays.pms.common.exception.ResourceNotFoundException;
import com.bhstays.pms.common.response.PageResponse;
import com.bhstays.pms.domain.Facility;
import com.bhstays.pms.domain.Property;
import com.bhstays.pms.repository.PropertyPhotoRepository;
import com.bhstays.pms.repository.PropertyRepository;
import com.bhstays.pms.repository.PropertySpecifications;
import com.bhstays.pms.domain.PropertyStatus;
import com.bhstays.pms.dto.publicapi.PublicCalendarEntryResponse;
import com.bhstays.pms.dto.publicapi.PublicPropertyResponse;
import com.bhstays.pms.dto.publicapi.PublicPropertySummaryResponse;
import com.bhstays.pms.repository.ReservationRepository;
import com.bhstays.pms.domain.ReservationStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bhstays.pms.service.mapper.PublicPropertyMapper;
@Service
@RequiredArgsConstructor
public class PublicPropertyService {

    private final PropertyRepository propertyRepository;
    private final PropertyPhotoRepository propertyPhotoRepository;
    private final ReservationRepository reservationRepository;
    private final PublicPropertyMapper publicPropertyMapper;

    @Transactional(readOnly = true)
    public PageResponse<PublicPropertySummaryResponse> search(String search, Integer guests, Integer bedrooms,
                                                               BigDecimal minPrice, BigDecimal maxPrice,
                                                               Set<Facility> facilities, LocalDate checkIn,
                                                               LocalDate checkOut, Pageable pageable) {
        Specification<Property> spec = PropertySpecifications.combine(
                PropertySpecifications.hasStatus(PropertyStatus.ACTIVE),
                PropertySpecifications.search(search),
                PropertySpecifications.minGuests(guests),
                PropertySpecifications.minBedrooms(bedrooms),
                PropertySpecifications.priceRange(minPrice, maxPrice),
                PropertySpecifications.hasFacilities(facilities)
        );

        Page<Property> page = propertyRepository.findAll(spec, pageable);

        List<Property> filtered = page.getContent();
        if (checkIn != null && checkOut != null) {
            // Searching by dates asks "what can I book then?" - a demo
            // listing is never an answer to that.
            filtered = filtered.stream()
                    .filter(property -> !property.isDemo())
                    .filter(property -> reservationRepository
                            .findOverlapping(property.getId(), checkIn, checkOut, null, ReservationStatus.NON_BLOCKING)
                            .isEmpty())
                    .toList();
        }

        List<PublicPropertySummaryResponse> content = filtered.stream()
                .map(property -> publicPropertyMapper.toSummary(
                        property, propertyPhotoRepository.findByPropertyIdOrderBySortOrderAsc(property.getId())))
                .toList();

        return new PageResponse<>(
                content, page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages(),
                page.isFirst(), page.isLast());
    }

    @Transactional(readOnly = true)
    public List<PublicCalendarEntryResponse> calendar(UUID propertyId, LocalDate from, LocalDate to) {
        return reservationRepository.findCalendarEntries(propertyId, from, to, ReservationStatus.NON_BLOCKING).stream()
                .map(r -> new PublicCalendarEntryResponse(r.getCheckInDate(), r.getCheckOutDate()))
                .toList();
    }

    @Transactional(readOnly = true)
    public PublicPropertyResponse get(UUID id) {
        Property property = propertyRepository.findById(id)
                .filter(p -> p.getStatus() == PropertyStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("Property not found"));

        return publicPropertyMapper.toResponse(
                property, propertyPhotoRepository.findByPropertyIdOrderBySortOrderAsc(id));
    }
}
