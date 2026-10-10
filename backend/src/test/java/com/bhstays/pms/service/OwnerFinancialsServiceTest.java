package com.bhstays.pms.service;

import static com.bhstays.pms.service.TestMoneyEvents.AT;
import static com.bhstays.pms.service.TestMoneyEvents.all;
import static com.bhstays.pms.service.TestMoneyEvents.paid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.bhstays.pms.dto.owner.OwnerRevenueLine;
import com.bhstays.pms.repository.ExpenseRepository;
import com.bhstays.pms.repository.FinancialEventRepository;
import com.bhstays.pms.repository.PropertyRepository;
import com.bhstays.pms.repository.projection.PropertyCommissionSettings;
import com.bhstays.pms.repository.projection.PropertyCurrencyAmount;
import com.bhstays.pms.repository.projection.ReservationMoneyEvent;
import com.bhstays.pms.service.mapper.OwnerMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Runs on the real shared calculation; only the repositories are mocked. */
@ExtendWith(MockitoExtension.class)
class OwnerFinancialsServiceTest {

    @Mock private PropertyRepository propertyRepository;
    @Mock private FinancialEventRepository financialEventRepository;
    @Mock private ExpenseRepository expenseRepository;

    private OwnerFinancialsService ownerFinancialsService;
    private final UUID ownerId = UUID.randomUUID();
    private final PropertyCommissionSettings casaMare = new PropertyCommissionSettings(UUID.randomUUID(),
            "Casa Mare", new BigDecimal("20.00"), ownerId, "Maria", "Ionescu");
    private final PropertyCommissionSettings unset = new PropertyCommissionSettings(UUID.randomUUID(),
            "Casa Mică", null, ownerId, "Maria", "Ionescu");

    @BeforeEach
    void setUp() {
        ownerFinancialsService = new OwnerFinancialsService(propertyRepository, expenseRepository,
                new PropertyCommissionReportService(propertyRepository, financialEventRepository));
    }

    @Test
    void payoutIsOwnerAmountMinusOwnerChargeableExpenses_commissionOnAccommodationOnly() {
        when(propertyRepository.findCommissionSettingsByOwnerId(ownerId)).thenReturn(List.of(casaMare));
        // 1000 = 800 accommodation + 200 cleaning/extra guest; 20% of 800 = 160 (not 200 on the gross)
        when(financialEventRepository.findEvents(eq(List.of(casaMare.id())), any(), any()))
                .thenReturn(paid(casaMare, "RON", "1000.00", "800.00", "20", "1000.00", "0"));
        when(expenseRepository.sumChargeableToOwnerGroupedByPropertyAndCurrency(eq(ownerId), any(), any()))
                .thenReturn(List.of(new PropertyCurrencyAmount(casaMare.id(), "RON", new BigDecimal("100.00"))));

        var rows = ownerFinancialsService.computeForOwner(ownerId, null, null);

        assertThat(rows).hasSize(1);
        var row = rows.get(0);
        assertThat(row.netRevenue()).isEqualByComparingTo("1000.00");
        assertThat(row.commissionableBase()).isEqualByComparingTo("800.00");
        assertThat(row.bhStaysCommission()).isEqualByComparingTo("160.00");
        assertThat(row.ownerAmount()).isEqualByComparingTo("840.00");
        assertThat(row.expensesTotal()).isEqualByComparingTo("100.00");
        assertThat(row.netPayout()).isEqualByComparingTo("740.00");
        assertThat(row.singleCommissionPercent()).isEqualByComparingTo("20.00");
    }

    @Test
    void propertiesWithoutActivityAreLeftOut_andCurrenciesStaySeparate() {
        PropertyCommissionSettings idle = new PropertyCommissionSettings(UUID.randomUUID(), "Fără activitate",
                new BigDecimal("10.00"), ownerId, "Maria", "Ionescu");
        when(propertyRepository.findCommissionSettingsByOwnerId(ownerId)).thenReturn(List.of(casaMare, idle));
        when(financialEventRepository.findEvents(any(), any(), any())).thenReturn(all(
                paid(casaMare, "RON", "500.00", "400.00", "20", "500.00", "0"),
                paid(casaMare, "EUR", "100.00", "100.00", "20", "100.00", "0")));
        when(expenseRepository.sumChargeableToOwnerGroupedByPropertyAndCurrency(any(), any(), any())).thenReturn(List.of());

        var rows = ownerFinancialsService.computeForOwner(ownerId, null, null);

        assertThat(rows).extracting(OwnerFinancialsService.PropertyFinancials::currency).containsExactly("EUR", "RON");
        assertThat(rows).extracting(OwnerFinancialsService.PropertyFinancials::propertyId).containsOnly(casaMare.id());
    }

    @Test
    void aggregate_addsUpTheRowsLikeAStatement_withoutCommissioningUnverifiableMoney() {
        when(propertyRepository.findCommissionSettingsByOwnerId(ownerId)).thenReturn(List.of(casaMare, unset));
        when(financialEventRepository.findEvents(any(), any(), any())).thenReturn(all(
                paid(casaMare, "RON", "500.00", "400.00", "20", "500.00", "0"),
                paid(unset, "RON", "300.00", "300.00", null, "300.00", "0")));
        when(expenseRepository.sumChargeableToOwnerGroupedByPropertyAndCurrency(any(), any(), any())).thenReturn(List.of(
                new PropertyCurrencyAmount(unset.id(), "RON", new BigDecimal("30.00"))));

        OwnerRevenueLine ron = OwnerFinancialsService.aggregate("RON",
                ownerFinancialsService.computeForOwner(ownerId, null, null));

        assertThat(ron.netRevenue()).isEqualByComparingTo("800.00");
        assertThat(ron.bhStaysCommission()).isEqualByComparingTo("80.00");
        assertThat(ron.ownerAmount()).isEqualByComparingTo("720.00");
        assertThat(ron.expensesTotal()).isEqualByComparingTo("30.00");
        assertThat(ron.netPayout()).isEqualByComparingTo("690.00");
        assertThat(ron.unallocatedNetRevenue()).isEqualByComparingTo("300.00");
        assertThat(ron.unallocatedReservationCount()).isEqualTo(1);
        assertThat(ron.commissionPercents()).containsExactly(new BigDecimal("20.00"));
    }

    @Test
    void refundOfAnEarlierCaptureIsANegativeAdjustmentOfItsOwnPeriod() {
        when(propertyRepository.findCommissionSettingsByOwnerId(ownerId)).thenReturn(List.of(casaMare));
        UUID reservation = UUID.randomUUID();
        Instant earlier = Instant.parse("2020-01-10T10:00:00Z");
        when(financialEventRepository.findEvents(any(), any(), any())).thenReturn(List.of(
                new ReservationMoneyEvent(casaMare.id(), reservation, UUID.randomUUID(), "RON", "RON",
                        new BigDecimal("500.00"), new BigDecimal("400.00"), new BigDecimal("20.00"),
                        ReservationMoneyEvent.Kind.CAPTURE, new BigDecimal("500.00"), earlier),
                new ReservationMoneyEvent(casaMare.id(), reservation, UUID.randomUUID(), "RON", "RON",
                        new BigDecimal("500.00"), new BigDecimal("400.00"), new BigDecimal("20.00"),
                        ReservationMoneyEvent.Kind.REFUND, new BigDecimal("100.00"), AT)));
        when(expenseRepository.sumChargeableToOwnerGroupedByPropertyAndCurrency(any(), any(), any())).thenReturn(List.of());

        var rows = ownerFinancialsService.computeForOwner(ownerId, LocalDate.of(2030, 5, 1), LocalDate.of(2030, 5, 31));

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).capturedTotal()).isEqualByComparingTo("0.00");
        assertThat(rows.get(0).netRevenue()).isEqualByComparingTo("-100.00");
        assertThat(rows.get(0).bhStaysCommission()).isEqualByComparingTo("-16.00");
        assertThat(rows.get(0).netPayout()).isEqualByComparingTo("-84.00");
    }

    @Test
    void deprecatedSingleCurrencyFields_mirrorTheOnlyCurrency_andAreNullWithSeveral() {
        OwnerRevenueLine eur = new OwnerRevenueLine("EUR", BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.TEN,
                BigDecimal.TEN, List.of(), BigDecimal.ONE, new BigDecimal("9"), new BigDecimal("9"),
                BigDecimal.ZERO, BigDecimal.ZERO, 0);
        OwnerRevenueLine ron = new OwnerRevenueLine("RON", new BigDecimal("500"), BigDecimal.ZERO,
                new BigDecimal("500"), new BigDecimal("400"), List.of(), new BigDecimal("80"), new BigDecimal("420"),
                new BigDecimal("420"), BigDecimal.ZERO, BigDecimal.ZERO, 0);

        OwnerMapper.LegacyFields onlyEur = OwnerMapper.LegacyFields.of(List.of(eur));
        assertThat(onlyEur.currency()).isEqualTo("EUR");
        assertThat(onlyEur.pick(OwnerRevenueLine::ownerAmount)).isEqualByComparingTo("9");

        OwnerMapper.LegacyFields both = OwnerMapper.LegacyFields.of(List.of(eur, ron));
        assertThat(both.currency()).isNull();
        assertThat(both.pick(OwnerRevenueLine::ownerAmount)).isNull();
        assertThat(both.pick(OwnerRevenueLine::netRevenue)).isNull();

        OwnerMapper.LegacyFields none = OwnerMapper.LegacyFields.of(List.of());
        assertThat(none.currency()).isNull();
        assertThat(none.pick(OwnerRevenueLine::netRevenue)).isEqualByComparingTo("0");
    }

    @Test
    void ownerWithoutPropertiesHasNoRows() {
        when(propertyRepository.findCommissionSettingsByOwnerId(ownerId)).thenReturn(List.of());

        assertThat(ownerFinancialsService.computeForOwner(ownerId, null, null)).isEmpty();
    }
}
