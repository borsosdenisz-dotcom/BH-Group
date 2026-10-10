package com.bhstays.pms.service;

import com.bhstays.pms.dto.report.CommissionSummaryCurrencyTotals;
import com.bhstays.pms.dto.report.FinancialReportCurrencyTotals;
import com.bhstays.pms.dto.report.FinancialReportRowResponse;
import com.bhstays.pms.dto.report.FinancialReportSummaryResponse;
import com.bhstays.pms.dto.report.PropertyCommissionCurrencyResponse;
import com.bhstays.pms.repository.ExpenseRepository;
import com.bhstays.pms.repository.PropertyRepository;
import com.bhstays.pms.repository.projection.PropertyCommissionSettings;
import com.bhstays.pms.repository.projection.PropertyCurrencyAmount;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The /finance report: per property and currency, the collected-money
 * figures of {@link PropertyCommissionReportService} (captures and
 * successful refunds dated by their transactions, commission on
 * accommodation only, at each reservation's snapshotted percent) plus the
 * property's expenses (by expense date). It never computes revenue or commission itself, so
 * it always matches the property report and the dashboard for the same
 * period. Amounts in different currencies are never added together.
 */
@Service
@RequiredArgsConstructor
public class FinancialReportService {

    private final PropertyRepository propertyRepository;
    private final ExpenseRepository expenseRepository;
    private final PropertyCommissionReportService commissionReportService;

    @Transactional(readOnly = true)
    public FinancialReportSummaryResponse summary(UUID propertyId, LocalDate from, LocalDate to) {
        FinancialPeriod.validate(from, to);
        List<PropertyCommissionSettings> properties = propertyId != null
                ? propertyRepository.findCommissionSettings(propertyId).map(List::of).orElseGet(List::of)
                : propertyRepository.findAllCommissionSettings();
        if (properties.isEmpty()) {
            return new FinancialReportSummaryResponse(List.of(), List.of());
        }

        Map<UUID, List<PropertyCommissionCurrencyResponse>> revenue =
                commissionReportService.linesFor(properties, from, to);
        Map<UUID, Map<String, BigDecimal>> expenses = expensesByProperty(from, to);

        List<FinancialReportRowResponse> rows = new ArrayList<>();
        for (PropertyCommissionSettings property : properties) {
            rows.addAll(buildRows(property, revenue.getOrDefault(property.id(), List.of()),
                    expenses.getOrDefault(property.id(), Map.of())));
        }

        return new FinancialReportSummaryResponse(rows, totals(revenue, rows));
    }

    @Transactional(readOnly = true)
    public List<List<String>> exportRows(UUID propertyId, LocalDate from, LocalDate to) {
        return summary(propertyId, from, to).rows().stream()
                .map(row -> List.of(
                        row.propertyName(),
                        row.ownerName() != null ? row.ownerName() : "BH Stays",
                        row.capturedTotal().toPlainString(),
                        row.refundedTotal().toPlainString(),
                        row.netRevenue().toPlainString(),
                        row.commissionableBase().toPlainString(),
                        row.commissionPercents().stream().map(BigDecimal::toPlainString)
                                .collect(java.util.stream.Collectors.joining(" / ")),
                        row.bhStaysRevenue().toPlainString(),
                        row.ownerAmount().toPlainString(),
                        row.unallocatedNetRevenue().toPlainString(),
                        row.expensesTotal().toPlainString(),
                        row.netProfit().toPlainString(),
                        row.currency()
                ))
                .toList();
    }

    /**
     * One row per currency with money movements or expenses. A property with
     * neither has no row: there is no currency to show it in, and none is
     * assumed.
     */
    private List<FinancialReportRowResponse> buildRows(PropertyCommissionSettings property,
                                                       List<PropertyCommissionCurrencyResponse> revenueLines,
                                                       Map<String, BigDecimal> expensesByCurrency) {
        Map<String, PropertyCommissionCurrencyResponse> revenueByCurrency = new HashMap<>();
        revenueLines.forEach(line -> revenueByCurrency.put(line.currency(), line));

        Set<String> currencies = new TreeSet<>();
        currencies.addAll(revenueByCurrency.keySet());
        currencies.addAll(expensesByCurrency.keySet());

        return currencies.stream()
                .map(currency -> {
                    PropertyCommissionCurrencyResponse line = revenueByCurrency.containsKey(currency)
                            ? revenueByCurrency.get(currency)
                            : PropertyCommissionCalculator.empty(currency);
                    BigDecimal expensesTotal = PropertyCommissionCalculator.money(
                            expensesByCurrency.getOrDefault(currency, BigDecimal.ZERO));
                    return new FinancialReportRowResponse(
                            property.id(), property.name(), property.ownerName(), currency,
                            line.capturedTotal(), line.refundedTotal(), line.netRevenue(),
                            line.commissionableBase(), line.commissionPercents(),
                            property.commissionPercent() != null
                                    ? PropertyCommissionCalculator.money(property.commissionPercent()) : null,
                            line.bhStaysRevenue(), line.ownerAmount(),
                            line.unallocatedNetRevenue(), line.unallocatedReservationCount(),
                            expensesTotal,
                            line.netRevenue().subtract(expensesTotal),
                            line.netRevenue(),
                            line.bhStaysRevenue());
                })
                .toList();
    }

    /** Revenue totals are exactly the dashboard's; currencies that only have expenses get zero revenue. */
    private List<FinancialReportCurrencyTotals> totals(Map<UUID, List<PropertyCommissionCurrencyResponse>> revenue,
                                                       List<FinancialReportRowResponse> rows) {
        Map<String, CommissionSummaryCurrencyTotals> revenueTotals = new HashMap<>();
        PropertyCommissionReportService.totalsByCurrency(revenue.values())
                .forEach(total -> revenueTotals.put(total.currency(), total));

        Map<String, BigDecimal> expensesByCurrency = new TreeMap<>();
        for (FinancialReportRowResponse row : rows) {
            expensesByCurrency.merge(row.currency(), row.expensesTotal(), BigDecimal::add);
        }

        return expensesByCurrency.entrySet().stream()
                .map(entry -> {
                    String currency = entry.getKey();
                    CommissionSummaryCurrencyTotals revenueTotal = revenueTotals.containsKey(currency)
                            ? revenueTotals.get(currency)
                            : PropertyCommissionCalculator.totals(currency, List.of());
                    BigDecimal expensesTotal = PropertyCommissionCalculator.money(entry.getValue());
                    return new FinancialReportCurrencyTotals(currency, revenueTotal, expensesTotal,
                            revenueTotal.propertiesNetRevenue().subtract(expensesTotal),
                            revenueTotal.propertiesNetRevenue(), revenueTotal.bhStaysRevenue());
                })
                .toList();
    }

    private Map<UUID, Map<String, BigDecimal>> expensesByProperty(LocalDate from, LocalDate to) {
        Map<UUID, Map<String, BigDecimal>> result = new HashMap<>();
        for (PropertyCurrencyAmount amount : expenseRepository.sumGroupedByPropertyAndCurrency(
                FinancialPeriod.startOf(from), FinancialPeriod.endOf(to))) {
            result.computeIfAbsent(amount.propertyId(), id -> new HashMap<>())
                    .merge(amount.currency(), amount.amount(), BigDecimal::add);
        }
        return result;
    }
}
