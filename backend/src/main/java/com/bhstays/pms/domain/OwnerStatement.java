package com.bhstays.pms.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@Entity
@Table(name = "owner_statements")
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(callSuper = true, exclude = {"owner", "generatedBy"})
@EqualsAndHashCode(callSuper = true)
public class OwnerStatement extends BaseEntity {

    public static final String CALCULATION_LEGACY_GROSS = "LEGACY_GROSS";
    /** Captured payments minus refunds; commission on the accommodation part only. */
    public static final String CALCULATION_CAPTURED_ACCOMMODATION = "CAPTURED_ACCOMMODATION";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(nullable = false)
    @Builder.Default
    private String currency = "RON";

    @Column(name = "gross_revenue", nullable = false)
    private BigDecimal grossRevenue;

    @Column(name = "commission_amount", nullable = false)
    private BigDecimal commissionAmount;

    @Column(name = "expenses_total", nullable = false)
    private BigDecimal expensesTotal;

    @Column(name = "net_payout", nullable = false)
    private BigDecimal netPayout;

    /**
     * How the figures were computed. {@code LEGACY_GROSS} statements (issued
     * before V40) took the commission on the whole net amount and have none
     * of the fields below; they are kept exactly as issued.
     */
    @Column(name = "calculation_method", nullable = false)
    @Builder.Default
    private String calculationMethod = CALCULATION_LEGACY_GROSS;

    @Column(name = "captured_total")
    private BigDecimal capturedTotal;

    @Column(name = "refunded_total")
    private BigDecimal refundedTotal;

    @Column(name = "commissionable_base")
    private BigDecimal commissionableBase;

    /** Net revenue minus BH Stays commission, before owner-chargeable expenses. */
    @Column(name = "owner_amount")
    private BigDecimal ownerAmount;

    @Column(name = "unallocated_net_revenue")
    private BigDecimal unallocatedNetRevenue;

    @Column(name = "unallocated_reservation_count")
    private Integer unallocatedReservationCount;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false)
    @Builder.Default
    private OwnerStatementStatus status = OwnerStatementStatus.ISSUED;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "generated_by")
    private User generatedBy;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "payment_reference")
    private String paymentReference;
}
