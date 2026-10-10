package com.bhstays.pms.dto.report;

import java.time.LocalDate;
import java.util.List;

/**
 * {@code unconfiguredProperties} lists every property without a commission
 * percentage, whether or not it collected anything in the period: new
 * reservations of these properties get no commission snapshot, so they
 * should be configured before taking bookings.
 */
public record CommissionSummaryResponse(
        LocalDate from,
        LocalDate to,
        List<CommissionSummaryCurrencyTotals> totals,
        List<UnconfiguredPropertyResponse> unconfiguredProperties
) {
}
