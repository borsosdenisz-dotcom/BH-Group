package com.bhstays.pms.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bhstays.pms.common.exception.ApiException;
import com.bhstays.pms.common.exception.BadRequestException;
import com.bhstays.pms.common.exception.ServiceUnavailableException;
import com.bhstays.pms.domain.Reservation;
import com.bhstays.pms.dto.payment.CheckoutSessionResponse;
import com.bhstays.pms.dto.publicapi.PublicBookingRequest;
import com.bhstays.pms.dto.publicapi.PublicReservationResponse;
import com.bhstays.pms.payment.StripeGateway;
import com.bhstays.pms.repository.PropertyRepository;
import com.bhstays.pms.service.mapper.PublicReservationMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

/**
 * Public booking is card-only, enforced here rather than only in the UI:
 * no Stripe means no public hold at all, a manual payment method is refused
 * whatever the request says, and the hold and its Checkout session are
 * opened together.
 */
@ExtendWith(MockitoExtension.class)
class PublicReservationServiceTest {

    @Mock
    private ReservationService reservationService;
    @Mock
    private PublicReservationMapper publicReservationMapper;
    @Mock
    private PricingService pricingService;
    @Mock
    private PropertyRepository propertyRepository;
    @Mock
    private MessageService messageService;
    @Mock
    private StripeCheckoutService stripeCheckoutService;

    private Reservation reservation;

    @BeforeEach
    void setUp() {
        reservation = Reservation.builder().managementToken("tok-1").build();
        reservation.setId(UUID.randomUUID());
    }

    private PublicReservationService serviceWithStripe(boolean stripeConfigured) {
        return new PublicReservationService(reservationService, publicReservationMapper, pricingService,
                propertyRepository, messageService,
                stripeConfigured ? Optional.of(stripeCheckoutService) : Optional.empty());
    }

    private static PublicBookingRequest request(String paymentMethod) {
        return new PublicBookingRequest(UUID.randomUUID(), "Ana", "Popescu", "ana@example.com", "0700000000",
                LocalDate.now().plusDays(30), LocalDate.now().plusDays(34), 2, null, "idem-1", paymentMethod);
    }

    @Test
    void createBooking_withStripeActive_holdsTheDatesAndReturnsTheCheckoutUrl() {
        when(reservationService.createGuestBooking(any(), anyString(), anyString(), anyString(), anyString(),
                any(), any(), anyInt(), any(), any())).thenReturn(reservation);
        when(stripeCheckoutService.createCheckoutSession("tok-1")).thenReturn(
                new CheckoutSessionResponse("https://checkout.stripe.com/c/pay/cs_1", new BigDecimal("500.00"), "RON"));
        when(publicReservationMapper.toResponse(reservation)).thenReturn(mock(PublicReservationResponse.class));

        var response = serviceWithStripe(true).createBooking(request("ONLINE_CARD"));

        assertThat(response.checkoutUrl()).isEqualTo("https://checkout.stripe.com/c/pay/cs_1");
        assertThat(response.amount()).isEqualByComparingTo("500.00");
    }

    @Test
    void createBooking_treatsAnOmittedPaymentMethodAsCard() {
        when(reservationService.createGuestBooking(any(), anyString(), anyString(), anyString(), anyString(),
                any(), any(), anyInt(), any(), any())).thenReturn(reservation);
        when(stripeCheckoutService.createCheckoutSession("tok-1")).thenReturn(
                new CheckoutSessionResponse("https://checkout.stripe.com/c/pay/cs_1", new BigDecimal("500.00"), "RON"));

        serviceWithStripe(true).createBooking(request(null));

        verify(stripeCheckoutService).createCheckoutSession("tok-1");
    }

    @ParameterizedTest
    @ValueSource(strings = {"BANK_TRANSFER", "ON_ARRIVAL", "CASH", "CARD_TERMINAL", "OTHER", "online_card", ""})
    void createBooking_rejectsAnyPublicPaymentMethodOtherThanCard_beforeHoldingAnything(String method) {
        assertThatThrownBy(() -> serviceWithStripe(true).createBooking(request(method)))
                .isInstanceOf(BadRequestException.class);

        verifyNoInteractions(reservationService, stripeCheckoutService);
    }

    @Test
    void createBooking_withoutStripe_createsNoHoldAndNoReservation() {
        assertThatThrownBy(() -> serviceWithStripe(false).createBooking(request("ONLINE_CARD")))
                .isInstanceOf(ServiceUnavailableException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));

        verifyNoInteractions(reservationService);
    }

    @Test
    void createBooking_whenStripeCannotOpenTheSession_failsSoTheHoldIsRolledBack() {
        when(reservationService.createGuestBooking(any(), anyString(), anyString(), anyString(), anyString(),
                any(), any(), anyInt(), any(), any())).thenReturn(reservation);
        when(stripeCheckoutService.createCheckoutSession("tok-1"))
                .thenThrow(new StripeGateway.StripePaymentException("Stripe down", null));

        // A runtime exception out of the @Transactional method rolls the hold back.
        assertThatThrownBy(() -> serviceWithStripe(true).createBooking(request("ONLINE_CARD")))
                .isInstanceOf(ServiceUnavailableException.class);

        verify(publicReservationMapper, never()).toResponse(any());
    }

    @Test
    void createCheckoutSession_withoutStripe_isUnavailable() {
        assertThatThrownBy(() -> serviceWithStripe(false).createCheckoutSession("tok-1"))
                .isInstanceOf(ServiceUnavailableException.class);
    }
}
