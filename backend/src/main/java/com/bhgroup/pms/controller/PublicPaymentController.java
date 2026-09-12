package com.bhgroup.pms.controller;

import com.bhgroup.pms.common.response.ApiResponse;
import com.bhgroup.pms.dto.payment.PaymentConfigResponse;
import com.bhgroup.pms.payment.StripeGateway;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lets the public booking site know which payment options to offer. The
 * publishable key is safe to hand out - that is what it exists for - and no
 * secret or webhook key is ever exposed here.
 */
@RestController
@RequestMapping("/api/v1/public/payments")
@RequiredArgsConstructor
@Tag(name = "Public Booking Engine", description = "Unauthenticated property search and booking")
public class PublicPaymentController {

    /** Absent when no Stripe key is configured - card payments are then reported as off. */
    private final Optional<StripeGateway> stripeGateway;

    @GetMapping("/config")
    @Operation(summary = "Which payment methods the booking site can offer")
    public ResponseEntity<ApiResponse<PaymentConfigResponse>> config() {
        return ResponseEntity.ok(ApiResponse.success(stripeGateway
                .map(gateway -> new PaymentConfigResponse(true, gateway.publishableKey()))
                .orElseGet(() -> new PaymentConfigResponse(false, null))));
    }
}
