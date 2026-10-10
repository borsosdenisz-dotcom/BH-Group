package com.bhstays.pms.dto.report;

import java.math.BigDecimal;

/**
 * /finance totals for one currency. The revenue figures are the same
 * {@link CommissionSummaryCurrencyTotals} the dashboard shows for the same
 * period and properties; expenses and net profit are added on top.
 */
public record FinancialReportCurrencyTotals(
        String currency,
        CommissionSummaryCurrencyTotals revenue,
        BigDecimal totalExpenses,
        /* revenue.propertiesNetRevenue - totalExpenses. */
        BigDecimal totalNetProfit,
        /* Deprecated: same value as {@code revenue.propertiesNetRevenue}; kept for API compatibility. */
        @Deprecated BigDecimal totalGrossRevenue,
        /* Deprecated: same value as {@code revenue.bhStaysRevenue}; kept for API compatibility. */
        @Deprecated BigDecimal totalCommission
) {
}
