package com.bhstays.pms.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.LastModifiedBy;

@Getter
@Setter
@Entity
@Table(name = "properties")
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class Property extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(length = 4000)
    private String description;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "property_type", nullable = false)
    @Builder.Default
    private PropertyType propertyType = PropertyType.APARTMENT;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    @Builder.Default
    private PropertyStatus status = PropertyStatus.DRAFT;

    @Embedded
    private Address address;

    /** When false (default), the public property page shows only an approximate location. */
    @Column(name = "show_exact_address_publicly", nullable = false)
    @Builder.Default
    private boolean showExactAddressPublicly = false;

    @Column(nullable = false)
    @Builder.Default
    private int bedrooms = 0;

    @Column(nullable = false)
    @Builder.Default
    private int bathrooms = 0;

    @Column(name = "max_guests", nullable = false)
    @Builder.Default
    private int maxGuests = 1;

    @Column(name = "size_sqm")
    private BigDecimal sizeSqm;

    @Column(name = "base_price_per_night", precision = 10, scale = 2)
    private BigDecimal basePricePerNight;

    @Column(name = "weekend_price_per_night", precision = 10, scale = 2)
    private BigDecimal weekendPricePerNight;

    @Column(name = "cleaning_fee", precision = 10, scale = 2)
    private BigDecimal cleaningFee;

    @Column(name = "extra_guest_fee", precision = 10, scale = 2)
    private BigDecimal extraGuestFee;

    /** Guests included in the nightly price before {@link #extraGuestFee} applies. Null = no extra-guest fee. */
    @Column(name = "base_guests_included")
    private Integer baseGuestsIncluded;

    @Column(name = "weekly_discount_percent", precision = 5, scale = 2)
    private BigDecimal weeklyDiscountPercent;

    @Column(name = "monthly_discount_percent", precision = 5, scale = 2)
    private BigDecimal monthlyDiscountPercent;

    @Column(name = "min_stay_nights")
    private Integer minStayNights;

    @Column(name = "max_stay_nights")
    private Integer maxStayNights;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "cancellation_policy", nullable = false)
    @Builder.Default
    private CancellationPolicy cancellationPolicy = CancellationPolicy.MODERATE;

    /** Which system owns this property's availability - see {@link IntegrationMode}. */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "integration_mode", nullable = false)
    @Builder.Default
    private IntegrationMode integrationMode = IntegrationMode.MANUAL;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private User owner;

    /**
     * BH Stays management commission, 0.00-100.00, applied to the
     * accommodation part of captured revenue only. Null = not configured:
     * no BH Stays revenue is reported for the property until it is set.
     */
    @Column(name = "commission_percent", precision = 5, scale = 2)
    private BigDecimal commissionPercent;

    /** Checklist template a cleaner must complete; snapshotted onto each cleaning task at creation. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "property_checklist_items", joinColumns = @JoinColumn(name = "property_id"))
    @OrderColumn(name = "sort_order")
    @Column(name = "item")
    @Builder.Default
    private List<String> cleaningChecklist = new ArrayList<>();

    @Column(name = "check_in_time", nullable = false)
    @Builder.Default
    private LocalTime checkInTime = LocalTime.of(14, 0);

    @Column(name = "check_out_time", nullable = false)
    @Builder.Default
    private LocalTime checkOutTime = LocalTime.of(11, 0);

    @Column(name = "smart_lock_enabled", nullable = false)
    @Builder.Default
    private boolean smartLockEnabled = false;

    @Column(name = "smart_lock_provider")
    private String smartLockProvider;

    @Column(name = "smart_lock_device_id")
    private String smartLockDeviceId;

    @Column(name = "late_checkout_enabled", nullable = false)
    @Builder.Default
    private boolean lateCheckoutEnabled = false;

    /** The later checkout time offered, e.g. 14:00 instead of the default checkOutTime. */
    @Column(name = "late_checkout_time")
    private LocalTime lateCheckoutTime;

    @Column(name = "late_checkout_fee")
    private BigDecimal lateCheckoutFee;

    @Column(name = "ical_export_token", unique = true)
    private String icalExportToken;

    @ElementCollection(targetClass = Facility.class, fetch = FetchType.EAGER)
    @CollectionTable(name = "property_facilities", joinColumns = @JoinColumn(name = "property_id"))
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "facility")
    @Builder.Default
    private Set<Facility> facilities = new HashSet<>();

    @CreatedBy
    @Column(name = "created_by")
    private UUID createdBy;

    @LastModifiedBy
    @Column(name = "updated_by")
    private UUID updatedBy;
}
