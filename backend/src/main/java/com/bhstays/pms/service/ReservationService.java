package com.bhstays.pms.service;

import com.bhstays.pms.common.exception.BadRequestException;
import com.bhstays.pms.common.exception.ResourceNotFoundException;
import com.bhstays.pms.common.response.PageResponse;
import com.bhstays.pms.domain.AuditAction;
import com.bhstays.pms.domain.Property;
import com.bhstays.pms.repository.PropertyRepository;
import com.bhstays.pms.domain.PropertyStatus;
import com.bhstays.pms.dto.property.PriceQuoteResponse;
import com.bhstays.pms.dto.reservation.AccessCodeUpdateRequest;
import com.bhstays.pms.dto.reservation.AvailabilityResponse;
import com.bhstays.pms.dto.reservation.CalendarEntryResponse;
import com.bhstays.pms.dto.reservation.ReservationCreateRequest;
import com.bhstays.pms.dto.reservation.ReservationResponse;
import com.bhstays.pms.dto.reservation.ReservationStatusUpdateRequest;
import com.bhstays.pms.dto.reservation.ReservationUpdateRequest;
import com.bhstays.pms.security.SecureTokenGenerator;
import com.bhstays.pms.service.EmailService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bhstays.pms.domain.Reservation;
import com.bhstays.pms.domain.ReservationSource;
import com.bhstays.pms.domain.ReservationStatus;
import com.bhstays.pms.repository.ReservationRepository;
import com.bhstays.pms.repository.ReservationSpecifications;
import com.bhstays.pms.service.mapper.ReservationMapper;
@Slf4j
@Service
public class ReservationService {

    private static final Map<ReservationStatus, Set<ReservationStatus>> ALLOWED_TRANSITIONS = Map.of(
            ReservationStatus.PENDING, Set.of(ReservationStatus.CONFIRMED, ReservationStatus.CANCELLED),
            ReservationStatus.CONFIRMED, Set.of(ReservationStatus.CHECKED_IN, ReservationStatus.CANCELLED,
                    ReservationStatus.NO_SHOW),
            ReservationStatus.CHECKED_IN, Set.of(ReservationStatus.CHECKED_OUT),
            ReservationStatus.CHECKED_OUT, Set.of(),
            ReservationStatus.CANCELLED, Set.of(),
            ReservationStatus.NO_SHOW, Set.of()
    );

    private static final java.time.Duration GUEST_BOOKING_HOLD_DURATION = java.time.Duration.ofMinutes(15);

    private final ReservationRepository reservationRepository;
    private final PropertyRepository propertyRepository;
    private final SecureTokenGenerator secureTokenGenerator;
    private final ReservationMapper reservationMapper;
    private final EmailService emailService;
    private final PricingService pricingService;
    private final CleaningTaskService cleaningTaskService;
    private final CancellationRefundCalculator cancellationRefundCalculator;
    private final PaymentService paymentService;
    private final AuditService auditService;

    // PaymentService also depends on ReservationService (to confirm a
    // reservation once it's fully paid), so this side of the cycle must be
    // injected lazily — a manual constructor is needed because Lombok does
    // not copy @Lazy from a field onto the generated constructor parameter.
    public ReservationService(ReservationRepository reservationRepository, PropertyRepository propertyRepository,
                               SecureTokenGenerator secureTokenGenerator, ReservationMapper reservationMapper,
                               EmailService emailService, PricingService pricingService,
                               CleaningTaskService cleaningTaskService,
                               CancellationRefundCalculator cancellationRefundCalculator,
                               @Lazy PaymentService paymentService, AuditService auditService) {
        this.reservationRepository = reservationRepository;
        this.propertyRepository = propertyRepository;
        this.secureTokenGenerator = secureTokenGenerator;
        this.reservationMapper = reservationMapper;
        this.emailService = emailService;
        this.pricingService = pricingService;
        this.cleaningTaskService = cleaningTaskService;
        this.cancellationRefundCalculator = cancellationRefundCalculator;
        this.paymentService = paymentService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public PageResponse<ReservationResponse> list(UUID propertyId, ReservationStatus status, String search,
                                                    LocalDate from, LocalDate to, Pageable pageable) {
        Specification<Reservation> spec = ReservationSpecifications.combine(
                ReservationSpecifications.hasProperty(propertyId),
                ReservationSpecifications.hasStatus(status),
                ReservationSpecifications.search(search),
                ReservationSpecifications.checkInFrom(from),
                ReservationSpecifications.checkInTo(to)
        );

        Page<Reservation> page = reservationRepository.findAll(spec, pageable);
        return PageResponse.of(page, reservationMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public ReservationResponse get(UUID id) {
        return reservationMapper.toResponse(findOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<List<String>> exportRows(UUID propertyId, ReservationStatus status, String search,
                                          LocalDate from, LocalDate to) {
        Specification<Reservation> spec = ReservationSpecifications.combine(
                ReservationSpecifications.hasProperty(propertyId),
                ReservationSpecifications.hasStatus(status),
                ReservationSpecifications.search(search),
                ReservationSpecifications.checkInFrom(from),
                ReservationSpecifications.checkInTo(to)
        );

        return reservationRepository.findAll(spec, Sort.by("checkInDate").descending())
                .stream()
                .map(r -> List.of(
                        r.getGuestFirstName() + " " + r.getGuestLastName(),
                        r.getGuestEmail() != null ? r.getGuestEmail() : "",
                        r.getGuestPhone() != null ? r.getGuestPhone() : "",
                        r.getProperty().getName(),
                        r.getCheckInDate().toString(),
                        r.getCheckOutDate().toString(),
                        String.valueOf(r.getNumberOfGuests()),
                        r.getStatus().name(),
                        r.getSource().name(),
                        r.getTotalAmount() != null ? r.getTotalAmount().toString() : "",
                        r.getCurrency(),
                        r.getNotes() != null ? r.getNotes() : ""
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CalendarEntryResponse> calendar(UUID propertyId, LocalDate from, LocalDate to) {
        return reservationRepository.findCalendarEntries(propertyId, from, to, ReservationStatus.NON_BLOCKING).stream()
                .map(reservationMapper::toCalendarEntry)
                .toList();
    }

    @Transactional(readOnly = true)
    public AvailabilityResponse availability(UUID propertyId, LocalDate checkIn, LocalDate checkOut) {
        boolean hasOverlap = !reservationRepository
                .findOverlapping(propertyId, checkIn, checkOut, null, ReservationStatus.NON_BLOCKING)
                .isEmpty();
        return new AvailabilityResponse(!hasOverlap);
    }

    @Transactional
    public ReservationResponse create(ReservationCreateRequest request) {
        Property property = propertyRepository.findById(request.propertyId())
                .orElseThrow(() -> new ResourceNotFoundException("Property not found"));

        validateDates(request.checkInDate(), request.checkOutDate());
        pricingService.validateGuestCount(property, request.numberOfGuests());
        assertNoOverlap(property.getId(), request.checkInDate(), request.checkOutDate(), null);

        Reservation reservation = Reservation.builder()
                .property(property)
                .guestFirstName(request.guestFirstName())
                .guestLastName(request.guestLastName())
                .guestEmail(request.guestEmail())
                .guestPhone(request.guestPhone())
                .checkInDate(request.checkInDate())
                .checkOutDate(request.checkOutDate())
                .numberOfGuests(request.numberOfGuests())
                .status(ReservationStatus.CONFIRMED)
                .source(request.source() != null ? request.source() : ReservationSource.DIRECT)
                .totalAmount(request.totalAmount())
                .currency(request.currency() != null && !request.currency().isBlank() ? request.currency() : "RON")
                .notes(request.notes())
                .build();
        snapshotStaffEnteredTotal(reservation);

        reservation = saveGuardingOverlap(reservation);

        return reservationMapper.toResponse(reservation);
    }

    @Transactional
    public ReservationResponse update(UUID id, ReservationUpdateRequest request) {
        Reservation reservation = findOrThrow(id);

        if (reservation.getStatus() == ReservationStatus.CHECKED_OUT
                || reservation.getStatus() == ReservationStatus.CANCELLED) {
            throw new BadRequestException("Cannot modify a reservation that is already checked out or cancelled");
        }

        validateDates(request.checkInDate(), request.checkOutDate());
        pricingService.validateGuestCount(reservation.getProperty(), request.numberOfGuests());
        assertNoOverlap(reservation.getProperty().getId(), request.checkInDate(), request.checkOutDate(), id);

        reservation.setGuestFirstName(request.guestFirstName());
        reservation.setGuestLastName(request.guestLastName());
        reservation.setGuestEmail(request.guestEmail());
        reservation.setGuestPhone(request.guestPhone());
        reservation.setCheckInDate(request.checkInDate());
        reservation.setCheckOutDate(request.checkOutDate());
        reservation.setNumberOfGuests(request.numberOfGuests());
        if (request.source() != null) {
            reservation.setSource(request.source());
        }
        reservation.setTotalAmount(request.totalAmount());
        if (request.currency() != null && !request.currency().isBlank()) {
            reservation.setCurrency(request.currency());
        }
        reservation.setNotes(request.notes());
        snapshotStaffEnteredTotal(reservation);

        reservation = saveGuardingOverlap(reservation);
        return reservationMapper.toResponse(reservation);
    }

    @Transactional
    public ReservationResponse updateStatus(UUID id, ReservationStatusUpdateRequest request) {
        Reservation reservation = findOrThrow(id);
        ReservationStatus current = reservation.getStatus();
        ReservationStatus target = request.status();

        if (!ALLOWED_TRANSITIONS.getOrDefault(current, Set.of()).contains(target)) {
            throw new BadRequestException(
                    "Cannot transition reservation from " + current + " to " + target);
        }

        reservation.setStatus(target);
        reservation = reservationRepository.save(reservation);

        if (target == ReservationStatus.CHECKED_OUT) {
            cleaningTaskService.autoCreateFromCheckout(reservation);
        } else if (target == ReservationStatus.CANCELLED) {
            processCancellationRefund(reservation);
        }

        return reservationMapper.toResponse(reservation);
    }

    @Transactional
    public void delete(UUID id) {
        reservationRepository.delete(findOrThrow(id));
    }

    @Transactional
    public ReservationResponse updateAccessCode(UUID id, AccessCodeUpdateRequest request) {
        Reservation reservation = findOrThrow(id);
        reservation.setAccessCode(request.accessCode() == null || request.accessCode().isBlank()
                ? null : request.accessCode().trim());
        reservation = reservationRepository.save(reservation);
        return reservationMapper.toResponse(reservation);
    }

    @Transactional
    public ReservationResponse sendCheckinInstructionsNow(UUID id) {
        Reservation reservation = findOrThrow(id);

        if (reservation.getAccessCode() == null || reservation.getAccessCode().isBlank()) {
            throw new BadRequestException("Set an access code before sending check-in instructions");
        }
        if (reservation.getGuestEmail() == null || reservation.getGuestEmail().isBlank()) {
            throw new BadRequestException("This reservation has no guest email");
        }
        if (reservation.getManagementToken() == null) {
            reservation.setManagementToken(secureTokenGenerator.generateRawToken());
        }

        sendCheckinEmail(reservation);
        reservation.setAccessCodeSentAt(Instant.now());
        reservation = reservationRepository.save(reservation);
        return reservationMapper.toResponse(reservation);
    }

    @Transactional
    public void sendPendingCheckinInstructions() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        List<Reservation> pending =
                reservationRepository.findPendingCheckinInstructions(ReservationStatus.CONFIRMED, tomorrow);

        for (Reservation reservation : pending) {
            try {
                if (reservation.getManagementToken() == null) {
                    reservation.setManagementToken(secureTokenGenerator.generateRawToken());
                }
                sendCheckinEmail(reservation);
                reservation.setAccessCodeSentAt(Instant.now());
                reservationRepository.save(reservation);
            } catch (Exception ex) {
                log.error("Failed to send check-in instructions for reservation {}", reservation.getId(), ex);
            }
        }
    }

    private void sendCheckinEmail(Reservation reservation) {
        Property property = reservation.getProperty();
        String address = property.getAddress().getAddressLine() + ", " + property.getAddress().getCity();
        emailService.sendCheckinInstructionsEmail(
                reservation.getGuestEmail(),
                reservation.getGuestFirstName(),
                property.getName(),
                reservation.getCheckInDate().toString(),
                property.getCheckInTime().format(DateTimeFormatter.ofPattern("HH:mm")),
                address,
                reservation.getAccessCode(),
                reservation.getManagementToken());
    }

    @Transactional
    public Reservation createGuestBooking(UUID propertyId, String guestFirstName, String guestLastName,
                                           String guestEmail, String guestPhone, LocalDate checkInDate,
                                           LocalDate checkOutDate, int numberOfGuests, String notes,
                                           String idempotencyKey) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            var existing = reservationRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                return existing.get();
            }
        }

        Property property = propertyRepository.findById(propertyId)
                .filter(p -> p.getStatus() == PropertyStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("Property not found"));

        validateDates(checkInDate, checkOutDate);
        pricingService.validateGuestCount(property, numberOfGuests);
        pricingService.validateStayLength(property, (int) ChronoUnit.DAYS.between(checkInDate, checkOutDate));
        assertNoOverlap(propertyId, checkInDate, checkOutDate, null);

        PriceQuoteResponse quote = pricingService.quote(property, checkInDate, checkOutDate, numberOfGuests);
        Reservation reservation = Reservation.builder()
                .property(property)
                .guestFirstName(guestFirstName)
                .guestLastName(guestLastName)
                .guestEmail(guestEmail)
                .guestPhone(guestPhone)
                .checkInDate(checkInDate)
                .checkOutDate(checkOutDate)
                .numberOfGuests(numberOfGuests)
                .status(ReservationStatus.PENDING)
                .source(ReservationSource.DIRECT)
                .totalAmount(quote.totalAmount())
                .currency("RON")
                .notes(notes)
                .managementToken(secureTokenGenerator.generateRawToken())
                .idempotencyKey(idempotencyKey != null && !idempotencyKey.isBlank() ? idempotencyKey : null)
                .holdExpiresAt(Instant.now().plus(GUEST_BOOKING_HOLD_DURATION))
                .build();
        ReservationPriceSnapshot.apply(reservation, quote);

        try {
            return reservationRepository.saveAndFlush(reservation);
        } catch (DataIntegrityViolationException ex) {
            // A concurrent request for the same idempotency key won the race and
            // already inserted the reservation: return it instead of failing.
            if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                Optional<Reservation> concurrent = reservationRepository.findByIdempotencyKey(idempotencyKey);
                if (concurrent.isPresent()) {
                    return concurrent.get();
                }
            }
            throw new BadRequestException("The property is not available for the selected dates");
        }
    }

    /**
     * Cancels PENDING guest bookings whose hold has expired without payment,
     * freeing the calendar for other guests. Any card payment still waiting
     * on Stripe is closed with it; should Stripe still report it paid later,
     * the webhook refunds it instead of confirming over the released dates.
     */
    @Transactional
    public void expireStaleHolds() {
        List<Reservation> expired =
                reservationRepository.findExpiredHolds(ReservationStatus.PENDING, Instant.now());
        for (Reservation reservation : expired) {
            releaseHold(reservation, "Perioada de reținere a expirat fără o plată confirmată");
            log.info("Expired unpaid booking hold for reservation {}", reservation.getId());
        }
    }

    /**
     * Releases an unpaid hold: the reservation is cancelled (so the GiST
     * no-overlap constraint stops counting it) and its open card payments
     * are closed. The caller must hold the reservation's row lock.
     */
    @Transactional
    public void releaseHold(Reservation reservation, String reason) {
        reservation.setStatus(ReservationStatus.CANCELLED);
        reservationRepository.save(reservation);
        paymentService.cancelOpenCardPayments(reservation.getId());
        auditService.recordSystemEvent(AuditAction.BOOKING_HOLD_RELEASED, "Reservation", reservation.getId(), reason);
    }

    /**
     * The reservation behind a management token, row-locked for the rest of
     * the transaction - so two "pay" clicks for one booking cannot both open
     * a Checkout session, and neither can race the webhook or expiry job.
     */
    @Transactional
    public Reservation lockByManagementToken(String token) {
        return reservationRepository.findByManagementTokenForUpdate(token)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found"));
    }

    @Transactional(readOnly = true)
    public Reservation getByManagementToken(String token) {
        return reservationRepository.findByManagementToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found"));
    }

    @Transactional
    public Reservation cancelByManagementToken(String token) {
        Reservation reservation = getByManagementToken(token);
        ReservationStatus current = reservation.getStatus();

        if (!ALLOWED_TRANSITIONS.getOrDefault(current, Set.of()).contains(ReservationStatus.CANCELLED)) {
            throw new BadRequestException("This reservation can no longer be cancelled");
        }

        reservation.setStatus(ReservationStatus.CANCELLED);
        reservation = reservationRepository.save(reservation);
        processCancellationRefund(reservation);
        return reservation;
    }

    @Transactional
    public Reservation updateByManagementToken(String token, LocalDate checkInDate, LocalDate checkOutDate,
                                                int numberOfGuests) {
        Reservation reservation = getByManagementToken(token);

        if (reservation.getStatus() == ReservationStatus.CHECKED_OUT
                || reservation.getStatus() == ReservationStatus.CANCELLED) {
            throw new BadRequestException("Cannot modify a reservation that is already checked out or cancelled");
        }
        // A Checkout session charges the price of the dates it was opened for;
        // changing them underneath it would let the guest pay one total and
        // be confirmed for another.
        if (reservation.getStatus() == ReservationStatus.PENDING
                && paymentService.findOpenCardCheckout(reservation.getId(), Instant.now()).isPresent()) {
            throw new BadRequestException(
                    "Plata cu cardul este în curs. Finalizează plata sau așteaptă expirarea ei înainte de a modifica rezervarea.");
        }

        validateDates(checkInDate, checkOutDate);
        pricingService.validateGuestCount(reservation.getProperty(), numberOfGuests);
        pricingService.validateStayLength(reservation.getProperty(),
                (int) ChronoUnit.DAYS.between(checkInDate, checkOutDate));
        assertNoOverlap(reservation.getProperty().getId(), checkInDate, checkOutDate, reservation.getId());

        reservation.setCheckInDate(checkInDate);
        reservation.setCheckOutDate(checkOutDate);
        reservation.setNumberOfGuests(numberOfGuests);
        PriceQuoteResponse quote = pricingService
                .quote(reservation.getProperty(), checkInDate, checkOutDate, numberOfGuests);
        reservation.setTotalAmount(quote.totalAmount());
        ReservationPriceSnapshot.apply(reservation, quote);

        return saveGuardingOverlap(reservation);
    }

    /**
     * A staff-entered total only gets a price breakdown when it is exactly
     * what the pricing engine quotes for the same stay; any other amount
     * (negotiated price, OTA payout, different currency) keeps the split
     * unknown rather than inventing one.
     */
    private void snapshotStaffEnteredTotal(Reservation reservation) {
        PriceQuoteResponse quote = reservation.getTotalAmount() != null
                ? pricingService.quote(reservation.getProperty(), reservation.getCheckInDate(),
                        reservation.getCheckOutDate(), reservation.getNumberOfGuests())
                : null;
        ReservationPriceSnapshot.apply(reservation, quote);
    }

    /**
     * Refunds whatever percentage the property's cancellation policy allows
     * for how many days out check-in was when the guest cancelled. A no-op
     * if the reservation had no successful payments to refund.
     */
    private void processCancellationRefund(Reservation reservation) {
        var refundPercent = cancellationQuoteFor(reservation).refundPercent();
        paymentService.autoRefundForCancellation(reservation.getId(), refundPercent);
    }

    @Transactional(readOnly = true)
    public com.bhstays.pms.dto.reservation.CancellationQuoteResponse cancellationQuote(UUID id) {
        return cancellationQuoteFor(findOrThrow(id));
    }

    @Transactional(readOnly = true)
    public com.bhstays.pms.dto.reservation.CancellationQuoteResponse cancellationQuoteByManagementToken(String token) {
        return cancellationQuoteFor(getByManagementToken(token));
    }

    private com.bhstays.pms.dto.reservation.CancellationQuoteResponse cancellationQuoteFor(Reservation reservation) {
        long daysBeforeCheckIn = ChronoUnit.DAYS.between(LocalDate.now(), reservation.getCheckInDate());
        var refundPercent = cancellationRefundCalculator
                .refundPercentFor(reservation.getProperty().getCancellationPolicy(), daysBeforeCheckIn);
        var estimatedAmount = paymentService.estimateRefund(reservation.getId(), refundPercent);
        return new com.bhstays.pms.dto.reservation.CancellationQuoteResponse(
                refundPercent, estimatedAmount, reservation.getCurrency());
    }

    /**
     * Saves a reservation and forces an immediate flush so that a violation of
     * the DB-level no-overlap exclusion constraint (the real guarantee against
     * double-booking under concurrent requests) surfaces here as a clean 400
     * instead of an unhandled exception during transaction commit.
     */
    private Reservation saveGuardingOverlap(Reservation reservation) {
        try {
            return reservationRepository.saveAndFlush(reservation);
        } catch (DataIntegrityViolationException ex) {
            throw new BadRequestException("The property is not available for the selected dates");
        }
    }

    private void assertNoOverlap(UUID propertyId, LocalDate checkIn, LocalDate checkOut, UUID excludeId) {
        if (!reservationRepository
                .findOverlapping(propertyId, checkIn, checkOut, excludeId, ReservationStatus.NON_BLOCKING)
                .isEmpty()) {
            throw new BadRequestException("The property is not available for the selected dates");
        }
    }

    private void validateDates(LocalDate checkIn, LocalDate checkOut) {
        if (!checkOut.isAfter(checkIn)) {
            throw new BadRequestException("Check-out date must be after check-in date");
        }
    }

    private Reservation findOrThrow(UUID id) {
        return reservationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found"));
    }
}
