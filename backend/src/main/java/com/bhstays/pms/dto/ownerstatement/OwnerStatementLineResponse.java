package com.bhstays.pms.dto.ownerstatement;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One property on a statement. For CAPTURED_ACCOMMODATION statements the
 * figures are those of the property commission report for the period:
 * {@code grossRevenue} is the net collected revenue (captured - refunds),
 * {@code ownerAmount = grossRevenue - commissionAmount} and
 * {@code netAmount = ownerAmount - expensesTotal}. The extra fields are null
 * on LEGACY_GROSS statements.
 */
public record OwnerStatementLineResponse(
        UUID propertyId,
        String propertyName,
        BigDecimal grossRevenue,
        BigDecimal commissionAmount,
        BigDecimal expensesTotal,
        BigDecimal netAmount,
        BigDecimal capturedTotal,
        BigDecimal refundedTotal,
        BigDecimal commissionableBase,
        BigDecimal commissionPercent,
        BigDecimal ownerAmount,
        BigDecimal unallocatedNetRevenue,
        Integer unallocatedReservationCount
) {
}
