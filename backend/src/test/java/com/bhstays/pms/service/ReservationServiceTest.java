package com.bhstays.pms.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bhstays.pms.common.exception.BadRequestException;
import com.bhstays.pms.domain.CancellationPolicy;
import com.bhstays.pms.domain.Property;
import com.bhstays.pms.domain.PropertyStatus;
import com.bhstays.pms.domain.Reservation;
import com.bhstays.pms.domain.ReservationStatus;
import com.bhstays.pms.dto.reservation.ReservationCreateRequest;
import com.bhstays.pms.dto.reservation.ReservationStatusUpdateRequest;
import com.bhstays.pms.dto.reservation.ReservationUpdateRequest;
import com.bhstays.pms.repository.PropertyRepository;
import com.bhstays.pms.repository.ReservationRepository;
import com.bhstays.pms.security.SecureTokenGenerator;
import com.bhstays.pms.service.mapper.ReservationMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private PropertyRepository propertyRepository;
    @Mock
    private SecureTokenGenerator secureTokenGenerator;
    @Mock
    private EmailService emailService;
    @Mock
    private PricingService pricingService;
    @Mock
    private CleaningTaskService cleaningTaskService;
    @Mock
    private CancellationRefundCalculator cancellationRefundCalculator;
    @Mock
    private PaymentService paymentService;
    @Mock
    private AuditService auditService;

    private ReservationService reservationService;
    private Property property;

    @BeforeEach
    void setUp() {
        reservationService = new ReservationService(
                reservationRepository, propertyRepository, secureTokenGenerator, new ReservationMapper(),
                emailService, pricingService, cleaningTaskService, cancellationRefundCalculator, paymentService,
                auditService);

        property = Property.builder()
                .name("Test Apartment")
                .status(PropertyStatus.ACTIVE)
                .cancellationPolicy(CancellationPolicy.MODERATE)
                .build();
        property.setId(UUID.randomUUID());
    }

    @Test
    void create_rejectsCheckOutOnOrBeforeCheckIn() {
        when(propertyRepository.findById(property.getId())).thenReturn(Optional.of(property));

        ReservationCreateRequest sameDayRequest = createRequest(
                LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 10));

        assertThatThrownBy(() -> reservationService.create(sameDayRequest))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Check-out date must be after check-in date");
    }

    @Test
    void create_rejectsOverlappingDates() {
        when(propertyRepository.findById(property.getId())).thenReturn(Optional.of(property));
        Reservation existingBooking = mock(Reservation.class);
        when(reservationRepository.findOverlapping(eq(property.getId()), any(), any(), eq(null), any()))
                .thenReturn(List.of(existingBooking));

        ReservationCreateRequest request = createRequest(
                LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 12));

        assertThatThrownBy(() -> reservationService.create(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("not available");
    }

    @Test
    void create_rejectsGuestCountAboveMax() {
        when(propertyRepository.findById(property.getId())).thenReturn(Optional.of(property));
        doThrow(new BadRequestException("This property accepts a maximum of 2 guests"))
                .when(pricingService).validateGuestCount(eq(property), org.mockito.ArgumentMatchers.anyInt());

        ReservationCreateRequest request = createRequest(
                LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 12));

        assertThatThrownBy(() -> reservationService.create(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("maximum");
    }

    @Test
    void create_succeedsWhenDatesAreValidAndFree() {
        when(propertyRepository.findById(property.getId())).thenReturn(Optional.of(property));
        when(reservationRepository.findOverlapping(eq(property.getId()), any(), any(), eq(null), any()))
                .thenReturn(List.of());
        when(reservationRepository.saveAndFlush(any(Reservation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ReservationCreateRequest request = createRequest(
                LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 12));

        var response = reservationService.create(request);

        assertThat(response.status()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(response.checkInDate()).isEqualTo(LocalDate.of(2026, 8, 10));
    }

    @ParameterizedTest(name = "{0} -> {1} is allowed")
    @CsvSource({
            "PENDING, CONFIRMED",
            "PENDING, CANCELLED",
            "CONFIRMED, CHECKED_IN",
            "CONFIRMED, CANCELLED",
            "CONFIRMED, NO_SHOW",
            "CHECKED_IN, CHECKED_OUT",
    })
    void updateStatus_allowsValidTransitions(ReservationStatus from, ReservationStatus to) {
        Reservation reservation = existingReservation(from);
        when(reservationRepository.findById(reservation.getId())).thenReturn(Optional.of(reservation));
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = reservationService.updateStatus(
                reservation.getId(), new ReservationStatusUpdateRequest(to));

        assertThat(response.status()).isEqualTo(to);
    }

    @ParameterizedTest(name = "{0} -> {1} is rejected")
    @CsvSource({
            "CHECKED_OUT, CONFIRMED",
            "CHECKED_OUT, CHECKED_IN",
            "CANCELLED, CONFIRMED",
            "NO_SHOW, CONFIRMED",
            "PENDING, CHECKED_IN",
            "CONFIRMED, CHECKED_OUT",
    })
    void updateStatus_rejectsInvalidTransitions(ReservationStatus from, ReservationStatus to) {
        Reservation reservation = existingReservation(from);
        when(reservationRepository.findById(reservation.getId())).thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> reservationService.updateStatus(
                reservation.getId(), new ReservationStatusUpdateRequest(to)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Cannot transition");
    }

    @Test
    void updateStatus_checkedOutTriggersAutomaticCleaningTask() {
        Reservation reservation = existingReservation(ReservationStatus.CHECKED_IN);
        when(reservationRepository.findById(reservation.getId())).thenReturn(Optional.of(reservation));
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(inv -> inv.getArgument(0));

        reservationService.updateStatus(reservation.getId(), new ReservationStatusUpdateRequest(ReservationStatus.CHECKED_OUT));

        org.mockito.Mockito.verify(cleaningTaskService).autoCreateFromCheckout(reservation);
    }

    @Test
    void updateStatus_cancellationTriggersRefundCalculation() {
        Reservation reservation = existingReservation(ReservationStatus.CONFIRMED);
        when(reservationRepository.findById(reservation.getId())).thenReturn(Optional.of(reservation));
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(inv -> inv.getArgument(0));
        doReturn(BigDecimal.valueOf(100)).when(cancellationRefundCalculator)
                .refundPercentFor(eq(CancellationPolicy.MODERATE), org.mockito.ArgumentMatchers.anyLong());

        reservationService.updateStatus(reservation.getId(), new ReservationStatusUpdateRequest(ReservationStatus.CANCELLED));

        org.mockito.Mockito.verify(paymentService)
                .autoRefundForCancellation(eq(reservation.getId()), eq(BigDecimal.valueOf(100)));
    }

    @Test
    void update_rejectsGuestCountAboveMax() {
        Reservation reservation = existingReservation(ReservationStatus.CONFIRMED);
        when(reservationRepository.findById(reservation.getId())).thenReturn(Optional.of(reservation));
        doThrow(new BadRequestException("This property accepts a maximum of 2 guests"))
                .when(pricingService).validateGuestCount(eq(property), org.mockito.ArgumentMatchers.anyInt());

        ReservationUpdateRequest request = new ReservationUpdateRequest(
                "Ana", "Popescu", "ana@example.com", "0700000000",
                LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 12), 5, null, null, null, null);

        assertThatThrownBy(() -> reservationService.update(reservation.getId(), request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("maximum");
    }

    @Test
    void createGuestBooking_rejectsGuestCountAboveMax() {
        when(propertyRepository.findById(property.getId())).thenReturn(Optional.of(property));
        doThrow(new BadRequestException("This property accepts a maximum of 2 guests"))
                .when(pricingService).validateGuestCount(eq(property), org.mockito.ArgumentMatchers.anyInt());

        assertThatThrownBy(() -> reservationService.createGuestBooking(
                property.getId(), "Ana", "Popescu", "ana@example.com", "0700000000",
                LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 12), 5, null, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("maximum");
    }

    @Test
    void updateByManagementToken_rejectsGuestCountAboveMax() {
        Reservation reservation = existingReservation(ReservationStatus.CONFIRMED);
        when(reservationRepository.findByManagementToken("token123")).thenReturn(Optional.of(reservation));
        doThrow(new BadRequestException("This property accepts a maximum of 2 guests"))
                .when(pricingService).validateGuestCount(eq(property), org.mockito.ArgumentMatchers.anyInt());

        assertThatThrownBy(() -> reservationService.updateByManagementToken(
                "token123", LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 12), 5))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("maximum");
    }

    private ReservationCreateRequest createRequest(LocalDate checkIn, LocalDate checkOut) {
        return new ReservationCreateRequest(
                property.getId(), "Ana", "Popescu", "ana@example.com", "0700000000",
                checkIn, checkOut, 2, null, null, null, null);
    }

    private Reservation existingReservation(ReservationStatus status) {
        Reservation reservation = Reservation.builder()
                .property(property)
                .guestFirstName("Ana")
                .guestLastName("Popescu")
                .checkInDate(LocalDate.now().plusDays(10))
                .checkOutDate(LocalDate.now().plusDays(13))
                .numberOfGuests(2)
                .status(status)
                .currency("RON")
                .build();
        reservation.setId(UUID.randomUUID());
        return reservation;
    }
}
