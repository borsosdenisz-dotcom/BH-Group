package com.bhstays.pms.dto.owner;

import com.bhstays.pms.dto.maintenance.MaintenanceTicketResponse;
import com.bhstays.pms.dto.reservation.ReservationResponse;
import java.math.BigDecimal;
import java.util.List;

/**
 * {@code revenueByCurrency} is the official source: the owner's figures,
 * one line per currency, on the same formula as the statements. The flat
 * amount fields are deprecated and kept for API compatibility: when the
 * owner has exactly one currency they mirror it (with its code in
 * {@code currency}); with several currencies they are null - currencies
 * are never added together and none is picked over another.
 */
public record OwnerDashboardSummaryResponse(
        int totalProperties,
        /* Deprecated: net collected revenue of the only currency, else null; use {@code revenueByCurrency}. */
        @Deprecated BigDecimal grossRevenue,
        /* Deprecated: BH Stays commission of the only currency, else null. */
        @Deprecated BigDecimal commissionAmount,
        /* Deprecated: owner-chargeable expenses of the only currency, else null. */
        @Deprecated BigDecimal expensesTotal,
        /* Deprecated: payout of the only currency, else null. */
        @Deprecated BigDecimal netRevenue,
        /* Deprecated: code of the only currency, else null. */
        @Deprecated String currency,
        List<ReservationResponse> upcomingReservations,
        List<MaintenanceTicketResponse> openMaintenanceTickets,
        List<OwnerRevenueLine> revenueByCurrency
) {
}
