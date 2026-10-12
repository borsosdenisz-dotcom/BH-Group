package com.bhstays.pms.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bhstays.pms.common.exception.DemoPropertyException;
import com.bhstays.pms.domain.Address;
import com.bhstays.pms.domain.Payment;
import com.bhstays.pms.domain.PaymentMethod;
import com.bhstays.pms.domain.PaymentProvider;
import com.bhstays.pms.domain.PaymentStatus;
import com.bhstays.pms.domain.Property;
import com.bhstays.pms.domain.PropertyStatus;
import com.bhstays.pms.domain.PropertyType;
import com.bhstays.pms.domain.Reservation;
import com.bhstays.pms.domain.ReservationSource;
import com.bhstays.pms.domain.ReservationStatus;
import com.bhstays.pms.payment.PaymentGatewayResult;
import com.bhstays.pms.payment.StripeGateway;
import com.bhstays.pms.repository.PaymentRepository;
import com.bhstays.pms.repository.PropertyRepository;
import com.bhstays.pms.repository.ReservationRepository;
import com.bhstays.pms.security.SecureTokenGenerator;
import com.bhstays.pms.service.EmailService;
import com.bhstays.pms.service.StripeWebhookService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * A demo property is a showcase: listed on the public site, never quotable,
 * bookable or payable - on any public path, including a booking token that
 * existed before the property became demo. Stripe is configured here, so
 * every refusal below is the demo check and not "card payments are off";
 * outbound Stripe calls are stubbed and must never be reached for a demo.
 */
@TestPropertySource(properties = {
        "app.stripe.secret-key=sk_test_integration_dummy",
        "app.stripe.publishable-key=pk_test_integration_dummy",
        "app.stripe.webhook-secret=" + DemoPropertyBookingIntegrationTest.WEBHOOK_SECRET
})
@AutoConfigureMockMvc
class DemoPropertyBookingIntegrationTest extends AbstractIntegrationTest {

    static final String WEBHOOK_SECRET = "whsec_demo_property_test_secret";

    private static final LocalDate CHECK_IN = LocalDate.of(2033, 5, 10);
    private static final LocalDate CHECK_OUT = LocalDate.of(2033, 5, 14);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private PropertyRepository propertyRepository;
    @Autowired
    private ReservationRepository reservationRepository;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private SecureTokenGenerator secureTokenGenerator;
    @Autowired
    private StripeWebhookService stripeWebhookService;

    @MockitoBean
    private EmailService emailService;

    @MockitoSpyBean
    private StripeGateway stripeGateway;

    private Property demo;
    private Property bookable;

    @BeforeEach
    void setUp() {
        String run = UUID.randomUUID().toString().substring(0, 8);
        demo = propertyRepository.save(property("Demo showcase " + run, true));
        bookable = propertyRepository.save(property("Bookable control " + run, false));
        clearInvocations(stripeGateway, emailService);
    }

    private static Property property(String name, boolean demo) {
        return Property.builder()
                .name(name)
                .propertyType(PropertyType.APARTMENT)
                .status(PropertyStatus.ACTIVE)
                .address(new Address("Str. Demo 1", "Cluj-Napoca", null, null, "România", null, null))
                .bedrooms(1)
                .bathrooms(1)
                .maxGuests(2)
                .basePricePerNight(new BigDecimal("125.00"))
                .demo(demo)
                .build();
    }

    // ------------------------------------------------------------------
    // quote / availability / search
    // ------------------------------------------------------------------

    @Test
    void aQuoteForADemoPropertyIs409_whileANormalPropertyStillGetsOne() throws Exception {
        mockMvc.perform(quote(demo))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(DemoPropertyException.MESSAGE));

        mockMvc.perform(quote(bookable))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(true))
                .andExpect(jsonPath("$.data.totalAmount").value(500.0));
    }

    @Test
    void aDemoPropertyIsNeverReportedAvailable() throws Exception {
        mockMvc.perform(availability(demo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(false));
        mockMvc.perform(availability(bookable))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(true));
    }

    @Test
    void searchListsADemoPropertyMarkedAsDemo_butNeverAsABookableResultForDates() throws Exception {
        mockMvc.perform(get("/api/v1/public/properties").param("search", demo.getName()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(demo.getId().toString()))
                .andExpect(jsonPath("$.data.content[0].demo").value(true));

        mockMvc.perform(get("/api/v1/public/properties").param("search", demo.getName())
                        .param("checkIn", CHECK_IN.toString()).param("checkOut", CHECK_OUT.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isEmpty());

        mockMvc.perform(get("/api/v1/public/properties").param("search", bookable.getName())
                        .param("checkIn", CHECK_IN.toString()).param("checkOut", CHECK_OUT.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(bookable.getId().toString()))
                .andExpect(jsonPath("$.data.content[0].demo").value(false));

        mockMvc.perform(get("/api/v1/public/properties/{id}", demo.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.demo").value(true));
    }

    // ------------------------------------------------------------------
    // booking
    // ------------------------------------------------------------------

    @Test
    void aPublicBookingOfADemoPropertyIs409AndHoldsNothing() throws Exception {
        // Whatever payment method is asked for, the demo refusal comes first.
        for (String method : new String[] {null, "ONLINE_CARD", "BANK_TRANSFER"}) {
            mockMvc.perform(post("/api/v1/public/reservations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(bookingBody(demo, method)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value(DemoPropertyException.MESSAGE));
        }

        assertThat(reservationsOf(demo)).isEmpty();
        verify(stripeGateway, never()).createCheckoutSession(any());
    }

    @Test
    void aPublicBookingOfANormalPropertyIsUnchanged() throws Exception {
        doReturn(new StripeGateway.StripeCheckoutSession("cs_control_" + UUID.randomUUID(),
                "https://checkout.stripe.com/c/pay/cs_control"))
                .when(stripeGateway).createCheckoutSession(any());

        mockMvc.perform(post("/api/v1/public/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingBody(bookable, "ONLINE_CARD")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.checkoutUrl").value("https://checkout.stripe.com/c/pay/cs_control"));

        assertThat(reservationsOf(bookable)).singleElement()
                .satisfies(r -> assertThat(r.getStatus()).isEqualTo(ReservationStatus.PENDING));
    }

    // ------------------------------------------------------------------
    // existing tokens: holds taken before the property became demo
    // ------------------------------------------------------------------

    @Test
    void anExistingHoldTokenCannotOpenCheckoutOnceThePropertyIsDemo() throws Exception {
        Property property = propertyRepository.save(property("Becomes demo " + UUID.randomUUID(), false));
        Reservation held = heldBooking(property);
        property.setDemo(true);
        propertyRepository.saveAndFlush(property);

        mockMvc.perform(post("/api/v1/public/reservations/{token}/checkout", held.getManagementToken()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(DemoPropertyException.MESSAGE));

        verify(stripeGateway, never()).createCheckoutSession(any());
        assertThat(paymentRepository.findByReservationIdOrderByCreatedAtDesc(held.getId())).isEmpty();
    }

    @Test
    void aSessionOpenedBeforeThePropertyBecameDemo_isRefundedWhenPaid_neverConfirmed() {
        Property property = propertyRepository.save(property("Paid after demo " + UUID.randomUUID(), false));
        Payment payment = openCheckout(heldBooking(property));
        property.setDemo(true);
        propertyRepository.saveAndFlush(property);
        doReturn(PaymentGatewayResult.succeeded("re_demo_" + UUID.randomUUID()))
                .when(stripeGateway).refund(any(), any(), any());

        String payload = paidEvent(payment);
        stripeWebhookService.handle(payload, sign(payload));

        Reservation reservation = reservationRepository.findById(payment.getReservation().getId()).orElseThrow();
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(paymentRepository.findById(payment.getId()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.REFUNDED);
        verify(stripeGateway).refund(any(), any(), any());
        verify(emailService, never()).sendPaymentConfirmedEmail(any(), any(), any(), any(), any(), any(), any(), any());
    }

    // ------------------------------------------------------------------
    // fixtures
    // ------------------------------------------------------------------

    private org.springframework.test.web.servlet.RequestBuilder quote(Property property) {
        return get("/api/v1/public/reservations/quote")
                .param("propertyId", property.getId().toString())
                .param("checkIn", CHECK_IN.toString())
                .param("checkOut", CHECK_OUT.toString())
                .param("guests", "2");
    }

    private org.springframework.test.web.servlet.RequestBuilder availability(Property property) {
        return get("/api/v1/public/reservations/availability")
                .param("propertyId", property.getId().toString())
                .param("checkIn", CHECK_IN.toString())
                .param("checkOut", CHECK_OUT.toString());
    }

    private String bookingBody(Property property, String paymentMethod) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("propertyId", property.getId().toString());
        body.put("guestFirstName", "Ana");
        body.put("guestLastName", "Popescu");
        body.put("guestEmail", "ana@example.com");
        body.put("guestPhone", "0700000000");
        body.put("checkInDate", CHECK_IN.toString());
        body.put("checkOutDate", CHECK_OUT.toString());
        body.put("numberOfGuests", 2);
        if (paymentMethod != null) {
            body.put("paymentMethod", paymentMethod);
        }
        return objectMapper.writeValueAsString(body);
    }

    private List<Reservation> reservationsOf(Property property) {
        return reservationRepository.findAll().stream()
                .filter(r -> r.getProperty().getId().equals(property.getId()))
                .toList();
    }

    private Reservation heldBooking(Property property) {
        return reservationRepository.saveAndFlush(Reservation.builder()
                .property(property)
                .guestFirstName("Ana")
                .guestLastName("Popescu")
                .guestEmail("ana@example.com")
                .guestPhone("0700000000")
                .checkInDate(CHECK_IN)
                .checkOutDate(CHECK_OUT)
                .numberOfGuests(2)
                .status(ReservationStatus.PENDING)
                .source(ReservationSource.DIRECT)
                .totalAmount(new BigDecimal("500.00"))
                .currency("RON")
                .managementToken(secureTokenGenerator.generateRawToken())
                .holdExpiresAt(Instant.now().plus(35, ChronoUnit.MINUTES))
                .build());
    }

    private Payment openCheckout(Reservation reservation) {
        return paymentRepository.saveAndFlush(Payment.builder()
                .reservation(reservation)
                .provider(PaymentProvider.STRIPE)
                .method(PaymentMethod.ONLINE_CARD)
                .status(PaymentStatus.PENDING)
                .amount(new BigDecimal("500.00"))
                .currency("RON")
                .checkoutSessionId("cs_test_" + UUID.randomUUID())
                .checkoutUrl("https://checkout.stripe.com/c/pay/test")
                .checkoutExpiresAt(Instant.now().plus(30, ChronoUnit.MINUTES))
                .build());
    }

    private static String paidEvent(Payment payment) {
        String eventId = "evt_" + UUID.randomUUID().toString().replace("-", "");
        return """
                {
                  "id": "%s",
                  "object": "event",
                  "type": "checkout.session.completed",
                  "data": { "object": {
                    "id": "%s",
                    "object": "checkout.session",
                    "payment_intent": "pi_%s",
                    "payment_status": "paid",
                    "amount_total": 50000,
                    "currency": "ron",
                    "metadata": { "reservationId": "%s" }
                  }}
                }
                """.formatted(eventId, payment.getCheckoutSessionId(), eventId, payment.getReservation().getId());
    }

    /** Signs a payload exactly as Stripe does for the {@code Stripe-Signature} header. */
    private static String sign(String payload) {
        try {
            long timestamp = Instant.now().getEpochSecond();
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal((timestamp + "." + payload).getBytes(StandardCharsets.UTF_8));
            return "t=" + timestamp + ",v1=" + HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
