package com.bhstays.pms.dto.owner;

import java.math.BigDecimal;
import java.util.List;

/**
 * An owner's money in one currency, on the same formula and transaction
 * dating as the statements: {@code ownerAmount = netRevenue -
 * bhStaysCommission}, {@code netPayout = ownerAmount - expensesTotal}
 * (owner-chargeable expenses only). {@code commissionPercents} are the
 * reservations' snapshotted percents involved. Money of reservations
 * without a verifiable snapshot is not commissioned and is counted in
 * {@code unallocatedNetRevenue} / {@code unallocatedReservationCount}.
 */
public record OwnerRevenueLine(
        String currency,
        BigDecimal capturedTotal,
        BigDecimal refundedTotal,
        BigDecimal netRevenue,
        BigDecimal commissionableBase,
        List<BigDecimal> commissionPercents,
        BigDecimal bhStaysCommission,
        BigDecimal ownerAmount,
        BigDecimal netPayout,
        BigDecimal expensesTotal,
        BigDecimal unallocatedNetRevenue,
        int unallocatedReservationCount
) {
}
