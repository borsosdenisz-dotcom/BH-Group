package com.bhstays.pms.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bhstays.pms.common.exception.BadRequestException;
import com.bhstays.pms.common.exception.ResourceNotFoundException;
import com.bhstays.pms.dto.report.CommissionSummaryCurrencyTotals;
import com.bhstays.pms.dto.report.CommissionSummaryResponse;
import com.bhstays.pms.dto.report.PropertyCommissionReportResponse;
import com.bhstays.pms.dto.report.UnconfiguredPropertyResponse;
import com.bhstays.pms.repository.FinancialEventRepository;
import com.bhstays.pms.repository.PropertyRepository;
import com.bhstays.pms.repository.projection.PropertyCommissionSettings;
import com.bhstays.pms.repository.projection.ReservationMoneyEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PropertyCommissionReportServiceTest {

    private static final LocalDate FROM = LocalDate.of(2030, 5, 1);
    private static final LocalDate TO = LocalDate.of(2030, 5, 31);
    private static final Instant MAY_10 = Instant.parse("2030-05-10T10:00:00Z");

    @Mock private PropertyRepository propertyRepository;
    @Mock private FinancialEventRepository financialEventRepository;

    private PropertyCommissionReportService service;

    private final PropertyCommissionSettings twenty =
            new PropertyCommissionSettings(UUID.randomUUID(), "Apartament 20%", new BigDecimal("20.00"));
    private final PropertyCommissionSettings ten =
            new PropertyCommissionSettings(UUID.randomUUID(), "Apartament 10%", new BigDecimal("10.00"));
    private final PropertyCommissionSettings unset =
            new PropertyCommissionSettings(UUID.randomUUID(), "Apartament fără comision", null);

    @BeforeEach
    void setUp() {
        service = new PropertyCommissionReportService(propertyRepository, financialEventRepository);
    }

    /** A capture (and optional refund) of a reservation priced and commissioned by its snapshots. */
    private static List<ReservationMoneyEvent> paid(PropertyCommissionSettings property, String currency, String total,
                                                    String accommodation, String percent, String captured,
                                                    String refunded) {
        UUID reservation = UUID.randomUUID();
        UUID payment = UUID.randomUUID();
        BigDecimal pct = percent != null ? new BigDecimal(percent) : null;
        ReservationMoneyEvent capture = new ReservationMoneyEvent(property.id(), reservation, payment, currency,
                currency, new BigDecimal(total), new BigDecimal(accommodation), pct,
                ReservationMoneyEvent.Kind.CAPTURE, new BigDecimal(captured), MAY_10);
        if (new BigDecimal(refunded).signum() == 0) {
            return List.of(capture);
        }
        return List.of(capture, new ReservationMoneyEvent(property.id(), reservation, payment, currency, currency,
                new BigDecimal(total), new BigDecimal(accommodation), pct, ReservationMoneyEvent.Kind.REFUND,
                new BigDecimal(refunded), MAY_10.plusSeconds(3600)));
    }

    @Test
    void summary_appliesEachReservationsSnapshotAndKeepsCurrenciesApart() {
        when(propertyRepository.findAllCommissionSettings()).thenReturn(List.of(twenty, ten, unset));
        List<ReservationMoneyEvent> events = new java.util.ArrayList<>();
        events.addAll(paid(twenty, "RON", "500.00", "400.00", "20", "500.00", "0"));       // BH 80, owner 420
        events.addAll(paid(ten, "RON", "1000.00", "800.00", "10", "1000.00", "500.00"));  // net 500, base 400, BH 40
        events.addAll(paid(ten, "EUR", "200.00", "150.00", "10", "200.00", "0"));         // BH 15, owner 185
        events.addAll(paid(unset, "RON", "300.00", "250.00", null, "300.00", "0"));       // no snapshot: unallocated
        when(financialEventRepository.findEvents(any(), any(), any())).thenReturn(events);

        CommissionSummaryResponse summary = service.summary(FROM, TO);

        assertThat(summary.totals()).extracting(CommissionSummaryCurrencyTotals::currency).containsExactly("EUR", "RON");
        CommissionSummaryCurrencyTotals eur = summary.totals().get(0);
        assertThat(eur.bhStaysRevenue()).isEqualByComparingTo("15.00");
        assertThat(eur.ownersAmount()).isEqualByComparingTo("185.00");

        CommissionSummaryCurrencyTotals ron = summary.totals().get(1);
        assertThat(ron.capturedTotal()).isEqualByComparingTo("1800.00");
        assertThat(ron.refundedTotal()).isEqualByComparingTo("500.00");
        assertThat(ron.propertiesNetRevenue()).isEqualByComparingTo("1300.00");
        assertThat(ron.bhStaysRevenue()).isEqualByComparingTo("120.00");
        assertThat(ron.ownersAmount()).isEqualByComparingTo("1180.00");
        assertThat(ron.includedPropertyCount()).isEqualTo(3);
        assertThat(ron.unallocatedNetRevenue()).isEqualByComparingTo("300.00");
        assertThat(ron.unallocatedReservationCount()).isEqualTo(1);
        assertThat(ron.bhStaysRevenue().add(ron.ownersAmount())).isEqualByComparingTo(ron.propertiesNetRevenue());

        assertThat(summary.unconfiguredProperties()).extracting(UnconfiguredPropertyResponse::propertyId)
                .containsExactly(unset.id());
    }

    @Test
    void periodIsPassedAsRomanianCalendarDays() {
        when(propertyRepository.findAllCommissionSettings()).thenReturn(List.of(twenty));
        when(financialEventRepository.findEvents(any(), any(), any())).thenReturn(List.of());

        service.summary(FROM, TO);

        verify(financialEventRepository).findEvents(eq(List.of(twenty.id())),
                eq(Instant.parse("2030-04-30T21:00:00Z")), eq(Instant.parse("2030-05-31T21:00:00Z")));
    }

    @Test
    void propertyReport_returnsOneLinePerCurrency_andTheCurrentSettingSeparately() {
        when(propertyRepository.findCommissionSettings(ten.id())).thenReturn(Optional.of(ten));
        List<ReservationMoneyEvent> events = new java.util.ArrayList<>();
        events.addAll(paid(ten, "RON", "1000.00", "800.00", "20", "1000.00", "0"));   // booked when it was 20%
        events.addAll(paid(ten, "EUR", "200.00", "150.00", "10", "200.00", "0"));
        when(financialEventRepository.findEvents(eq(List.of(ten.id())), any(), any())).thenReturn(events);

        PropertyCommissionReportResponse report = service.propertyReport(ten.id(), FROM, TO);

        assertThat(report.commissionConfigured()).isTrue();
        assertThat(report.commissionPercent()).isEqualByComparingTo("10.00");
        assertThat(report.currencies()).hasSize(2);
        assertThat(report.currencies().get(1).bhStaysRevenue()).isEqualByComparingTo("160.00");
        assertThat(report.currencies().get(1).commissionPercents()).containsExactly(new BigDecimal("20.00"));
    }

    @Test
    void propertyReport_unknownPropertyIsNotFound() {
        UUID missing = UUID.randomUUID();
        when(propertyRepository.findCommissionSettings(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.propertyReport(missing, FROM, TO))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void reversedPeriod_isRejected() {
        assertThatThrownBy(() -> service.summary(TO, FROM)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.propertyReport(ten.id(), TO, FROM)).isInstanceOf(BadRequestException.class);
        verifyNoInteractions(financialEventRepository);
    }
}
