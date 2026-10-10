package com.bhstays.pms.dto.report;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One property in one currency on the /finance page. The revenue figures
 * are exactly those of the property commission report for the same period
 * (they come from the same calculation, dated by the transactions);
 * expenses (by expense date) and net profit are added on top.
 *
 * <p>{@code commissionPercents} are the reservations' snapshotted percents
 * involved in the period; {@code propertyCommissionPercent} is the
 * property's current setting, which only applies to new reservations.
 */
public record FinancialReportRowResponse(
        UUID propertyId,
        String propertyName,
        String ownerName,
        String currency,
        BigDecimal capturedTotal,
        BigDecimal refundedTotal,
        BigDecimal netRevenue,
        BigDecimal commissionableBase,
        java.util.List<BigDecimal> commissionPercents,
        BigDecimal propertyCommissionPercent,
        BigDecimal bhStaysRevenue,
        BigDecimal ownerAmount,
        BigDecimal unallocatedNetRevenue,
        int unallocatedReservationCount,
        BigDecimal expensesTotal,
        /* netRevenue - expensesTotal. */
        BigDecimal netProfit,
        /* Deprecated: same value as {@code netRevenue} (it was never gross); kept for API compatibility. */
        @Deprecated BigDecimal grossRevenue,
        /*
         * Deprecated: same value as {@code bhStaysRevenue}; kept for API compatibility.
         */
        @Deprecated BigDecimal commissionAmount
) {
}
