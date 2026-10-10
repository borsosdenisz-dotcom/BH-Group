package com.bhstays.pms.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.bhstays.pms.domain.ReservationStatus;
import com.bhstays.pms.dto.dashboard.CurrencyAmountResponse;
import com.bhstays.pms.dto.dashboard.DashboardSummaryResponse;
import com.bhstays.pms.repository.PropertyLeadRepository;
import com.bhstays.pms.repository.PropertyRepository;
import com.bhstays.pms.repository.ReservationRepository;
import com.bhstays.pms.service.mapper.LeadMapper;
import com.bhstays.pms.service.mapper.ReservationMapper;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock private PropertyRepository propertyRepository;
    @Mock private ReservationRepository reservationRepository;
    @Mock private PropertyLeadRepository leadRepository;
    @Mock private ReservationMapper reservationMapper;
    @Mock private LeadMapper leadMapper;

    private DashboardService dashboardService;

    @BeforeEach
    void setUp() {
        dashboardService = new DashboardService(propertyRepository, reservationRepository, leadRepository,
                reservationMapper, leadMapper);
        when(reservationRepository.findUpcoming(any(), any(), any())).thenReturn(List.of());
        when(leadRepository.findAllByOrderByCreatedAtDesc(any())).thenReturn(Page.empty());
    }

    private void revenue(CurrencyAmountResponse... amounts) {
        when(reservationRepository.sumTotalRevenueByCurrency(ReservationStatus.NON_BLOCKING))
                .thenReturn(List.of(amounts));
    }

    @Test
    void ronAndEur_areListedSeparately_andTheDeprecatedFieldsPickNeither() {
        revenue(new CurrencyAmountResponse("EUR", new BigDecimal("300.00")),
                new CurrencyAmountResponse("RON", new BigDecimal("1500.00")));

        DashboardSummaryResponse summary = dashboardService.getSummary();

        assertThat(summary.totalRevenueByCurrency()).containsExactly(
                new CurrencyAmountResponse("EUR", new BigDecimal("300.00")),
                new CurrencyAmountResponse("RON", new BigDecimal("1500.00")));
        // never 1800 (a mixed total) and never the RON amount picked by default
        assertThat(summary.totalRevenue()).isNull();
        assertThat(summary.currency()).isNull();
    }

    @Test
    void singleCurrency_isMirroredInTheDeprecatedFieldsWithItsOwnCode() {
        revenue(new CurrencyAmountResponse("EUR", new BigDecimal("300.00")));

        DashboardSummaryResponse summary = dashboardService.getSummary();

        assertThat(summary.totalRevenue()).isEqualByComparingTo("300.00");
        assertThat(summary.currency()).isEqualTo("EUR");
    }

    @Test
    void noReservations_isZeroWithoutAnAssumedCurrency() {
        revenue();

        DashboardSummaryResponse summary = dashboardService.getSummary();

        assertThat(summary.totalRevenueByCurrency()).isEmpty();
        assertThat(summary.totalRevenue()).isEqualByComparingTo("0");
        assertThat(summary.currency()).isNull();
    }
}
