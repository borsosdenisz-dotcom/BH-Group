package com.bhstays.pms.integration;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bhstays.pms.domain.Address;
import com.bhstays.pms.domain.Property;
import com.bhstays.pms.domain.PropertyStatus;
import com.bhstays.pms.domain.PropertyType;
import com.bhstays.pms.domain.Reservation;
import com.bhstays.pms.domain.ReservationStatus;
import com.bhstays.pms.repository.PropertyRepository;
import com.bhstays.pms.repository.ReservationRepository;
import com.bhstays.pms.security.SecureTokenGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Guards against a client bypassing the frontend entirely and calling the
 * public booking API directly with a numberOfGuests value above the
 * property's maxGuests. HTML/JS-only limits (a bounded <select>, a max
 * attribute) never protect a real API - only a server-side check does,
 * so this exercises the actual HTTP endpoints end-to-end against a real
 * Postgres-backed Spring context, not just the service layer in isolation.
 */
// Public booking is card-only and refused outright without Stripe, so a
// (dummy) Stripe configuration is needed to reach the guest-count check.
// The request fails validation before any call to Stripe is made.
@TestPropertySource(properties = {
        "app.stripe.secret-key=sk_test_integration_dummy",
        "app.stripe.publishable-key=pk_test_integration_dummy",
        "app.stripe.webhook-secret=whsec_integration_test_secret"
})
@AutoConfigureMockMvc
class PublicBookingGuestCountIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private PropertyRepository propertyRepository;
    @Autowired
    private ReservationRepository reservationRepository;
    @Autowired
    private SecureTokenGenerator secureTokenGenerator;

    private Property property;

    @BeforeEach
    void setUp() {
        property = propertyRepository.save(Property.builder()
                .name("Guest count test property")
                .propertyType(PropertyType.APARTMENT)
                .status(PropertyStatus.ACTIVE)
                .address(new Address("Str. Test 2", "Cluj-Napoca", null, null, "România", null, null))
                .bedrooms(1)
                .bathrooms(1)
                .maxGuests(2)
                .build());
    }

    @Test
    void quoteEndpointRejectsGuestCountAboveMax() throws Exception {
        mockMvc.perform(get("/api/v1/public/reservations/quote")
                        .param("propertyId", property.getId().toString())
                        .param("checkIn", "2027-09-01")
                        .param("checkOut", "2027-09-05")
                        .param("guests", "7"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("maximum")));
    }

    @Test
    void createBookingEndpointRejectsGuestCountAboveMax() throws Exception {
        String body = objectMapper.writeValueAsString(new java.util.HashMap<>() {{
            put("propertyId", property.getId().toString());
            put("guestFirstName", "Ana");
            put("guestLastName", "Popescu");
            put("guestEmail", "ana@example.com");
            put("guestPhone", "0700000000");
            put("checkInDate", "2027-09-01");
            put("checkOutDate", "2027-09-05");
            put("numberOfGuests", 7);
        }});

        mockMvc.perform(post("/api/v1/public/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("maximum")));

        org.assertj.core.api.Assertions.assertThat(
                reservationRepository.findAll().stream()
                        .filter(r -> r.getProperty().getId().equals(property.getId()))
                        .toList()).isEmpty();
    }

    @Test
    void manageEndpointRejectsGuestCountAboveMaxOnUpdate() throws Exception {
        Reservation reservation = reservationRepository.saveAndFlush(Reservation.builder()
                .property(property)
                .guestFirstName("Ana")
                .guestLastName("Popescu")
                .guestEmail("ana@example.com")
                .guestPhone("0700000000")
                .checkInDate(LocalDate.of(2027, 9, 1))
                .checkOutDate(LocalDate.of(2027, 9, 5))
                .numberOfGuests(2)
                .status(ReservationStatus.PENDING)
                .currency("RON")
                .managementToken(secureTokenGenerator.generateRawToken())
                .build());

        String body = objectMapper.writeValueAsString(new java.util.HashMap<>() {{
            put("checkInDate", "2027-09-01");
            put("checkOutDate", "2027-09-05");
            put("numberOfGuests", 7);
        }});

        mockMvc.perform(put("/api/v1/public/reservations/manage/{token}", reservation.getManagementToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("maximum")));
    }
}
