package com.bhgroup.pms.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.bhgroup.pms.domain.PaymentProvider;
import com.bhgroup.pms.payment.StripeGateway;
import com.bhgroup.pms.service.StripeWebhookService;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpStatus;

/**
 * Stripe is the only provider whose webhook does anything, and only for a
 * delivery it can verify: an unsigned or forged payload is turned away with
 * 400 before the service ever sees it. Every other provider still answers
 * 501 - the old "accept any JSON" endpoint stays gone.
 */
class PaymentWebhookControllerTest {

    private static final String PAYLOAD = "{\"id\":\"evt_1\",\"type\":\"checkout.session.completed\"}";

    @ParameterizedTest
    @EnumSource(PaymentProvider.class)
    void receive_isNotImplementedForEveryProviderOnTheGenericRoute(PaymentProvider provider) {
        PaymentWebhookController controller = new PaymentWebhookController(Optional.empty());

        var response = controller.receive(provider);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_IMPLEMENTED);
    }

    @Test
    void receiveStripe_isNotImplemented_whenStripeIsNotConfigured() {
        PaymentWebhookController controller = new PaymentWebhookController(Optional.empty());

        var response = controller.receiveStripe("t=1,v1=whatever", PAYLOAD);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_IMPLEMENTED);
    }

    @Test
    void receiveStripe_rejectsAnUnsignedPayloadWithoutProcessingIt() {
        StripeWebhookService service = mock(StripeWebhookService.class);
        doThrow(new StripeGateway.StripeSignatureException("Lipsește semnătura Stripe"))
                .when(service).handle(anyString(), org.mockito.ArgumentMatchers.isNull());

        var response = new PaymentWebhookController(Optional.of(service)).receiveStripe(null, PAYLOAD);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void receiveStripe_rejectsAForgedSignature() {
        StripeWebhookService service = mock(StripeWebhookService.class);
        doThrow(new StripeGateway.StripeSignatureException("Semnătură Stripe invalidă"))
                .when(service).handle(PAYLOAD, "t=1,v1=forged");

        var response = new PaymentWebhookController(Optional.of(service)).receiveStripe("t=1,v1=forged", PAYLOAD);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void receiveStripe_acceptsAVerifiedDelivery() {
        StripeWebhookService service = mock(StripeWebhookService.class);

        var response = new PaymentWebhookController(Optional.of(service)).receiveStripe("t=1,v1=valid", PAYLOAD);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(service).handle(PAYLOAD, "t=1,v1=valid");
    }

    @Test
    void receiveStripe_neverProcessesWhenStripeIsOff() {
        StripeWebhookService service = mock(StripeWebhookService.class);

        new PaymentWebhookController(Optional.empty()).receiveStripe("t=1,v1=valid", PAYLOAD);

        verify(service, never()).handle(anyString(), anyString());
    }
}
