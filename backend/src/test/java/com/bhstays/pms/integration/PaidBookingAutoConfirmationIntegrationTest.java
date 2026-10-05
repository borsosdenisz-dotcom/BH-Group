package com.bhstays.pms.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bhstays.pms.domain.Address;
import com.bhstays.pms.domain.AuditAction;
import com.bhstays.pms.domain.NotificationType;
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
import com.bhstays.pms.domain.Role;
import com.bhstays.pms.domain.User;
import com.bhstays.pms.domain.UserStatus;
import com.bhstays.pms.dto.payment.ManualPaymentCreateRequest;
import com.bhstays.pms.payment.StripeGateway;
import com.bhstays.pms.repository.AuditLogRepository;
import com.bhstays.pms.repository.NotificationRepository;
import com.bhstays.pms.repository.PaymentRepository;
import com.bhstays.pms.repository.PaymentWebhookEventRepository;
import com.bhstays.pms.repository.PropertyRepository;
import com.bhstays.pms.repository.ReservationRepository;
import com.bhstays.pms.repository.UserRepository;
import com.bhstays.pms.security.SecureTokenGenerator;
import com.bhstays.pms.service.EmailService;
import com.bhstays.pms.service.PaymentService;
import com.bhstays.pms.service.PublicReservationService;
import com.bhstays.pms.service.ReservationService;
import com.bhstays.pms.service.StripeWebhookService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * End-to-end proof, against a real Postgres, that a direct booking paid by
 * card is confirmed by the signed Stripe webhook - and by nothing else.
 *
 * <p>Deliveries are signed exactly the way Stripe signs them (HMAC-SHA256
 * over {@code "<timestamp>.<payload>"} with the webhook secret), so the
 * real signature verification runs. No call ever reaches Stripe: these
 * paths only read the payload. Email sending is mocked so the test can
 * count it; everything else - locking, the GiST no-overlap constraint, the
 * inbox's unique index, the notification enum - is the real thing.
 */
@TestPropertySource(properties = {
        "app.stripe.secret-key=sk_test_integration_dummy",
        "app.stripe.publishable-key=pk_test_integration_dummy",
        "app.stripe.webhook-secret=" + PaidBookingAutoConfirmationIntegrationTest.WEBHOOK_SECRET
})
@AutoConfigureMockMvc
class PaidBookingAutoConfirmationIntegrationTest extends AbstractIntegrationTest {

    static final String WEBHOOK_SECRET = "whsec_integration_test_secret";

    @Autowired
    private StripeWebhookService stripeWebhookService;
    @Autowired
    private ReservationService reservationService;
    @Autowired
    private PublicReservationService publicReservationService;
    @Autowired
    private PropertyRepository propertyRepository;
    @Autowired
    private ReservationRepository reservationRepository;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private PaymentWebhookEventRepository paymentWebhookEventRepository;
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private AuditLogRepository auditLogRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private SecureTokenGenerator secureTokenGenerator;

    @Autowired
    private PaymentService paymentService;
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private EmailService emailService;

    /**
     * Real gateway (real signature verification); only the outbound
     * "open a Checkout session" call is stubbed per test, so nothing ever
     * reaches Stripe.
     */
    @MockitoSpyBean
    private StripeGateway stripeGateway;

    private Property property;
    private User admin;

    @BeforeEach
    void setUp() {
        property = propertyRepository.save(Property.builder()
                .name("Paid booking test property")
                .propertyType(PropertyType.APARTMENT)
                .status(PropertyStatus.ACTIVE)
                .address(new Address("Str. Plății 1", "Cluj-Napoca", null, null, "România", null, null))
                .bedrooms(1)
                .bathrooms(1)
                .maxGuests(2)
                .basePricePerNight(new BigDecimal("125.00"))
                .build());
        admin = userRepository.save(User.builder()
                .email("admin-paid-" + System.nanoTime() + "@bhstays.ro")
                .passwordHash("irrelevant-for-this-test")
                .firstName("Admin")
                .lastName("Test")
                .role(Role.SUPER_ADMIN)
                .status(UserStatus.ACTIVE)
                .build());
        clearInvocations(emailService);
    }

    // ------------------------------------------------------------------
    // fixtures
    // ------------------------------------------------------------------

    /** A held direct booking with an open card payment, as checkout leaves it. */
    private Payment heldBookingWithOpenCheckout(LocalDate checkIn, LocalDate checkOut) {
        Reservation reservation = reservationRepository.saveAndFlush(Reservation.builder()
                .property(property)
                .guestFirstName("Ana")
                .guestLastName("Popescu")
                .guestEmail("ana@example.com")
                .guestPhone("0700000000")
                .checkInDate(checkIn)
                .checkOutDate(checkOut)
                .numberOfGuests(2)
                .status(ReservationStatus.PENDING)
                .source(ReservationSource.DIRECT)
                .totalAmount(new BigDecimal("500.00"))
                .currency("RON")
                .managementToken(secureTokenGenerator.generateRawToken())
                .holdExpiresAt(Instant.now().plus(35, ChronoUnit.MINUTES))
                .build());
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

    private String sessionEvent(String eventId, String type, Payment payment, String paymentStatus, long amountTotal,
                                String currency) {
        return """
                {
                  "id": "%s",
                  "object": "event",
                  "type": "%s",
                  "data": { "object": {
                    "id": "%s",
                    "object": "checkout.session",
                    "payment_intent": "pi_%s",
                    "payment_status": "%s",
                    "amount_total": %d,
                    "currency": "%s",
                    "metadata": { "reservationId": "%s" }
                  }}
                }
                """.formatted(eventId, type, payment.getCheckoutSessionId(), eventId, paymentStatus, amountTotal,
                currency, payment.getReservation().getId());
    }

    private String paidEvent(String eventId, Payment payment) {
        return sessionEvent(eventId, "checkout.session.completed", payment, "paid", 50000, "ron");
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

    private static String newEventId() {
        return "evt_" + UUID.randomUUID().toString().replace("-", "");
    }

    private Reservation reload(Payment payment) {
        return reservationRepository.findById(payment.getReservation().getId()).orElseThrow();
    }

    private PaymentStatus paymentStatus(Payment payment) {
        return paymentRepository.findById(payment.getId()).orElseThrow().getStatus();
    }

    private long paidBookingNotifications(Reservation reservation) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(admin.getId(), PageRequest.of(0, 50))
                .stream()
                .filter(n -> n.getType() == NotificationType.NEW_PAID_BOOKING)
                .filter(n -> n.getLinkPath().equals("/dashboard/reservations/" + reservation.getId()))
                .count();
    }

    private long auditRows(Reservation reservation, AuditAction action) {
        return auditLogRepository.findAll().stream()
                .filter(a -> reservation.getId().toString().equals(a.getEntityId()))
                .filter(a -> action.name().equals(a.getAction()))
                .count();
    }

    private void verifyConfirmationEmailSent(int times) {
        verify(emailService, times(times)).sendPaymentConfirmedEmail(
                any(), any(), any(), any(), any(), any(), any(), any());
    }

    // ------------------------------------------------------------------
    // tests
    // ------------------------------------------------------------------

    @Test
    void aValidSignedPaidWebhookCapturesThePaymentAndConfirmsTheBooking_exactlyOnce() {
        Payment payment = heldBookingWithOpenCheckout(LocalDate.of(2031, 1, 10), LocalDate.of(2031, 1, 14));
        String eventId = newEventId();
        String payload = paidEvent(eventId, payment);

        stripeWebhookService.handle(payload, sign(payload));

        Reservation reservation = reload(payment);
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(reservation.getHoldExpiresAt()).isNull();
        assertThat(paymentStatus(payment)).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(paidBookingNotifications(reservation)).isEqualTo(1);
        assertThat(auditRows(reservation, AuditAction.BOOKING_PAYMENT_CONFIRMED)).isEqualTo(1);
        verifyConfirmationEmailSent(1);
        assertThat(paymentWebhookEventRepository.findByProviderAndExternalEventId(PaymentProvider.STRIPE, eventId))
                .hasValueSatisfying(inbox -> {
                    assertThat(inbox.getProcessedAt()).isNotNull();
                    assertThat(inbox.getProcessingError()).isNull();
                    assertThat(inbox.getPayload()).doesNotContain("ana@example.com");
                });

        // Stripe redelivers the same event, then sends a second event about the same payment.
        stripeWebhookService.handle(payload, sign(payload));
        String asyncPayload = sessionEvent(newEventId(), "checkout.session.async_payment_succeeded", payment,
                "paid", 50000, "ron");
        stripeWebhookService.handle(asyncPayload, sign(asyncPayload));

        assertThat(paidBookingNotifications(reservation)).isEqualTo(1);
        assertThat(auditRows(reservation, AuditAction.BOOKING_PAYMENT_CONFIRMED)).isEqualTo(1);
        assertThat(paymentRepository.findById(payment.getId()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.SUCCEEDED);
        verifyConfirmationEmailSent(1);
    }

    @Test
    void concurrentDuplicateDeliveriesOfOneEventHaveTheirEffectsOnlyOnce() throws Exception {
        Payment payment = heldBookingWithOpenCheckout(LocalDate.of(2031, 2, 10), LocalDate.of(2031, 2, 14));
        String payload = paidEvent(newEventId(), payment);
        String signature = sign(payload);

        runConcurrently(4, () -> {
            stripeWebhookService.handle(payload, signature);
            return null;
        });

        Reservation reservation = reload(payment);
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(paidBookingNotifications(reservation)).isEqualTo(1);
        verifyConfirmationEmailSent(1);
    }

    @Test
    void anInvalidSignatureIsRejectedAndNothingIsRecorded() {
        Payment payment = heldBookingWithOpenCheckout(LocalDate.of(2031, 3, 10), LocalDate.of(2031, 3, 14));
        String eventId = newEventId();
        String payload = paidEvent(eventId, payment);
        String forged = "t=" + Instant.now().getEpochSecond() + ",v1=" + "0".repeat(64);

        assertThatThrownBy(() -> stripeWebhookService.handle(payload, forged))
                .isInstanceOf(StripeGateway.StripeSignatureException.class);
        assertThatThrownBy(() -> stripeWebhookService.handle(payload, null))
                .isInstanceOf(StripeGateway.StripeSignatureException.class);

        assertThat(reload(payment).getStatus()).isEqualTo(ReservationStatus.PENDING);
        assertThat(paymentStatus(payment)).isEqualTo(PaymentStatus.PENDING);
        assertThat(paymentWebhookEventRepository.findByProviderAndExternalEventId(PaymentProvider.STRIPE, eventId))
                .isEmpty();
        verifyConfirmationEmailSent(0);
    }

    @Test
    void aDifferentAmountOrCurrencyIsRejected() {
        Payment payment = heldBookingWithOpenCheckout(LocalDate.of(2031, 4, 10), LocalDate.of(2031, 4, 14));
        String wrongAmountId = newEventId();
        String wrongAmount = sessionEvent(wrongAmountId, "checkout.session.completed", payment, "paid", 100, "ron");
        String wrongCurrency = sessionEvent(newEventId(), "checkout.session.completed", payment, "paid", 50000, "eur");

        stripeWebhookService.handle(wrongAmount, sign(wrongAmount));
        stripeWebhookService.handle(wrongCurrency, sign(wrongCurrency));

        Reservation reservation = reload(payment);
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PENDING);
        assertThat(paymentStatus(payment)).isEqualTo(PaymentStatus.PENDING);
        assertThat(paidBookingNotifications(reservation)).isZero();
        assertThat(auditRows(reservation, AuditAction.BOOKING_PAYMENT_REJECTED)).isEqualTo(2);
        assertThat(paymentWebhookEventRepository.findByProviderAndExternalEventId(PaymentProvider.STRIPE, wrongAmountId))
                .hasValueSatisfying(inbox -> assertThat(inbox.getProcessingError()).contains("amount"));
        verifyConfirmationEmailSent(0);
    }

    @Test
    void aCompletedButUnpaidCheckoutDoesNotConfirm() {
        Payment payment = heldBookingWithOpenCheckout(LocalDate.of(2031, 5, 10), LocalDate.of(2031, 5, 14));
        String payload = sessionEvent(newEventId(), "checkout.session.completed", payment, "unpaid", 50000, "ron");

        stripeWebhookService.handle(payload, sign(payload));

        assertThat(reload(payment).getStatus()).isEqualTo(ReservationStatus.PENDING);
        assertThat(paymentStatus(payment)).isEqualTo(PaymentStatus.PENDING);
        verifyConfirmationEmailSent(0);
    }

    @Test
    void anExpiredSessionReleasesTheHoldSoTheDatesCanBeBookedAgain() {
        LocalDate checkIn = LocalDate.of(2031, 6, 10);
        LocalDate checkOut = LocalDate.of(2031, 6, 14);
        Payment payment = heldBookingWithOpenCheckout(checkIn, checkOut);
        String payload = sessionEvent(newEventId(), "checkout.session.expired", payment, "unpaid", 50000, "ron");

        stripeWebhookService.handle(payload, sign(payload));

        Reservation released = reload(payment);
        assertThat(released.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(paymentStatus(payment)).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(auditRows(released, AuditAction.BOOKING_HOLD_RELEASED)).isEqualTo(1);
        verifyConfirmationEmailSent(0);

        Reservation rebooked = reservationService.createGuestBooking(property.getId(), "Ion", "Ionescu",
                "ion@example.com", "0711111111", checkIn, checkOut, 2, null, null);
        assertThat(rebooked.getStatus()).isEqualTo(ReservationStatus.PENDING);
    }

    @Test
    void anExpiredHoldIsReleasedByTheExpiryJobTogetherWithItsOpenCardPayment() {
        Payment payment = heldBookingWithOpenCheckout(LocalDate.of(2031, 7, 10), LocalDate.of(2031, 7, 14));
        Reservation held = reload(payment);
        held.setHoldExpiresAt(Instant.now().minusSeconds(5));
        reservationRepository.saveAndFlush(held);

        reservationService.expireStaleHolds();

        assertThat(reload(payment).getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(paymentStatus(payment)).isEqualTo(PaymentStatus.CANCELLED);
    }

    @Test
    void twoConcurrentBookingsForTheSameDates_atMostOneGoesThrough() throws Exception {
        LocalDate checkIn = LocalDate.of(2031, 8, 10);
        LocalDate checkOut = LocalDate.of(2031, 8, 14);
        AtomicInteger attempt = new AtomicInteger();

        List<Object> outcomes = runConcurrently(2, () -> reservationService.createGuestBooking(
                property.getId(), "Guest", "No" + attempt.incrementAndGet(), "guest@example.com",
                "0700000000", checkIn, checkOut, 2, null, null));

        long succeeded = outcomes.stream().filter(Reservation.class::isInstance).count();
        assertThat(succeeded).isEqualTo(1);
        assertThat(reservationRepository.findOverlapping(property.getId(), checkIn, checkOut, null,
                ReservationStatus.NON_BLOCKING)).hasSize(1);
    }

    // ------------------------------------------------------------------
    // public booking is card-only
    // ------------------------------------------------------------------

    private String publicBookingBody(LocalDate checkIn, LocalDate checkOut, String paymentMethod) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("propertyId", property.getId().toString());
        body.put("guestFirstName", "Maria");
        body.put("guestLastName", "Pop");
        body.put("guestEmail", "maria@example.com");
        body.put("guestPhone", "0722222222");
        body.put("checkInDate", checkIn.toString());
        body.put("checkOutDate", checkOut.toString());
        body.put("numberOfGuests", 2);
        if (paymentMethod != null) {
            body.put("paymentMethod", paymentMethod);
        }
        return objectMapper.writeValueAsString(body);
    }

    private List<Reservation> reservationsOfProperty() {
        return reservationRepository.findAll().stream()
                .filter(r -> r.getProperty().getId().equals(property.getId()))
                .toList();
    }

    @Test
    void aPublicBookingHoldsTheDatesAndSendsTheGuestStraightToStripeCheckout() throws Exception {
        doReturn(new StripeGateway.StripeCheckoutSession("cs_public_" + UUID.randomUUID(),
                "https://checkout.stripe.com/c/pay/cs_public"))
                .when(stripeGateway).createCheckoutSession(any());

        mockMvc.perform(post("/api/v1/public/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(publicBookingBody(LocalDate.of(2031, 9, 10), LocalDate.of(2031, 9, 14), "ONLINE_CARD")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.checkoutUrl").value("https://checkout.stripe.com/c/pay/cs_public"))
                .andExpect(jsonPath("$.data.reservation.status").value("PENDING"));

        List<Reservation> held = reservationsOfProperty();
        assertThat(held).hasSize(1);
        Reservation reservation = held.get(0);
        List<Payment> payments = paymentRepository.findByReservationIdOrderByCreatedAtDesc(reservation.getId());
        assertThat(payments).singleElement().satisfies(p -> {
            assertThat(p.getProvider()).isEqualTo(PaymentProvider.STRIPE);
            assertThat(p.getMethod()).isEqualTo(PaymentMethod.ONLINE_CARD);
            assertThat(p.getStatus()).isEqualTo(PaymentStatus.PENDING);
        });
        // Nothing is confirmed, announced or emailed until Stripe says it is paid.
        assertThat(paidBookingNotifications(reservation)).isZero();
        verifyConfirmationEmailSent(0);
        verify(emailService, never()).sendBookingConfirmationEmail(any(), any(), any(), any(), any(), any());
    }

    @Test
    void aPublicRequestForAManualPaymentMethodIsRejectedAndHoldsNothing() throws Exception {
        for (String method : List.of("BANK_TRANSFER", "ON_ARRIVAL", "CASH")) {
            mockMvc.perform(post("/api/v1/public/reservations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(publicBookingBody(LocalDate.of(2031, 9, 20), LocalDate.of(2031, 9, 24), method)))
                    .andExpect(status().isBadRequest());
        }

        assertThat(reservationsOfProperty()).isEmpty();
        verify(stripeGateway, never()).createCheckoutSession(any());
    }

    @Test
    void whenStripeCannotOpenCheckout_noHoldIsLeftAndTheDatesStayBookable() throws Exception {
        doThrow(new StripeGateway.StripePaymentException("Stripe unavailable", null))
                .when(stripeGateway).createCheckoutSession(any());

        mockMvc.perform(post("/api/v1/public/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(publicBookingBody(LocalDate.of(2031, 12, 10), LocalDate.of(2031, 12, 14), null)))
                .andExpect(status().isServiceUnavailable());

        assertThat(reservationsOfProperty()).isEmpty();
        assertThat(reservationService.availability(property.getId(), LocalDate.of(2031, 12, 10),
                LocalDate.of(2031, 12, 14)).available()).isTrue();
    }

    @Test
    void anAdministratorCanStillRecordAManualPayment() {
        Reservation adminBooking = reservationRepository.saveAndFlush(Reservation.builder()
                .property(property)
                .guestFirstName("Ion")
                .guestLastName("Admin")
                .checkInDate(LocalDate.of(2032, 1, 10))
                .checkOutDate(LocalDate.of(2032, 1, 14))
                .numberOfGuests(2)
                .status(ReservationStatus.PENDING)
                .source(ReservationSource.DIRECT)
                .totalAmount(new BigDecimal("500.00"))
                .currency("RON")
                .build());

        var recorded = paymentService.recordManualPayment(new ManualPaymentCreateRequest(
                adminBooking.getId(), new BigDecimal("500.00"), PaymentMethod.BANK_TRANSFER, "Transfer primit"));

        // Existing admin behaviour, unchanged by the card-only public flow.
        assertThat(recorded.provider()).isEqualTo(PaymentProvider.MANUAL);
        assertThat(recorded.method()).isEqualTo(PaymentMethod.BANK_TRANSFER);
        assertThat(recorded.status()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(reservationRepository.findById(adminBooking.getId()).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.CONFIRMED);
        // Not a card payment: no paid-booking notice and no card confirmation email.
        assertThat(paidBookingNotifications(adminBooking)).isZero();
        verifyConfirmationEmailSent(0);
    }

    @Test
    void aStripeEventForAManualPaymentNeverConfirmsIt() {
        Payment payment = heldBookingWithOpenCheckout(LocalDate.of(2031, 10, 10), LocalDate.of(2031, 10, 14));
        payment.setProvider(PaymentProvider.MANUAL);
        payment.setMethod(PaymentMethod.BANK_TRANSFER);
        paymentRepository.saveAndFlush(payment);
        String payload = paidEvent(newEventId(), payment);

        stripeWebhookService.handle(payload, sign(payload));

        assertThat(reload(payment).getStatus()).isEqualTo(ReservationStatus.PENDING);
        assertThat(paymentStatus(payment)).isEqualTo(PaymentStatus.PENDING);
        verifyConfirmationEmailSent(0);
    }

    @Test
    void returningToTheSuccessPageCannotConfirmTheBooking() {
        Payment payment = heldBookingWithOpenCheckout(LocalDate.of(2031, 11, 10), LocalDate.of(2031, 11, 14));
        String token = reload(payment).getManagementToken();

        // What /plata/succes does: read the booking back by its token.
        var seen = publicReservationService.getByToken(token);
        publicReservationService.getByToken(token);

        assertThat(seen.status()).isEqualTo(ReservationStatus.PENDING);
        assertThat(seen.cardPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(reload(payment).getStatus()).isEqualTo(ReservationStatus.PENDING);
        assertThat(paymentStatus(payment)).isEqualTo(PaymentStatus.PENDING);
        verify(emailService, never()).sendPaymentConfirmedEmail(
                any(), any(), any(), any(), any(), any(), eq("RON"), any());
    }

    /** Starts {@code n} copies of a task at the same instant; returns each result or the exception it threw. */
    private static List<Object> runConcurrently(int n, Callable<Object> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    try {
                        return task.call();
                    } catch (Exception ex) {
                        return ex;
                    }
                }));
            }
            start.countDown();
            List<Object> results = new ArrayList<>();
            for (Future<Object> future : futures) {
                results.add(future.get(60, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }
}
