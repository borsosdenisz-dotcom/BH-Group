package com.bhstays.pms.service;

import com.bhstays.pms.common.exception.ResourceNotFoundException;
import com.bhstays.pms.dto.report.CommissionSummaryCurrencyTotals;
import com.bhstays.pms.dto.report.CommissionSummaryResponse;
import com.bhstays.pms.dto.report.PropertyCommissionCurrencyResponse;
import com.bhstays.pms.dto.report.PropertyCommissionReportResponse;
import com.bhstays.pms.dto.report.UnconfiguredPropertyResponse;
import com.bhstays.pms.repository.FinancialEventRepository;
import com.bhstays.pms.repository.PropertyRepository;
import com.bhstays.pms.repository.projection.PropertyCommissionSettings;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The single source of truth for collected money. Only captured payments
 * count (pending, processing, failed, cancelled and expired card attempts
 * never do, and a booking hold has no captured payment), minus successful
 * refunds; each movement is dated by its own transaction (see
 * {@link FinancialPeriod} and {@link PropertyCommissionCalculator}).
 *
 * <p>The property report, the dashboard, /finance
 * ({@link FinancialReportService}), owner statements and the owner portal
 * ({@link OwnerFinancialsService}) all take their per-property,
 * per-currency figures from {@link #linesFor}, so the same property and
 * period can never show different numbers.
 *
 * <p>Each call runs a fixed number of queries - property settings, one
 * query over the payment ledger - however many properties there are.
 */
@Service
@RequiredArgsConstructor
public class PropertyCommissionReportService {

    private final PropertyRepository propertyRepository;
    private final FinancialEventRepository financialEventRepository;

    @Transactional(readOnly = true)
    public PropertyCommissionReportResponse propertyReport(UUID propertyId, LocalDate from, LocalDate to) {
        FinancialPeriod.validate(from, to);
        PropertyCommissionSettings property = propertyRepository.findCommissionSettings(propertyId)
                .orElseThrow(() -> new ResourceNotFoundException("Property not found"));

        List<PropertyCommissionCurrencyResponse> lines =
                linesFor(List.of(property), from, to).getOrDefault(property.id(), List.of());

        return new PropertyCommissionReportResponse(
                property.id(), property.name(), from, to,
                property.commissionPercent() != null ? PropertyCommissionCalculator.money(property.commissionPercent()) : null,
                property.commissionPercent() != null,
                lines);
    }

    @Transactional(readOnly = true)
    public CommissionSummaryResponse summary(LocalDate from, LocalDate to) {
        FinancialPeriod.validate(from, to);
        List<PropertyCommissionSettings> properties = propertyRepository.findAllCommissionSettings();
        Map<UUID, List<PropertyCommissionCurrencyResponse>> linesByProperty = linesFor(properties, from, to);

        List<UnconfiguredPropertyResponse> unconfigured = properties.stream()
                .filter(property -> property.commissionPercent() == null)
                .map(property -> new UnconfiguredPropertyResponse(property.id(), property.name()))
                .toList();

        return new CommissionSummaryResponse(from, to, totalsByCurrency(linesByProperty.values()), unconfigured);
    }

    /**
     * Per-property, per-currency lines for the given properties in the
     * period. Properties without any capture or refund in the period have
     * no entry.
     */
    @Transactional(readOnly = true)
    public Map<UUID, List<PropertyCommissionCurrencyResponse>> linesFor(
            Collection<PropertyCommissionSettings> properties, LocalDate from, LocalDate to) {
        FinancialPeriod period = FinancialPeriod.of(from, to);
        if (properties.isEmpty()) {
            return Map.of();
        }
        List<UUID> ids = properties.stream().map(PropertyCommissionSettings::id).toList();
        return PropertyCommissionCalculator.calculate(
                financialEventRepository.findEvents(ids, period.start(), period.end()),
                period.start(), period.end());
    }

    /** Portfolio totals, one per currency, sorted by currency. */
    public static List<CommissionSummaryCurrencyTotals> totalsByCurrency(
            Collection<List<PropertyCommissionCurrencyResponse>> linesByProperty) {
        Map<String, List<PropertyCommissionCurrencyResponse>> byCurrency = new TreeMap<>();
        for (List<PropertyCommissionCurrencyResponse> lines : linesByProperty) {
            for (PropertyCommissionCurrencyResponse line : lines) {
                byCurrency.computeIfAbsent(line.currency(), currency -> new ArrayList<>()).add(line);
            }
        }
        return byCurrency.entrySet().stream()
                .map(entry -> PropertyCommissionCalculator.totals(entry.getKey(), entry.getValue()))
                .toList();
    }
}
