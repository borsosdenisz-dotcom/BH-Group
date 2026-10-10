package com.bhstays.pms.repository.projection;

import java.math.BigDecimal;
import java.util.UUID;

/** An amount summed per property and currency - currencies are never added together. */
public record PropertyCurrencyAmount(UUID propertyId, String currency, BigDecimal amount) {
}
