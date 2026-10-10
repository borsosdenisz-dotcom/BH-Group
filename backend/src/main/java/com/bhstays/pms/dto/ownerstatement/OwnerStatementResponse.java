package com.bhstays.pms.dto.ownerstatement;

import com.bhstays.pms.domain.OwnerStatementStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * A statement covers exactly one currency. {@code calculationMethod} is
 * CAPTURED_ACCOMMODATION for statements on the shared formula (see
 * {@link OwnerStatementLineResponse}) or LEGACY_GROSS for statements issued
 * before it, whose commission was taken on the whole net amount and whose
 * extra fields are null.
 */
public record OwnerStatementResponse(
        UUID id,
        UUID ownerId,
        String ownerName,
        LocalDate periodStart,
        LocalDate periodEnd,
        String currency,
        BigDecimal grossRevenue,
        BigDecimal commissionAmount,
        BigDecimal expensesTotal,
        BigDecimal netPayout,
        OwnerStatementStatus status,
        String generatedByName,
        Instant paidAt,
        String paymentReference,
        Instant createdAt,
        List<OwnerStatementLineResponse> lines,
        String calculationMethod,
        BigDecimal capturedTotal,
        BigDecimal refundedTotal,
        BigDecimal commissionableBase,
        BigDecimal ownerAmount,
        BigDecimal unallocatedNetRevenue,
        Integer unallocatedReservationCount
) {
}
