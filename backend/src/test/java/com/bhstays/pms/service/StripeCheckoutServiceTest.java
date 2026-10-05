package com.bhstays.pms.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bhstays.pms.common.exception.BadRequestException;
import com.bhstays.pms.config.AppProperties;
import com.bhstays.pms.domain.Payment;
import com.bhstays.pms.domain.PaymentProvider;
import com.bhstays.pms.domain.Property;
import com.bhstays.pms.domain.Reservation;
import com.bhstays.pms.domain.ReservationStatus;
import com.bhstays.pms.dto.property.PriceQuoteResponse;
import com.bhstays.pms.payment.StripeGateway;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The money-critical guarantee here: the amount sent to Stripe comes from
 * the server-side quote, never from the caller. The endpoint takes nothing
 * but a management token, so these tests pin the recomputation itself.
 */
@ExtendWith(MockitoExtension.class)
class StripeCheckoutServiceTest {

    @Mock
    private ReservationService reservationService;
    @Mock
    private PricingService pricingService;
    @Mock
    private PaymentService paymentService;
    @Mock
    private StripeGateway stripeGateway;

    private StripeCheckoutService stripeCheckoutService;
    private Reservation reservation;
    private static final String TOKEN = "manage-token-123";

    @BeforeEach
    void setUp() {
        AppProperties appProperties = new AppProperties();
        appProperties.setBaseUrl("https://bhstays.test");

        stripeCheckoutService = new StripeCheckoutService(
                reservationService, pricingService, paymentService, stripeGateway, appProperties);

        Property property = Property.builder().name("Casa Mare").build();
        property.setId(UUID.randomUUID());

        reservation = Reservation.builder()
                .property(property)
                .guestFirstName("Ion")
                .guestEmail("ion@example.com")
                .checkInDate(LocalDate.of(2026, 7, 1))
                .checkOutDate(LocalDate.of(2026, 7, 5))
                .numberOfGuests(2)
                .status(ReservationStatus.PENDING)
                .totalAmount(new BigDecimal("500.00"))
                .currency("RON")
                .managementToken(TOKEN)
                .holdExpiresAt(Instant.now().plus(10, ChronoUnit.MINUTES))
                .build();
        reservation.setId(UUID.randomUUID());
    }

    private PriceQuoteResponse quoteOf(BigDecimal total, String currency) {
        return new PriceQuoteResponse(true, null, reservation.getCheckInDate(), reservation.getCheckOutDate(),
                4, total, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                total, currency, null, null);
    }

    private Payment cardPaymentOf(BigDecimal amount, String currency) {
        Payment payment = Payment.builder()
                .reservation(reservation)
                .provider(PaymentProvider.STRIPE)
                .amount(amount)
                .currency(currency)
                .build();
        payment.setId(UUID.randomUUID());
        return payment;
    }

    @Test
    void createCheckoutSession_chargesTheServerRecomputedQuote_notTheStoredTotal() {
        // The stored total is stale/tampered (1 RON); the quote is authoritative.
        reservation.setTotalAmount(new BigDecimal("1.00"));
        BigDecimal quotedTotal = new BigDecimal("500.00");

        when(reservationService.lockByManagementToken(TOKEN)).thenReturn(reservation);
        when(pricingService.quote(eq(reservation.getProperty()), any(), any(), anyInt()))
                .thenReturn(quoteOf(quotedTotal, "RON"));
        when(paymentService.startOnlineCardPayment(any(), any(), any()))
                .thenReturn(cardPaymentOf(quotedTotal, "RON"));
        when(stripeGateway.createCheckoutSession(any()))
                .thenReturn(new StripeGateway.StripeCheckoutSession("cs_test_1", "https://checkout.stripe.com/cs_test_1"));

        var response = stripeCheckoutService.createCheckoutSession(TOKEN);

        ArgumentCaptor<StripeGateway.CheckoutSessionRequest> captor =
                ArgumentCaptor.forClass(StripeGateway.CheckoutSessionRequest.class);
        verify(stripeGateway).createCheckoutSession(captor.capture());

        assertThat(captor.getValue().amount()).isEqualByComparingTo("500.00");
        assertThat(captor.getValue().currency()).isEqualTo("RON");
        assertThat(captor.getValue().reservationId()).isEqualTo(reservation.getId().toString());
        assertThat(response.amount()).isEqualByComparingTo("500.00");
        assertThat(response.checkoutUrl()).isEqualTo("https://checkout.stripe.com/cs_test_1");
        // the stale stored total is realigned to the recomputed one
        assertThat(reservation.getTotalAmount()).isEqualByComparingTo("500.00");
    }

    @Test
    void createCheckoutSession_passesTheReservationIdAsMetadataAndBothRedirectUrls() {
        when(reservationService.lockByManagementToken(TOKEN)).thenReturn(reservation);
        when(pricingService.quote(any(), any(), any(), anyInt()))
                .thenReturn(quoteOf(new BigDecimal("500.00"), "RON"));
        when(paymentService.startOnlineCardPayment(any(), any(), any()))
                .thenReturn(cardPaymentOf(new BigDecimal("500.00"), "RON"));
        when(stripeGateway.createCheckoutSession(any()))
                .thenReturn(new StripeGateway.StripeCheckoutSession("cs_test_1", "https://checkout.stripe.com/cs_test_1"));

        stripeCheckoutService.createCheckoutSession(TOKEN);

        ArgumentCaptor<StripeGateway.CheckoutSessionRequest> captor =
                ArgumentCaptor.forClass(StripeGateway.CheckoutSessionRequest.class);
        verify(stripeGateway).createCheckoutSession(captor.capture());

        assertThat(captor.getValue().successUrl()).isEqualTo("https://bhstays.test/plata/succes?token=" + TOKEN);
        assertThat(captor.getValue().cancelUrl()).isEqualTo("https://bhstays.test/plata/anulat?token=" + TOKEN);
        assertThat(captor.getValue().idempotencyKey()).isNotBlank();
    }

    @Test
    void createCheckoutSession_rejectsAnAlreadyConfirmedBooking() {
        reservation.setStatus(ReservationStatus.CONFIRMED);
        when(reservationService.lockByManagementToken(TOKEN)).thenReturn(reservation);

        assertThatThrownBy(() -> stripeCheckoutService.createCheckoutSession(TOKEN))
                .isInstanceOf(BadRequestException.class);

        verify(stripeGateway, never()).createCheckoutSession(any());
    }

    @Test
    void createCheckoutSession_rejectsABookingWhoseHoldHasExpired() {
        reservation.setHoldExpiresAt(Instant.now().minus(1, ChronoUnit.MINUTES));
        when(reservationService.lockByManagementToken(TOKEN)).thenReturn(reservation);

        assertThatThrownBy(() -> stripeCheckoutService.createCheckoutSession(TOKEN))
                .isInstanceOf(BadRequestException.class);

        verify(stripeGateway, never()).createCheckoutSession(any());
    }

    @Test
    void createCheckoutSession_rejectsWhenTheQuoteCannotBePriced() {
        when(reservationService.lockByManagementToken(TOKEN)).thenReturn(reservation);
        when(pricingService.quote(any(), any(), any(), anyInt())).thenReturn(new PriceQuoteResponse(
                false, "No price configured", reservation.getCheckInDate(), reservation.getCheckOutDate(),
                4, null, null, null, null, null, null, "RON", null, null));

        assertThatThrownBy(() -> stripeCheckoutService.createCheckoutSession(TOKEN))
                .isInstanceOf(BadRequestException.class);

        verify(stripeGateway, never()).createCheckoutSession(any());
    }

    @Test
    void createCheckoutSession_handsBackTheOpenSession_onARepeatedClick_withoutOpeningASecondOne() {
        Payment open = cardPaymentOf(new BigDecimal("500.00"), "RON");
        open.setCheckoutSessionId("cs_open");
        open.setCheckoutUrl("https://checkout.stripe.com/cs_open");
        open.setCheckoutExpiresAt(Instant.now().plus(20, ChronoUnit.MINUTES));

        when(reservationService.lockByManagementToken(TOKEN)).thenReturn(reservation);
        when(pricingService.quote(any(), any(), any(), anyInt()))
                .thenReturn(quoteOf(new BigDecimal("500.00"), "RON"));
        when(paymentService.findOpenCardCheckout(eq(reservation.getId()), any())).thenReturn(Optional.of(open));

        var response = stripeCheckoutService.createCheckoutSession(TOKEN);

        assertThat(response.checkoutUrl()).isEqualTo("https://checkout.stripe.com/cs_open");
        verify(stripeGateway, never()).createCheckoutSession(any());
        verify(paymentService, never()).startOnlineCardPayment(any(), any(), any());
    }

    @Test
    void createCheckoutSession_expiresAnAlmostLapsedSessionBeforeOpeningAReplacement() {
        Payment lapsing = cardPaymentOf(new BigDecimal("500.00"), "RON");
        lapsing.setCheckoutSessionId("cs_lapsing");
        lapsing.setCheckoutUrl("https://checkout.stripe.com/cs_lapsing");
        lapsing.setCheckoutExpiresAt(Instant.now().plus(1, ChronoUnit.MINUTES));

        when(reservationService.lockByManagementToken(TOKEN)).thenReturn(reservation);
        when(pricingService.quote(any(), any(), any(), anyInt()))
                .thenReturn(quoteOf(new BigDecimal("500.00"), "RON"));
        when(paymentService.findOpenCardCheckout(eq(reservation.getId()), any())).thenReturn(Optional.of(lapsing));
        when(paymentService.startOnlineCardPayment(any(), any(), any())).thenReturn(lapsing);
        when(stripeGateway.createCheckoutSession(any()))
                .thenReturn(new StripeGateway.StripeCheckoutSession("cs_new", "https://checkout.stripe.com/cs_new"));

        var response = stripeCheckoutService.createCheckoutSession(TOKEN);

        verify(stripeGateway, times(1)).expireCheckoutSession("cs_lapsing");
        assertThat(response.checkoutUrl()).isEqualTo("https://checkout.stripe.com/cs_new");
        assertThat(lapsing.getCheckoutSessionId()).isEqualTo("cs_new");
    }

    @Test
    void createCheckoutSession_keepsTheHoldInStepWithTheSessionExpiry() {
        Payment payment = cardPaymentOf(new BigDecimal("500.00"), "RON");
        when(reservationService.lockByManagementToken(TOKEN)).thenReturn(reservation);
        when(pricingService.quote(any(), any(), any(), anyInt()))
                .thenReturn(quoteOf(new BigDecimal("500.00"), "RON"));
        when(paymentService.startOnlineCardPayment(any(), any(), any())).thenReturn(payment);
        when(stripeGateway.createCheckoutSession(any()))
                .thenReturn(new StripeGateway.StripeCheckoutSession("cs_test_1", "https://checkout.stripe.com/cs_test_1"));

        stripeCheckoutService.createCheckoutSession(TOKEN);

        ArgumentCaptor<StripeGateway.CheckoutSessionRequest> captor =
                ArgumentCaptor.forClass(StripeGateway.CheckoutSessionRequest.class);
        verify(stripeGateway).createCheckoutSession(captor.capture());
        Instant sessionExpiry = Instant.ofEpochSecond(captor.getValue().expiresAt());

        assertThat(payment.getCheckoutSessionId()).isEqualTo("cs_test_1");
        assertThat(payment.getCheckoutExpiresAt()).isEqualTo(sessionExpiry);
        assertThat(reservation.getHoldExpiresAt())
                .isEqualTo(sessionExpiry.plus(StripeCheckoutService.HOLD_GRACE_AFTER_SESSION));
    }

    @Test
    void createCheckoutSession_refusesToExtendAHoldBeyondTheMaximum() {
        reservation.setCreatedAt(Instant.now().minus(StripeCheckoutService.MAX_HOLD).plus(10, ChronoUnit.MINUTES));
        when(reservationService.lockByManagementToken(TOKEN)).thenReturn(reservation);
        when(pricingService.quote(any(), any(), any(), anyInt()))
                .thenReturn(quoteOf(new BigDecimal("500.00"), "RON"));

        assertThatThrownBy(() -> stripeCheckoutService.createCheckoutSession(TOKEN))
                .isInstanceOf(BadRequestException.class);

        verify(stripeGateway, never()).createCheckoutSession(any());
    }
}
