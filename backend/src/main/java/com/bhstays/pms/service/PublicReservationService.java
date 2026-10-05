package com.bhstays.pms.service;

import com.bhstays.pms.common.exception.BadRequestException;
import com.bhstays.pms.common.exception.ResourceNotFoundException;
import com.bhstays.pms.common.exception.ServiceUnavailableException;
import com.bhstays.pms.domain.Property;
import com.bhstays.pms.dto.messaging.MessageResponse;
import com.bhstays.pms.dto.payment.CheckoutSessionResponse;
import com.bhstays.pms.dto.property.PriceQuoteResponse;
import com.bhstays.pms.dto.publicapi.PublicBookingCheckoutResponse;
import com.bhstays.pms.dto.publicapi.PublicBookingRequest;
import com.bhstays.pms.dto.publicapi.PublicBookingUpdateRequest;
import com.bhstays.pms.dto.publicapi.PublicReservationResponse;
import com.bhstays.pms.domain.Reservation;
import com.bhstays.pms.dto.reservation.AvailabilityResponse;
import com.bhstays.pms.payment.StripeGateway;
import com.bhstays.pms.repository.PropertyRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bhstays.pms.service.mapper.PublicReservationMapper;
@Service
@RequiredArgsConstructor
public class PublicReservationService {

    private final ReservationService reservationService;
    private final PublicReservationMapper publicReservationMapper;
    private final PricingService pricingService;
    private final PropertyRepository propertyRepository;
    private final MessageService messageService;
    /**
     * Absent when no Stripe key is configured. Public booking is card-only,
     * so without it the site takes no public bookings at all - no hold, no
     * reservation - and says so plainly.
     */
    private final Optional<StripeCheckoutService> stripeCheckoutService;

    static final String ONLINE_BOOKING_UNAVAILABLE =
            "Rezervarea online nu este momentan disponibilă. Te rugăm să ne contactezi direct.";

    @Transactional(readOnly = true)
    public AvailabilityResponse availability(UUID propertyId, LocalDate checkIn, LocalDate checkOut) {
        return reservationService.availability(propertyId, checkIn, checkOut);
    }

    @Transactional(readOnly = true)
    public PriceQuoteResponse quote(UUID propertyId, LocalDate checkIn, LocalDate checkOut, int guests) {
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new ResourceNotFoundException("Property not found"));
        pricingService.validateGuestCount(property, guests);
        return pricingService.quote(property, checkIn, checkOut, guests);
    }

    /**
     * Holds the dates and opens the hosted Stripe Checkout session in one
     * transaction. Public booking is card-only:
     * <ul>
     *   <li>no Stripe configured: refused before anything is written;
     *   <li>any payment method other than ONLINE_CARD: refused, whatever the
     *       client sends - manual methods exist only in the admin flows;
     *   <li>Stripe failing to open the session: the whole transaction rolls
     *       back, so no hold is left blocking the calendar.
     * </ul>
     * No email goes out here: the guest's confirmation is sent only once the
     * signed webhook has confirmed the payment.
     */
    @Transactional
    public PublicBookingCheckoutResponse createBooking(PublicBookingRequest request) {
        if (request.paymentMethod() != null && !PublicBookingRequest.ONLINE_CARD.equals(request.paymentMethod())) {
            throw new BadRequestException("Rezervarea online se poate plăti doar cu cardul.");
        }
        StripeCheckoutService checkout = stripeCheckoutService.orElseThrow(() ->
                new ServiceUnavailableException("ONLINE_BOOKING_UNAVAILABLE", ONLINE_BOOKING_UNAVAILABLE));

        Reservation reservation = reservationService.createGuestBooking(
                request.propertyId(), request.guestFirstName(), request.guestLastName(),
                request.guestEmail(), request.guestPhone(), request.checkInDate(), request.checkOutDate(),
                request.numberOfGuests(), request.notes(), request.idempotencyKey());

        CheckoutSessionResponse session = openCheckout(checkout, reservation.getManagementToken());
        return new PublicBookingCheckoutResponse(
                publicReservationMapper.toResponse(reservation), session.checkoutUrl(), session.amount(),
                session.currency());
    }

    /**
     * Re-opens card payment for a booking still on hold (back from a
     * cancelled or declined checkout). The amount is recomputed inside
     * {@link StripeCheckoutService} - this path never accepts one.
     */
    @Transactional
    public CheckoutSessionResponse createCheckoutSession(String token) {
        StripeCheckoutService checkout = stripeCheckoutService.orElseThrow(() ->
                new ServiceUnavailableException("ONLINE_BOOKING_UNAVAILABLE", ONLINE_BOOKING_UNAVAILABLE));
        return openCheckout(checkout, token);
    }

    private CheckoutSessionResponse openCheckout(StripeCheckoutService checkout, String token) {
        try {
            return checkout.createCheckoutSession(token);
        } catch (StripeGateway.StripePaymentException ex) {
            // Rethrown as a runtime exception, so the surrounding transaction -
            // including a hold created moments ago - rolls back.
            throw new ServiceUnavailableException("ONLINE_PAYMENT_UNAVAILABLE",
                    "Plata online nu este disponibilă momentan. Nu am reținut nicio rezervare - te rugăm să reîncerci în câteva minute sau să ne contactezi.");
        }
    }

    @Transactional(readOnly = true)
    public PublicReservationResponse getByToken(String token) {
        return publicReservationMapper.toResponse(reservationService.getByManagementToken(token));
    }

    @Transactional
    public PublicReservationResponse cancelByToken(String token) {
        return publicReservationMapper.toResponse(reservationService.cancelByManagementToken(token));
    }

    @Transactional(readOnly = true)
    public com.bhstays.pms.dto.reservation.CancellationQuoteResponse cancellationQuoteByToken(String token) {
        return reservationService.cancellationQuoteByManagementToken(token);
    }

    @Transactional
    public PublicReservationResponse updateByToken(String token, PublicBookingUpdateRequest request) {
        Reservation reservation = reservationService.updateByManagementToken(
                token, request.checkInDate(), request.checkOutDate(), request.numberOfGuests());
        return publicReservationMapper.toResponse(reservation);
    }

    @Transactional
    public List<MessageResponse> listMessagesByToken(String token) {
        messageService.markThreadReadByGuest(token);
        return messageService.listForReservationByToken(token);
    }

    @Transactional
    public MessageResponse sendMessageByToken(String token, String body) {
        return messageService.sendGuestMessage(token, body);
    }
}
