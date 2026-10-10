package com.bhstays.pms.service;

import com.bhstays.pms.common.exception.BadRequestException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * A reporting period as an instant range [start, end). Dates are calendar
 * days in Romanian time, so "January" means from 1 January 00:00 to
 * 1 February 00:00 Europe/Bucharest, whatever the server's time zone. An
 * open bound is replaced by a far date, so nothing is ever bound as null.
 */
public record FinancialPeriod(Instant start, Instant end) {

    public static final ZoneId BUSINESS_ZONE = ZoneId.of("Europe/Bucharest");
    static final LocalDate OPEN_START = LocalDate.of(1900, 1, 1);
    static final LocalDate OPEN_END = LocalDate.of(8999, 12, 31);

    public static FinancialPeriod of(LocalDate from, LocalDate to) {
        validate(from, to);
        return new FinancialPeriod(
                startOf(from).atStartOfDay(BUSINESS_ZONE).toInstant(),
                endOf(to).plusDays(1).atStartOfDay(BUSINESS_ZONE).toInstant());
    }

    static void validate(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new BadRequestException("The start date must not be after the end date");
        }
    }

    /** For date columns (expenses), the inclusive first day. */
    static LocalDate startOf(LocalDate from) {
        return from != null ? from : OPEN_START;
    }

    /** For date columns (expenses), the inclusive last day. */
    static LocalDate endOf(LocalDate to) {
        return to != null ? to : OPEN_END;
    }
}
