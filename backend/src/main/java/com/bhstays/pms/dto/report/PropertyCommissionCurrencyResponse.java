package com.bhstays.pms.dto.report;

import java.math.BigDecimal;
import java.util.List;

/**
 * One property's money movements in one currency within a period, dated
 * by the transactions: captures by capture date, refunds by refund date.
 * A refund in a later period shows there as a negative adjustment of the
 * net revenue, the base and the commission - earlier periods never change.
 *
 * <p>The commission of each reservation uses the percent snapshotted on
 * the reservation when it was created ({@code commissionPercents} lists the
 * distinct ones involved). Reservations without a verifiable price
 * breakdown or percent snapshot (historical bookings) stay in the net
 * revenue, are never commissioned with a guessed value, and are reported in
 * {@code unallocatedNetRevenue} / {@code unallocatedReservationCount}.
 *
 * <p>Always {@code ownerAmount = netRevenue - bhStaysRevenue}. This is the
 * one line every financial view is built from.
 */
public record PropertyCommissionCurrencyResponse(
        String currency,
        BigDecimal capturedTotal,
        BigDecimal refundedTotal,
        BigDecimal netRevenue,
        BigDecimal commissionableBase,
        List<BigDecimal> commissionPercents,
        BigDecimal bhStaysRevenue,
        BigDecimal ownerAmount,
        BigDecimal unallocatedNetRevenue,
        int unallocatedReservationCount,
        int reservationCount
) {
}
