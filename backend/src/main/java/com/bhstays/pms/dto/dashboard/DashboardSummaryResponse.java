package com.bhstays.pms.dto.dashboard;

import com.bhstays.pms.dto.lead.LeadResponse;
import com.bhstays.pms.dto.reservation.ReservationResponse;
import java.math.BigDecimal;
import java.util.List;

/**
 * {@code totalRevenueByCurrency} is the official source: the booked value of
 * non-cancelled reservations, one entry per currency. The flat
 * {@code totalRevenue} / {@code currency} fields are deprecated: with exactly
 * one currency they mirror it, with several they are null - RON and EUR are
 * never added together and none is picked over another.
 */
public record DashboardSummaryResponse(
        long totalProperties,
        long totalReservations,
        /* Deprecated: total of the only currency, else null; use {@code totalRevenueByCurrency}. */
        @Deprecated BigDecimal totalRevenue,
        /* Deprecated: code of the only currency, else null. */
        @Deprecated String currency,
        long uncontactedLeads,
        List<ReservationResponse> upcomingReservations,
        List<LeadResponse> recentLeads,
        List<CurrencyAmountResponse> totalRevenueByCurrency
) {
}
