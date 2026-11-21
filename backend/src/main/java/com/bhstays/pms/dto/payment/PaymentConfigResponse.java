package com.bhstays.pms.dto.payment;

/**
 * Public payment capabilities. {@code publishableKey} is Stripe's public
 * identifier (safe in a browser); the secret and webhook keys never leave
 * the server.
 */
public record PaymentConfigResponse(
        boolean directBookingEnabled,
        boolean cardPaymentsEnabled,
        String publishableKey
) {
}
