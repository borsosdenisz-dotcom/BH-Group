package com.bhstays.pms.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bhstays.pms.domain.Address;
import com.bhstays.pms.domain.Property;
import com.bhstays.pms.domain.PropertyStatus;
import com.bhstays.pms.domain.PropertyType;
import com.bhstays.pms.repository.PropertyRepository;
import com.bhstays.pms.repository.ReservationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Public booking is card-only. With no Stripe configured (this context sets
 * no Stripe keys) there is no way to pay online, so the public endpoint must
 * refuse outright: no hold, no reservation - and certainly no booking that
 * looks confirmed.
 */
@AutoConfigureMockMvc
class PublicBookingWithoutStripeIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private PropertyRepository propertyRepository;
    @Autowired
    private ReservationRepository reservationRepository;

    private Property property;

    @BeforeEach
    void setUp() {
        property = propertyRepository.save(Property.builder()
                .name("No Stripe test property")
                .propertyType(PropertyType.APARTMENT)
                .status(PropertyStatus.ACTIVE)
                .address(new Address("Str. Test 3", "Cluj-Napoca", null, null, "România", null, null))
                .bedrooms(1)
                .bathrooms(1)
                .maxGuests(2)
                .basePricePerNight(new BigDecimal("125.00"))
                .build());
    }

    private String body(String paymentMethod) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("propertyId", property.getId().toString());
        body.put("guestFirstName", "Ana");
        body.put("guestLastName", "Popescu");
        body.put("guestEmail", "ana@example.com");
        body.put("guestPhone", "0700000000");
        body.put("checkInDate", "2032-03-01");
        body.put("checkOutDate", "2032-03-05");
        body.put("numberOfGuests", 2);
        if (paymentMethod != null) {
            body.put("paymentMethod", paymentMethod);
        }
        return objectMapper.writeValueAsString(body);
    }

    @Test
    void theSiteReportsCardPaymentAsUnavailable() throws Exception {
        mockMvc.perform(get("/api/v1/public/payments/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cardPaymentsEnabled").value(false));
    }

    @Test
    void aPublicBookingIsRefusedWithoutCreatingAHoldOrAReservation() throws Exception {
        mockMvc.perform(post("/api/v1/public/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(null)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("nu este momentan disponibilă")));

        // Asking for a manual method instead does not open a back door either.
        mockMvc.perform(post("/api/v1/public/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("BANK_TRANSFER")))
                .andExpect(status().isBadRequest());

        assertThat(reservationRepository.findAll().stream()
                .filter(r -> r.getProperty().getId().equals(property.getId()))
                .toList()).isEmpty();
    }
}
