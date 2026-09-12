package com.bhgroup.pms.service;

import com.bhgroup.pms.common.exception.BadRequestException;
import com.bhgroup.pms.common.exception.ResourceNotFoundException;
import com.bhgroup.pms.domain.Property;
import com.bhgroup.pms.dto.messaging.MessageResponse;
import com.bhgroup.pms.dto.payment.CheckoutSessionResponse;
import com.bhgroup.pms.dto.property.PriceQuoteResponse;
import com.bhgroup.pms.dto.publicapi.PublicBookingRequest;
import com.bhgroup.pms.dto.publicapi.PublicBookingUpdateRequest;
import com.bhgroup.pms.dto.publicapi.PublicReservationResponse;
import com.bhgroup.pms.domain.Reservation;
import com.bhgroup.pms.dto.reservation.AvailabilityResponse;
import com.bhgroup.pms.repository.PropertyRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bhgroup.pms.service.mapper.PublicReservationMapper;
@Service
@RequiredArgsConstructor
public class PublicReservationService {

    private final ReservationService reservationService;
    private final EmailService emailService;
    private final PublicReservationMapper publicReservationMapper;
    private final PricingService pricingService;
    private final PropertyRepository propertyRepository;
    private final MessageService messageService;
    /**
     * Absent when no Stripe key is configured: the site then offers manual
     * payment (bank transfer / on arrival) only, and the checkout endpoint
     * says so instead of failing obscurely.
     */
    private final Optional<StripeCheckoutService> stripeCheckoutService;

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

    @Transactional
    public PublicReservationResponse createBooking(PublicBookingRequest request) {
        Reservation reservation = reservationService.createGuestBooking(
                request.propertyId(), request.guestFirstName(), request.guestLastName(),
                request.guestEmail(), request.guestPhone(), request.checkInDate(), request.checkOutDate(),
                request.numberOfGuests(), request.notes(), request.idempotencyKey());

        emailService.sendBookingConfirmationEmail(
                reservation.getGuestEmail(), reservation.getGuestFirstName(), reservation.getProperty().getName(),
                reservation.getCheckInDate().toString(), reservation.getCheckOutDate().toString(),
                reservation.getManagementToken());

        return publicReservationMapper.toResponse(reservation);
    }

    /**
     * Hands back the Stripe-hosted URL the guest is redirected to in order to
     * pay by card. The amount is recomputed inside
     * {@link StripeCheckoutService} - this path never accepts one.
     */
    @Transactional
    public CheckoutSessionResponse createCheckoutSession(String token) {
        return stripeCheckoutService
                .orElseThrow(() -> new BadRequestException(
                        "Plata cu cardul nu este disponibilă momentan. Te rugăm să alegi plata prin transfer bancar."))
                .createCheckoutSession(token);
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
    public com.bhgroup.pms.dto.reservation.CancellationQuoteResponse cancellationQuoteByToken(String token) {
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
