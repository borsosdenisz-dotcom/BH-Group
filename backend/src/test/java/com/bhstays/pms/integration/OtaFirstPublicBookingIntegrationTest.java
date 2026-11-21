package com.bhstays.pms.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bhstays.pms.domain.Address;
import com.bhstays.pms.domain.Property;
import com.bhstays.pms.domain.PropertyStatus;
import com.bhstays.pms.domain.PropertyType;
import com.bhstays.pms.domain.Reservation;
import com.bhstays.pms.domain.ReservationSource;
import com.bhstays.pms.domain.ReservationStatus;
import com.bhstays.pms.dto.reservation.ReservationCreateRequest;
import com.bhstays.pms.payment.StripeGateway;
import com.bhstays.pms.repository.PropertyRepository;
import com.bhstays.pms.repository.ReservationRepository;
import com.bhstays.pms.service.ReservationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

@TestPropertySource(properties = {
        "app.stripe.secret-key=sk_test_ota_first_dummy",
        "app.stripe.publishable-key=pk_test_ota_first_dummy"
})
@AutoConfigureMockMvc
class OtaFirstPublicBookingIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private PropertyRepository propertyRepository;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private ReservationService reservationService;
    @MockitoSpyBean private StripeGateway stripeGateway;

    private Property property;

    @BeforeEach
    void setUp() {
        property = propertyRepository.save(Property.builder()
                .name("OTA-first " + UUID.randomUUID())
                .propertyType(PropertyType.APARTMENT)
                .status(PropertyStatus.ACTIVE)
                .address(new Address("Str. Test 1", "Cluj-Napoca", null, null, "România", null, null))
                .bedrooms(1)
                .bathrooms(1)
                .maxGuests(2)
                .basePricePerNight(new BigDecimal("150.00"))
                .build());
        clearInvocations(stripeGateway);
    }

    @Test
    void defaultFlagReportsDirectBookingOffEvenWithStripeConfigured() throws Exception {
        mockMvc.perform(get("/api/v1/public/payments/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cardPaymentsEnabled").value(true))
                .andExpect(jsonPath("$.data.directBookingEnabled").value(false));
    }

    @Test
    void publicPostCannotCreateAHoldOrBypassTheFlagWithAManualMethod() throws Exception {
        for (String method : new String[] {"ONLINE_CARD", "BANK_TRANSFER"}) {
            mockMvc.perform(post("/api/v1/public/reservations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(bookingBody(method)))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.errorCode").value("DIRECT_BOOKING_DISABLED"));
        }

        assertThat(reservationRepository.findAll().stream()
                .noneMatch(r -> r.getProperty().getId().equals(property.getId()))).isTrue();
        verify(stripeGateway, never()).createCheckoutSession(any());
    }

    @Test
    void publicCheckoutForAnExistingHoldIsBlockedBeforeStripe() throws Exception {
        Reservation held = reservationRepository.saveAndFlush(Reservation.builder()
                .property(property)
                .guestFirstName("Ana")
                .guestLastName("Popescu")
                .guestEmail("ana@example.com")
                .checkInDate(LocalDate.of(2035, 4, 1))
                .checkOutDate(LocalDate.of(2035, 4, 4))
                .numberOfGuests(2)
                .status(ReservationStatus.PENDING)
                .source(ReservationSource.DIRECT)
                .totalAmount(new BigDecimal("450.00"))
                .currency("RON")
                .managementToken(UUID.randomUUID().toString())
                .holdExpiresAt(Instant.now().plus(35, ChronoUnit.MINUTES))
                .build());

        mockMvc.perform(post("/api/v1/public/reservations/{token}/checkout", held.getManagementToken()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errorCode").value("DIRECT_BOOKING_DISABLED"));

        verify(stripeGateway, never()).createCheckoutSession(any());
    }

    @Test
    void staffEnteredReservationRemainsAvailableWhilePublicBookingIsOff() {
        var response = reservationService.create(new ReservationCreateRequest(
                property.getId(), "Staff", "Guest", "staff-guest@example.com", null,
                LocalDate.of(2036, 6, 1), LocalDate.of(2036, 6, 3), 2,
                ReservationSource.AIRBNB, new BigDecimal("350.00"), "RON", "Introduced by staff"));

        assertThat(response.status()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(response.source()).isEqualTo(ReservationSource.AIRBNB);
    }

    private String bookingBody(String method) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "propertyId", property.getId().toString(),
                "guestFirstName", "Ana",
                "guestLastName", "Popescu",
                "guestEmail", "ana@example.com",
                "guestPhone", "+40722111222",
                "checkInDate", "2035-03-01",
                "checkOutDate", "2035-03-05",
                "numberOfGuests", 2,
                "paymentMethod", method));
    }
}
