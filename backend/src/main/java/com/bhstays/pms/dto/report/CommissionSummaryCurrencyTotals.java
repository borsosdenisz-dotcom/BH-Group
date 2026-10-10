package com.bhstays.pms.dto.report;

import java.math.BigDecimal;

/**
 * Portfolio totals for one currency in a period (transaction-dated).
 * {@code propertiesNetRevenue} is money collected for the owners'
 * properties - not BH Stays revenue; BH Stays only keeps
 * {@code bhStaysRevenue}. Always
 * {@code propertiesNetRevenue = bhStaysRevenue + ownersAmount}; money from
 * reservations without a verifiable snapshot is part of the owners' amount
 * and counted in {@code unallocatedNetRevenue}.
 */
public record CommissionSummaryCurrencyTotals(
        String currency,
        BigDecimal capturedTotal,
        BigDecimal refundedTotal,
        BigDecimal propertiesNetRevenue,
        BigDecimal bhStaysRevenue,
        BigDecimal ownersAmount,
        int includedPropertyCount,
        BigDecimal unallocatedNetRevenue,
        int unallocatedReservationCount
) {
}
