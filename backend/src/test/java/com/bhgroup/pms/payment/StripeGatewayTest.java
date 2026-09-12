package com.bhgroup.pms.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bhgroup.pms.config.AppProperties;
import com.bhgroup.pms.domain.Payment;
import com.bhgroup.pms.domain.PaymentProvider;
import com.bhgroup.pms.domain.PaymentStatus;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Gateway behaviour that needs no network: currency handling, the
 * asynchronous nature of a Checkout charge, and the refusal to process a
 * webhook that cannot be verified. Session creation and refunds are covered
 * at the service level with the gateway mocked - exercising them here would
 * mean calling Stripe for real.
 */
class StripeGatewayTest {

    private StripeGateway stripeGateway;

    @BeforeEach
    void setUp() {
        AppProperties appProperties = new AppProperties();
        appProperties.getStripe().setSecretKey("sk_test_dummy");
        appProperties.getStripe().setPublishableKey("pk_test_dummy");
        appProperties.getStripe().setWebhookSecret("whsec_dummy");
        stripeGateway = new StripeGateway(appProperties);
    }

    @Test
    void getProvider_isStripe() {
        assertThat(stripeGateway.getProvider()).isEqualTo(PaymentProvider.STRIPE);
    }

    @Test
    void charge_reportsProcessing_becauseTheGuestPaysOnStripesOwnPage() {
        Payment payment = Payment.builder().providerPaymentId("pi_test_1").build();

        PaymentGatewayResult result = stripeGateway.charge(payment);

        assertThat(result.status()).isEqualTo(PaymentStatus.PROCESSING);
    }

    @Test
    void refund_failsCleanly_whenThePaymentHasNoStripeReference() {
        Payment payment = Payment.builder().amount(new BigDecimal("100.00")).build();
        payment.setId(java.util.UUID.randomUUID());

        PaymentGatewayResult result = stripeGateway.refund(payment, new BigDecimal("100.00"), "test");

        assertThat(result.status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(result.failureReason()).contains("PaymentIntent");
    }

    @Test
    void verifyAndParse_rejectsAMissingSignature() {
        assertThatThrownBy(() -> stripeGateway.verifyAndParse("{}", null))
                .isInstanceOf(StripeGateway.StripeSignatureException.class);
    }

    @Test
    void verifyAndParse_rejectsAForgedSignature() {
        assertThatThrownBy(() -> stripeGateway.verifyAndParse("{\"id\":\"evt_1\"}", "t=1,v1=forged"))
                .isInstanceOf(StripeGateway.StripeSignatureException.class);
    }

    @Test
    void verifyAndParse_rejectsEverything_whenNoWebhookSecretIsConfigured() {
        AppProperties withoutSecret = new AppProperties();
        withoutSecret.getStripe().setSecretKey("sk_test_dummy");
        StripeGateway gateway = new StripeGateway(withoutSecret);

        assertThatThrownBy(() -> gateway.verifyAndParse("{}", "t=1,v1=anything"))
                .isInstanceOf(StripeGateway.StripeSignatureException.class);
    }

    @Test
    void handleWebhookEvent_mapsStripeEventTypesToOutcomes() {
        assertThat(stripeGateway.handleWebhookEvent("checkout.session.completed", "{}").status())
                .isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(stripeGateway.handleWebhookEvent("payment_intent.succeeded", "{}").status())
                .isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(stripeGateway.handleWebhookEvent("payment_intent.payment_failed", "{}").status())
                .isEqualTo(PaymentStatus.FAILED);
        assertThat(stripeGateway.handleWebhookEvent("checkout.session.expired", "{}").status())
                .isEqualTo(PaymentStatus.CANCELLED);
        // anything else is recorded but changes no payment
        assertThat(stripeGateway.handleWebhookEvent("customer.created", "{}").status())
                .isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void reservationIdFromMetadata_readsTheKeyWeSetOnEverySession() {
        assertThat(StripeGateway.reservationIdFromMetadata(Map.of("reservationId", "abc"))).isEqualTo("abc");
        assertThat(StripeGateway.reservationIdFromMetadata(Map.of())).isNull();
        assertThat(StripeGateway.reservationIdFromMetadata(null)).isNull();
    }
}
