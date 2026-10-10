package com.bhstays.pms.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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
@Table(name = "reservations")
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(callSuper = true, exclude = "property")
@EqualsAndHashCode(callSuper = true)
public class Reservation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "property_id", nullable = false)
    private Property property;

    @Column(name = "guest_first_name", nullable = false)
    private String guestFirstName;

    @Column(name = "guest_last_name", nullable = false)
    private String guestLastName;

    @Column(name = "guest_email")
    private String guestEmail;

    @Column(name = "guest_phone")
    private String guestPhone;

    @Column(name = "check_in_date", nullable = false)
    private LocalDate checkInDate;

    @Column(name = "check_out_date", nullable = false)
    private LocalDate checkOutDate;

    @Column(name = "number_of_guests", nullable = false)
    @Builder.Default
    private int numberOfGuests = 1;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    @Builder.Default
    private ReservationStatus status = ReservationStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    @Builder.Default
    private ReservationSource source = ReservationSource.DIRECT;

    @Column(name = "total_amount")
    private BigDecimal totalAmount;

    @Column(nullable = false)
    @Builder.Default
    private String currency = "RON";

    /**
     * Price breakdown snapshot of {@link #totalAmount}. Only
     * {@code accommodationAmount} is commissionable: nightly rates (base,
     * weekend, seasonal, dynamic) after the weekly/monthly stay discount.
     * Every other part is kept in its own column and is never commissioned.
     * The parts always add up exactly to the total (DB CHECK); all are null
     * when the total did not come from the pricing engine, so the split is
     * unknown.
     */
    @Column(name = "accommodation_amount", precision = 10, scale = 2)
    private BigDecimal accommodationAmount;

    @Column(name = "cleaning_fee_amount", precision = 10, scale = 2)
    private BigDecimal cleaningFeeAmount;

    @Column(name = "extra_guest_fee_amount", precision = 10, scale = 2)
    private BigDecimal extraGuestFeeAmount;

    @Column(name = "late_checkout_fee_amount", precision = 10, scale = 2)
    private BigDecimal lateCheckoutFeeAmount;

    @Column(name = "tax_amount", precision = 10, scale = 2)
    private BigDecimal taxAmount;

    @Column(name = "addon_amount", precision = 10, scale = 2)
    private BigDecimal addonAmount;

    /**
     * The property's BH Stays management commission when this reservation
     * was created. The financial reports use it instead of the property's
     * current percent, so changing the percent only affects reservations
     * created afterwards. Null when the property had none configured, and
     * for reservations created before the snapshot existed.
     */
    @Column(name = "management_commission_percent_snapshot", precision = 5, scale = 2, updatable = false)
    @DecimalMin("0.00")
    @DecimalMax("100.00")
    @Digits(integer = 3, fraction = 2)
    private BigDecimal managementCommissionPercentSnapshot;

    @Column(length = 2000)
    private String notes;

    @Column(name = "management_token", unique = true)
    private String managementToken;

    @Column(name = "idempotency_key", unique = true)
    private String idempotencyKey;

    @Column(name = "hold_expires_at")
    private Instant holdExpiresAt;

    /** Stable event UID from an imported Airbnb/Booking.com .ics feed; null for reservations created in the PMS. */
    @Column(name = "external_uid", unique = true)
    private String externalUid;

    @Column(name = "access_code", length = 50)
    private String accessCode;

    @Column(name = "access_code_sent_at")
    private Instant accessCodeSentAt;

    @CreatedBy
    @Column(name = "created_by")
    private UUID createdBy;

    @LastModifiedBy
    @Column(name = "updated_by")
    private UUID updatedBy;

    /** Every creation path (staff, public booking, iCal import) goes through here; updates never change it. */
    @PrePersist
    void snapshotManagementCommission() {
        if (managementCommissionPercentSnapshot == null && property != null) {
            managementCommissionPercentSnapshot = property.getCommissionPercent();
        }
    }

    public String getGuestFullName() {
        return guestFirstName + " " + guestLastName;
    }
}
