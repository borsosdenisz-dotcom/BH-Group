package com.bhstays.pms.service;

import com.bhstays.pms.dto.owner.OwnerRevenueLine;
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
import java.util.TreeSet;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What BH Stays owes an owner, per property and currency - used by owner
 * statement generation, the owner dashboard and the owner's property cards.
 *
 * <p>Revenue and commission are never computed here: they are the lines of
 * {@link PropertyCommissionReportService}, exactly what the property
 * report, the dashboard and /finance show for the same property and
 * period. The only thing added is the owner's side of expenses - only
 * those explicitly flagged {@code chargeToOwner} reduce the payout:
 * {@code netPayout = ownerAmount - owner-chargeable expenses}.
 */
@Service
@RequiredArgsConstructor
public class OwnerFinancialsService {

    private final PropertyRepository propertyRepository;
    private final ExpenseRepository expenseRepository;
    private final PropertyCommissionReportService commissionReportService;

    /**
     * One property in one currency for the period. Captures and refunds are
     * dated by their transactions, so a row can be a pure adjustment (only a
     * refund of an earlier period's capture) with negative amounts.
     */
    public record PropertyFinancials(
            UUID propertyId,
            String propertyName,
            String currency,
            BigDecimal capturedTotal,
            BigDecimal refundedTotal,
            BigDecimal netRevenue,
            BigDecimal commissionableBase,
            List<BigDecimal> commissionPercents,
            BigDecimal bhStaysCommission,
            BigDecimal ownerAmount,
            BigDecimal unallocatedNetRevenue,
            int unallocatedReservationCount,
            BigDecimal expensesTotal,
            BigDecimal netPayout) {

        public boolean hasActivity() {
            return capturedTotal.signum() != 0 || refundedTotal.signum() != 0 || expensesTotal.signum() != 0;
        }

        /** The reservations' snapshot percent when they all share one, otherwise null. */
        public BigDecimal singleCommissionPercent() {
            return commissionPercents.size() == 1 ? commissionPercents.get(0) : null;
        }
    }

    /** Adds up one currency's rows exactly the way a statement does. */
    public static OwnerRevenueLine aggregate(String currency, List<PropertyFinancials> rows) {
        BigDecimal captured = BigDecimal.ZERO;
        BigDecimal refunded = BigDecimal.ZERO;
        BigDecimal net = BigDecimal.ZERO;
        BigDecimal base = BigDecimal.ZERO;
        BigDecimal commission = BigDecimal.ZERO;
        BigDecimal owner = BigDecimal.ZERO;
        BigDecimal expenses = BigDecimal.ZERO;
        BigDecimal unallocatedNet = BigDecimal.ZERO;
        int unallocatedReservations = 0;
        java.util.TreeSet<BigDecimal> percents = new java.util.TreeSet<>();
        for (PropertyFinancials row : rows) {
            captured = captured.add(row.capturedTotal());
            refunded = refunded.add(row.refundedTotal());
            net = net.add(row.netRevenue());
            base = base.add(row.commissionableBase());
            commission = commission.add(row.bhStaysCommission());
            owner = owner.add(row.ownerAmount());
            expenses = expenses.add(row.expensesTotal());
            unallocatedNet = unallocatedNet.add(row.unallocatedNetRevenue());
            unallocatedReservations += row.unallocatedReservationCount();
            percents.addAll(row.commissionPercents());
        }
        return new OwnerRevenueLine(currency, captured, refunded, net, base, List.copyOf(percents),
                commission, owner, owner.subtract(expenses), expenses, unallocatedNet, unallocatedReservations);
    }

    /** One entry per property per currency with captures, refunds or owner-chargeable expenses in the period. */
    @Transactional(readOnly = true)
    public List<PropertyFinancials> computeForOwner(UUID ownerId, LocalDate from, LocalDate to) {
        List<PropertyCommissionSettings> properties = propertyRepository.findCommissionSettingsByOwnerId(ownerId);
        if (properties.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<PropertyCommissionCurrencyResponse>> revenue =
                commissionReportService.linesFor(properties, from, to);
        Map<UUID, Map<String, BigDecimal>> expenses = new HashMap<>();
        for (PropertyCurrencyAmount amount
                : expenseRepository.sumChargeableToOwnerGroupedByPropertyAndCurrency(ownerId,
                        FinancialPeriod.startOf(from), FinancialPeriod.endOf(to))) {
            expenses.computeIfAbsent(amount.propertyId(), id -> new HashMap<>())
                    .merge(amount.currency(), amount.amount(), BigDecimal::add);
        }

        List<PropertyFinancials> result = new ArrayList<>();
        for (PropertyCommissionSettings property : properties) {
            result.addAll(rowsFor(property, revenue.getOrDefault(property.id(), List.of()),
                    expenses.getOrDefault(property.id(), Map.of())));
        }
        return result;
    }

    private List<PropertyFinancials> rowsFor(PropertyCommissionSettings property,
                                             List<PropertyCommissionCurrencyResponse> revenueLines,
                                             Map<String, BigDecimal> expensesByCurrency) {
        Map<String, PropertyCommissionCurrencyResponse> revenueByCurrency = new HashMap<>();
        revenueLines.forEach(line -> revenueByCurrency.put(line.currency(), line));
        Set<String> currencies = new TreeSet<>(revenueByCurrency.keySet());
        currencies.addAll(expensesByCurrency.keySet());

        List<PropertyFinancials> rows = new ArrayList<>();
        for (String currency : currencies) {
            PropertyCommissionCurrencyResponse line = revenueByCurrency.containsKey(currency)
                    ? revenueByCurrency.get(currency)
                    : PropertyCommissionCalculator.empty(currency);
            BigDecimal expensesTotal = PropertyCommissionCalculator.money(
                    expensesByCurrency.getOrDefault(currency, BigDecimal.ZERO));
            PropertyFinancials row = new PropertyFinancials(
                    property.id(), property.name(), currency,
                    line.capturedTotal(), line.refundedTotal(), line.netRevenue(), line.commissionableBase(),
                    line.commissionPercents(),
                    line.bhStaysRevenue(), line.ownerAmount(),
                    line.unallocatedNetRevenue(), line.unallocatedReservationCount(),
                    expensesTotal,
                    line.ownerAmount().subtract(expensesTotal));
            if (row.hasActivity()) {
                rows.add(row);
            }
        }
        return rows;
    }
}
