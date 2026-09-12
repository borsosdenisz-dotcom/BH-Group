package com.bhgroup.pms.controller;

import com.bhgroup.pms.domain.PaymentProvider;
import com.bhgroup.pms.payment.StripeGateway;
import com.bhgroup.pms.service.StripeWebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Inbound payment gateway webhooks.
 *
 * <p>Stripe is the one provider wired in, and its endpoint only does
 * anything for a delivery whose {@code Stripe-Signature} header verifies
 * against {@code STRIPE_WEBHOOK_SECRET}: an unsigned or tampered payload is
 * rejected with 400 before it is parsed, recorded, or acted on. Every other
 * provider still answers 501 - the generic "accept any JSON posted here"
 * endpoint this class used to expose is deliberately gone.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/public/payments/webhook")
@RequiredArgsConstructor
@Tag(name = "Payment Webhooks", description = "Signature-verified inbound payment gateway events")
public class PaymentWebhookController {

    /** Absent unless Stripe is configured; the endpoint then answers 501 like any other provider. */
    private final Optional<StripeWebhookService> stripeWebhookService;

    @PostMapping("/stripe")
    @Operation(summary = "Receive a signature-verified Stripe webhook event")
    public ResponseEntity<Void> receiveStripe(
            @RequestHeader(value = "Stripe-Signature", required = false) String signature,
            @RequestBody String payload) {

        if (stripeWebhookService.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
        }

        try {
            stripeWebhookService.get().handle(payload, signature);
            return ResponseEntity.ok().build();
        } catch (StripeGateway.StripeSignatureException ex) {
            // Never 5xx here: a bad signature is the caller's problem, and a
            // 400 stops Stripe from retrying a delivery that can never verify.
            log.warn("Rejected Stripe webhook delivery: {}", ex.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/{provider}")
    @Operation(summary = "Not implemented - only Stripe webhooks are accepted")
    public ResponseEntity<Void> receive(@PathVariable PaymentProvider provider) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }
}
