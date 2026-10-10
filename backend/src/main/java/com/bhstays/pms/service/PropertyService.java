package com.bhstays.pms.service;

import com.bhstays.pms.common.exception.BadRequestException;
import com.bhstays.pms.common.exception.ResourceNotFoundException;
import com.bhstays.pms.common.response.PageResponse;
import com.bhstays.pms.domain.AuditAction;
import com.bhstays.pms.domain.IntegrationMode;
import com.bhstays.pms.domain.SeasonalRate;
import com.bhstays.pms.dto.property.PropertyCreateRequest;
import com.bhstays.pms.dto.property.PropertyDocumentResponse;
import com.bhstays.pms.dto.property.PropertyPhotoResponse;
import com.bhstays.pms.dto.property.PropertyResponse;
import com.bhstays.pms.dto.property.PropertySummaryResponse;
import com.bhstays.pms.dto.property.PropertyUpdateRequest;
import com.bhstays.pms.dto.property.SeasonalRateRequest;
import com.bhstays.pms.dto.property.SeasonalRateResponse;
import com.bhstays.pms.repository.SeasonalRateRepository;
import com.bhstays.pms.service.mapper.SeasonalRateMapper;
import com.bhstays.pms.domain.Role;
import com.bhstays.pms.domain.User;
import com.bhstays.pms.repository.UserRepository;
import com.bhstays.pms.security.SecurityUtils;
import com.bhstays.pms.security.UserPrincipal;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.bhstays.pms.domain.Property;
import com.bhstays.pms.domain.PropertyDocument;
import com.bhstays.pms.domain.PropertyDocumentType;
import com.bhstays.pms.domain.PropertyPhoto;
import com.bhstays.pms.domain.PropertyStatus;
import com.bhstays.pms.domain.PropertyType;
import com.bhstays.pms.repository.PropertyDocumentRepository;
import com.bhstays.pms.repository.PropertyPhotoRepository;
import com.bhstays.pms.repository.PropertyRepository;
import com.bhstays.pms.repository.PropertySpecifications;
import com.bhstays.pms.service.mapper.PropertyMapper;
@Service
@RequiredArgsConstructor
public class PropertyService {

    private static final int DOCUMENT_EXPIRY_WARNING_DAYS = 30;
    private static final BigDecimal MAX_COMMISSION_PERCENT = new BigDecimal("100.00");

    private final PropertyRepository propertyRepository;
    private final PropertyPhotoRepository propertyPhotoRepository;
    private final PropertyDocumentRepository propertyDocumentRepository;
    private final SeasonalRateRepository seasonalRateRepository;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final PropertyMapper propertyMapper;
    private final SeasonalRateMapper seasonalRateMapper;

    @Transactional(readOnly = true)
    public PageResponse<PropertySummaryResponse> list(String search, PropertyStatus status, PropertyType type,
                                                        Pageable pageable) {
        Specification<Property> spec = PropertySpecifications.combine(
                PropertySpecifications.search(search),
                PropertySpecifications.hasStatus(status),
                PropertySpecifications.hasType(type)
        );

        Page<Property> page = propertyRepository.findAll(spec, pageable);
        return PageResponse.of(page, property -> propertyMapper.toSummaryResponse(
                property, propertyPhotoRepository.findByPropertyIdOrderBySortOrderAsc(property.getId())));
    }

    @Transactional(readOnly = true)
    public PropertyResponse get(UUID id) {
        Property property = findPropertyOrThrow(id);
        return toFullResponse(property);
    }

    @Transactional(readOnly = true)
    public List<List<String>> exportRows(String search, PropertyStatus status, PropertyType type) {
        Specification<Property> spec = PropertySpecifications.combine(
                PropertySpecifications.search(search),
                PropertySpecifications.hasStatus(status),
                PropertySpecifications.hasType(type)
        );

        return propertyRepository.findAll(spec, org.springframework.data.domain.Sort.by("name")).stream()
                .map(property -> List.of(
                        property.getName(),
                        property.getPropertyType().name(),
                        property.getStatus().name(),
                        property.getAddress().getCity(),
                        property.getAddress().getAddressLine(),
                        String.valueOf(property.getBedrooms()),
                        String.valueOf(property.getBathrooms()),
                        String.valueOf(property.getMaxGuests()),
                        property.getBasePricePerNight() != null ? property.getBasePricePerNight().toString() : ""
                ))
                .toList();
    }

    @Transactional
    public PropertyResponse create(PropertyCreateRequest request) {
        java.time.LocalTime effectiveCheckOutTime =
                request.checkOutTime() != null ? request.checkOutTime() : java.time.LocalTime.of(11, 0);
        assertValidLateCheckoutConfig(request.lateCheckoutEnabled(), request.lateCheckoutTime(),
                request.lateCheckoutFee(), effectiveCheckOutTime);

        Property property = Property.builder()
                .name(request.name())
                .description(request.description())
                .propertyType(request.propertyType())
                .status(PropertyStatus.DRAFT)
                .address(propertyMapper.toAddress(request.address()))
                .showExactAddressPublicly(request.showExactAddressPublicly())
                .bedrooms(request.bedrooms())
                .bathrooms(request.bathrooms())
                .maxGuests(request.maxGuests())
                .sizeSqm(request.sizeSqm())
                .basePricePerNight(request.basePricePerNight())
                .weekendPricePerNight(request.weekendPricePerNight())
                .cleaningFee(request.cleaningFee())
                .extraGuestFee(request.extraGuestFee())
                .baseGuestsIncluded(request.baseGuestsIncluded())
                .weeklyDiscountPercent(request.weeklyDiscountPercent())
                .monthlyDiscountPercent(request.monthlyDiscountPercent())
                .minStayNights(request.minStayNights())
                .maxStayNights(request.maxStayNights())
                .cancellationPolicy(request.cancellationPolicy() != null
                        ? request.cancellationPolicy() : com.bhstays.pms.domain.CancellationPolicy.MODERATE)
                .owner(resolveOwner(request.ownerId()))
                .commissionPercent(normalizeCommissionPercent(request.commissionPercent()))
                .cleaningChecklist(request.cleaningChecklist() != null
                        ? new java.util.ArrayList<>(request.cleaningChecklist()) : new java.util.ArrayList<>())
                .checkInTime(request.checkInTime() != null ? request.checkInTime() : java.time.LocalTime.of(14, 0))
                .checkOutTime(request.checkOutTime() != null ? request.checkOutTime() : java.time.LocalTime.of(11, 0))
                .facilities(request.facilities() != null ? request.facilities() : java.util.Set.of())
                .smartLockEnabled(request.smartLockEnabled())
                .smartLockProvider(request.smartLockProvider())
                .smartLockDeviceId(request.smartLockDeviceId())
                .lateCheckoutEnabled(request.lateCheckoutEnabled())
                .lateCheckoutTime(request.lateCheckoutTime())
                .lateCheckoutFee(request.lateCheckoutFee())
                .build();

        property = propertyRepository.save(property);
        if (property.getCommissionPercent() != null) {
            auditCommissionChange(property, null);
        }
        return toFullResponse(property);
    }

    @Transactional
    public PropertyResponse update(UUID id, PropertyUpdateRequest request) {
        Property property = findPropertyOrThrow(id);

        property.setName(request.name());
        property.setDescription(request.description());
        property.setPropertyType(request.propertyType());
        property.setStatus(request.status());
        property.setAddress(propertyMapper.toAddress(request.address()));
        property.setShowExactAddressPublicly(request.showExactAddressPublicly());
        property.setBedrooms(request.bedrooms());
        property.setBathrooms(request.bathrooms());
        property.setMaxGuests(request.maxGuests());
        property.setSizeSqm(request.sizeSqm());
        property.setBasePricePerNight(request.basePricePerNight());
        property.setWeekendPricePerNight(request.weekendPricePerNight());
        property.setCleaningFee(request.cleaningFee());
        property.setExtraGuestFee(request.extraGuestFee());
        property.setBaseGuestsIncluded(request.baseGuestsIncluded());
        property.setWeeklyDiscountPercent(request.weeklyDiscountPercent());
        property.setMonthlyDiscountPercent(request.monthlyDiscountPercent());
        property.setMinStayNights(request.minStayNights());
        property.setMaxStayNights(request.maxStayNights());
        if (request.cancellationPolicy() != null) property.setCancellationPolicy(request.cancellationPolicy());
        property.setOwner(resolveOwner(request.ownerId()));
        BigDecimal previousCommission = property.getCommissionPercent();
        property.setCommissionPercent(normalizeCommissionPercent(request.commissionPercent()));
        property.setCleaningChecklist(request.cleaningChecklist() != null
                ? new java.util.ArrayList<>(request.cleaningChecklist()) : new java.util.ArrayList<>());
        if (request.checkInTime() != null) property.setCheckInTime(request.checkInTime());
        if (request.checkOutTime() != null) property.setCheckOutTime(request.checkOutTime());
        property.setFacilities(request.facilities() != null ? request.facilities() : java.util.Set.of());
        property.setSmartLockEnabled(request.smartLockEnabled());
        property.setSmartLockProvider(request.smartLockProvider());
        property.setSmartLockDeviceId(request.smartLockDeviceId());
        assertValidLateCheckoutConfig(request.lateCheckoutEnabled(), request.lateCheckoutTime(),
                request.lateCheckoutFee(), property.getCheckOutTime());
        property.setLateCheckoutEnabled(request.lateCheckoutEnabled());
        property.setLateCheckoutTime(request.lateCheckoutTime());
        property.setLateCheckoutFee(request.lateCheckoutFee());

        property = propertyRepository.save(property);
        if (commissionChanged(previousCommission, property.getCommissionPercent())) {
            auditCommissionChange(property, previousCommission);
        }
        return toFullResponse(property);
    }

    /**
     * Sets (or, with null, clears) the BH Stays management commission. It
     * takes effect on the next report - nothing is stored per payment - so
     * changing it re-prices every period reported afterwards.
     */
    @Transactional
    public PropertyResponse updateCommission(UUID id, BigDecimal commissionPercent) {
        Property property = findPropertyOrThrow(id);
        BigDecimal previous = property.getCommissionPercent();
        property.setCommissionPercent(normalizeCommissionPercent(commissionPercent));
        property = propertyRepository.save(property);
        if (commissionChanged(previous, property.getCommissionPercent())) {
            auditCommissionChange(property, previous);
        }
        return toFullResponse(property);
    }

    /** Same rule as the request validation and the DB CHECK, for callers that bypass the controller. */
    private BigDecimal normalizeCommissionPercent(BigDecimal percent) {
        if (percent == null) {
            return null;
        }
        if (percent.signum() < 0 || percent.compareTo(MAX_COMMISSION_PERCENT) > 0) {
            throw new BadRequestException("Commission must be between 0 and 100");
        }
        if (percent.stripTrailingZeros().scale() > 2) {
            throw new BadRequestException("Commission can have at most two decimals");
        }
        return percent.setScale(2, RoundingMode.UNNECESSARY);
    }

    private static boolean commissionChanged(BigDecimal before, BigDecimal after) {
        if (before == null || after == null) {
            return before != after;
        }
        return before.compareTo(after) != 0;
    }

    /** Only the percentages are recorded - no owner, guest or revenue data. */
    private void auditCommissionChange(Property property, BigDecimal previous) {
        UserPrincipal actor = SecurityUtils.getCurrentPrincipal().orElse(null);
        auditService.recordEntityChange(AuditAction.PROPERTY_COMMISSION_CHANGED, "Property", property.getId(),
                actor != null ? actor.getId() : null, actor != null ? actor.getEmail() : null,
                "Management commission: " + describePercent(previous) + " -> "
                        + describePercent(property.getCommissionPercent()));
    }

    private static String describePercent(BigDecimal percent) {
        return percent != null ? percent.toPlainString() + "%" : "not configured";
    }

    /**
     * Switching modes is an explicit, audited administrator action - never
     * automatic. No cleanup is required when leaving ICAL: existing feeds
     * are kept (not deleted) so switching back doesn't require
     * re-registering them; {@link IcalImportService} simply skips feeds
     * for a property that is no longer in ICAL mode.
     */
    @Transactional
    public PropertyResponse updateIntegrationMode(UUID id, IntegrationMode newMode, User actor) {
        Property property = findPropertyOrThrow(id);
        IntegrationMode oldMode = property.getIntegrationMode();

        property.setIntegrationMode(newMode);
        property = propertyRepository.save(property);

        auditService.record(AuditAction.PROPERTY_INTEGRATION_MODE_CHANGED, actor,
                "Property " + id + " (" + property.getName() + ") integration mode: " + oldMode + " -> " + newMode,
                null, null);

        return toFullResponse(property);
    }

    @Transactional
    public void delete(UUID id) {
        Property property = findPropertyOrThrow(id);
        List<PropertyPhoto> photos = propertyPhotoRepository.findByPropertyIdOrderBySortOrderAsc(id);
        List<PropertyDocument> documents = propertyDocumentRepository.findByPropertyIdOrderByCreatedAtDesc(id);

        photos.forEach(photo -> fileStorageService.delete(photo.getFileKey()));
        documents.forEach(document -> fileStorageService.delete(document.getFileKey()));

        propertyRepository.delete(property);
    }

    @Transactional
    public PropertyPhotoResponse addPhoto(UUID propertyId, MultipartFile file, String caption) {
        Property property = findPropertyOrThrow(propertyId);
        FileStorageService.StoredFile stored = fileStorageService.storeImage(file, "properties/" + propertyId);

        boolean isFirstPhoto = propertyPhotoRepository.countByPropertyId(propertyId) == 0;
        int nextSortOrder = (int) propertyPhotoRepository.countByPropertyId(propertyId);

        PropertyPhoto photo = PropertyPhoto.builder()
                .property(property)
                .fileKey(stored.fileKey())
                .url(stored.url())
                .caption(caption)
                .sortOrder(nextSortOrder)
                .cover(isFirstPhoto)
                .build();

        photo = propertyPhotoRepository.save(photo);
        return propertyMapper.toPhotoResponse(photo);
    }

    @Transactional
    public void deletePhoto(UUID propertyId, UUID photoId) {
        PropertyPhoto photo = propertyPhotoRepository.findById(photoId)
                .filter(p -> p.getProperty().getId().equals(propertyId))
                .orElseThrow(() -> new ResourceNotFoundException("Photo not found"));

        boolean wasCover = photo.isCover();
        fileStorageService.delete(photo.getFileKey());
        propertyPhotoRepository.delete(photo);

        if (wasCover) {
            propertyPhotoRepository.findByPropertyIdOrderBySortOrderAsc(propertyId).stream()
                    .findFirst()
                    .ifPresent(next -> {
                        next.setCover(true);
                        propertyPhotoRepository.save(next);
                    });
        }
    }

    @Transactional
    public void setCoverPhoto(UUID propertyId, UUID photoId) {
        List<PropertyPhoto> photos = propertyPhotoRepository.findByPropertyIdOrderBySortOrderAsc(propertyId);
        boolean found = false;
        for (PropertyPhoto photo : photos) {
            boolean isTarget = photo.getId().equals(photoId);
            photo.setCover(isTarget);
            found = found || isTarget;
        }
        if (!found) {
            throw new ResourceNotFoundException("Photo not found");
        }
        propertyPhotoRepository.saveAll(photos);
    }

    @Transactional
    public PropertyDocumentResponse addDocument(UUID propertyId, MultipartFile file,
                                                 PropertyDocumentType documentType, User uploadedBy,
                                                 java.time.LocalDate expiresAt) {
        Property property = findPropertyOrThrow(propertyId);
        FileStorageService.StoredFile stored = fileStorageService.storeDocument(file, "properties/" + propertyId + "/documents");

        PropertyDocument document = PropertyDocument.builder()
                .property(property)
                .fileName(file.getOriginalFilename())
                .fileKey(stored.fileKey())
                .url(stored.url())
                .documentType(documentType)
                .uploadedBy(uploadedBy)
                .expiresAt(expiresAt)
                .createdAt(Instant.now())
                .build();

        document = propertyDocumentRepository.save(document);
        return propertyMapper.toDocumentResponse(document);
    }

    @Transactional
    public void deleteDocument(UUID propertyId, UUID documentId) {
        PropertyDocument document = getDocumentOrThrow(propertyId, documentId);

        fileStorageService.delete(document.getFileKey());
        propertyDocumentRepository.delete(document);
    }

    @Transactional(readOnly = true)
    public PropertyDocument getDocumentOrThrow(UUID propertyId, UUID documentId) {
        return propertyDocumentRepository.findById(documentId)
                .filter(d -> d.getProperty().getId().equals(propertyId))
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));
    }

    @Transactional(readOnly = true)
    public Resource loadDocumentResource(PropertyDocument document) {
        return fileStorageService.loadAsResource(document.getFileKey());
    }

    @Transactional(readOnly = true)
    public List<SeasonalRateResponse> listSeasonalRates(UUID propertyId) {
        findPropertyOrThrow(propertyId);
        return seasonalRateRepository.findByPropertyIdOrderByStartDateAsc(propertyId).stream()
                .map(seasonalRateMapper::toResponse)
                .toList();
    }

    @Transactional
    public SeasonalRateResponse addSeasonalRate(UUID propertyId, SeasonalRateRequest request) {
        Property property = findPropertyOrThrow(propertyId);
        validateSeasonalRateDates(request);

        SeasonalRate rate = SeasonalRate.builder()
                .property(property)
                .label(request.label())
                .startDate(request.startDate())
                .endDate(request.endDate())
                .pricePerNight(request.pricePerNight())
                .build();

        return seasonalRateMapper.toResponse(saveGuardingOverlap(rate));
    }

    @Transactional
    public SeasonalRateResponse updateSeasonalRate(UUID propertyId, UUID rateId, SeasonalRateRequest request) {
        SeasonalRate rate = findSeasonalRateOrThrow(propertyId, rateId);
        validateSeasonalRateDates(request);

        rate.setLabel(request.label());
        rate.setStartDate(request.startDate());
        rate.setEndDate(request.endDate());
        rate.setPricePerNight(request.pricePerNight());

        return seasonalRateMapper.toResponse(saveGuardingOverlap(rate));
    }

    @Transactional
    public void deleteSeasonalRate(UUID propertyId, UUID rateId) {
        seasonalRateRepository.delete(findSeasonalRateOrThrow(propertyId, rateId));
    }

    private void validateSeasonalRateDates(SeasonalRateRequest request) {
        if (request.endDate().isBefore(request.startDate())) {
            throw new BadRequestException("End date must be on or after the start date");
        }
    }

    private SeasonalRate saveGuardingOverlap(SeasonalRate rate) {
        try {
            return seasonalRateRepository.saveAndFlush(rate);
        } catch (DataIntegrityViolationException ex) {
            throw new BadRequestException("This date range overlaps an existing season for this property");
        }
    }

    private SeasonalRate findSeasonalRateOrThrow(UUID propertyId, UUID rateId) {
        return seasonalRateRepository.findById(rateId)
                .filter(r -> r.getProperty().getId().equals(propertyId))
                .orElseThrow(() -> new ResourceNotFoundException("Seasonal rate not found"));
    }

    private PropertyResponse toFullResponse(Property property) {
        List<PropertyPhoto> photos = propertyPhotoRepository.findByPropertyIdOrderBySortOrderAsc(property.getId());
        List<PropertyDocument> documents = propertyDocumentRepository.findByPropertyIdOrderByCreatedAtDesc(property.getId());
        return propertyMapper.toResponse(property, photos, documents);
    }

    private User resolveOwner(UUID ownerId) {
        if (ownerId == null) {
            return null;
        }
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new com.bhstays.pms.common.exception.BadRequestException("Owner not found"));
        if (owner.getRole() != Role.OWNER) {
            throw new com.bhstays.pms.common.exception.BadRequestException("Assigned user must have the OWNER role");
        }
        return owner;
    }

    private Property findPropertyOrThrow(UUID id) {
        return propertyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Property not found"));
    }

    private void assertValidLateCheckoutConfig(boolean enabled, java.time.LocalTime lateCheckoutTime,
                                                java.math.BigDecimal lateCheckoutFee,
                                                java.time.LocalTime effectiveCheckOutTime) {
        if (lateCheckoutFee != null && lateCheckoutFee.signum() < 0) {
            throw new com.bhstays.pms.common.exception.BadRequestException(
                    "Taxa de check-out târziu nu poate fi negativă");
        }
        if (enabled) {
            if (lateCheckoutTime == null) {
                throw new com.bhstays.pms.common.exception.BadRequestException(
                        "Este necesară o oră de check-out târziu cât timp opțiunea este activă");
            }
            if (!lateCheckoutTime.isAfter(effectiveCheckOutTime)) {
                throw new com.bhstays.pms.common.exception.BadRequestException(
                        "Ora de check-out târziu trebuie să fie după ora normală de check-out");
            }
        }
    }

    /**
     * Sends a one-time in-app reminder for each property document expiring
     * within {@link #DOCUMENT_EXPIRY_WARNING_DAYS} days (or already expired)
     * that hasn't been notified about yet.
     */
    @Transactional
    public void notifyExpiringDocuments() {
        java.time.LocalDate cutoff = java.time.LocalDate.now().plusDays(DOCUMENT_EXPIRY_WARNING_DAYS);
        List<PropertyDocument> expiring =
                propertyDocumentRepository.findByExpiresAtLessThanEqualAndExpiryNotifiedAtIsNull(cutoff);

        for (PropertyDocument document : expiring) {
            boolean alreadyExpired = document.getExpiresAt().isBefore(java.time.LocalDate.now());
            notificationService.notifyAdmins(
                    com.bhstays.pms.domain.NotificationType.DOCUMENT_EXPIRING,
                    (alreadyExpired ? "Document expirat: " : "Document expiră curând: ") + document.getFileName(),
                    document.getProperty().getName() + " — expiră la " + document.getExpiresAt(),
                    "/dashboard/properties/" + document.getProperty().getId());
            document.setExpiryNotifiedAt(Instant.now());
        }
        propertyDocumentRepository.saveAll(expiring);
    }
}
