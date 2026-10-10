package com.bhstays.pms.dto.dashboard;

import java.math.BigDecimal;

/** An amount in one currency - amounts in different currencies are never added together. */
public record CurrencyAmountResponse(String currency, BigDecimal amount) {
}
