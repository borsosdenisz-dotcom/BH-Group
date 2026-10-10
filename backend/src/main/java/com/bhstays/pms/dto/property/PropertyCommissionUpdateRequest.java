package com.bhstays.pms.dto.property;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import java.math.BigDecimal;

/** Null clears the commission back to "not configured". */
public record PropertyCommissionUpdateRequest(
        @DecimalMin(value = "0.00", message = "Commission must be between 0 and 100")
        @DecimalMax(value = "100.00", message = "Commission must be between 0 and 100")
        @Digits(integer = 3, fraction = 2, message = "Commission can have at most two decimals")
        BigDecimal commissionPercent
) {
}
