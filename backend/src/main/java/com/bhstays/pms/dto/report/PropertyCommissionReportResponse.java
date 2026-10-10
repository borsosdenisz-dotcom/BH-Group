package com.bhstays.pms.dto.report;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * {@code currencies} has one entry per currency with money movements in the
 * period - never a blended total. {@code commissionPercent} /
 * {@code commissionConfigured} are the property's current setting, which
 * applies to reservations created from now on; past reservations keep the
 * percent snapshotted when they were created.
 */
public record PropertyCommissionReportResponse(
        UUID propertyId,
        String propertyName,
        LocalDate from,
        LocalDate to,
        BigDecimal commissionPercent,
        boolean commissionConfigured,
        List<PropertyCommissionCurrencyResponse> currencies
) {
}
