package com.bhgroup.pms.dto.payment;

import java.math.BigDecimal;

/**
 * Where to send the guest to pay, plus the amount they are about to be
 * charged - both computed server-side.
 */
public record CheckoutSessionResponse(
        String checkoutUrl,
        BigDecimal amount,
        String currency
) {
}
