package com.bhstays.pms.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bhstays.pms.common.exception.ConflictException;
import com.bhstays.pms.domain.Address;
import com.bhstays.pms.domain.DynamicPricingConfig;
import com.bhstays.pms.domain.OwnerStatement;
import com.bhstays.pms.domain.Payment;
import com.bhstays.pms.domain.Property;
import com.bhstays.pms.domain.PropertyStatus;
import com.bhstays.pms.domain.PropertyType;
import com.bhstays.pms.domain.Reservation;
import com.bhstays.pms.domain.ReservationSource;
import com.bhstays.pms.domain.ReservationStatus;
import com.bhstays.pms.domain.Role;
import com.bhstays.pms.domain.SeasonalRate;
import com.bhstays.pms.domain.User;
import com.bhstays.pms.domain.UserStatus;
import com.bhstays.pms.dto.dashboard.CurrencyAmountResponse;
import com.bhstays.pms.dto.dashboard.DashboardSummaryResponse;
import com.bhstays.pms.dto.owner.OwnerDashboardSummaryResponse;
import com.bhstays.pms.dto.owner.OwnerPropertyResponse;
import com.bhstays.pms.dto.owner.OwnerRevenueLine;
import com.bhstays.pms.dto.ownerstatement.OwnerStatementResponse;
import com.bhstays.pms.dto.report.CommissionSummaryCurrencyTotals;
import com.bhstays.pms.dto.report.FinancialReportCurrencyTotals;
import com.bhstays.pms.dto.report.FinancialReportRowResponse;
import com.bhstays.pms.dto.report.PropertyCommissionCurrencyResponse;
import com.bhstays.pms.repository.DynamicPricingConfigRepository;
import com.bhstays.pms.repository.PaymentRepository;
import com.bhstays.pms.repository.PaymentTransactionRepository;
import com.bhstays.pms.repository.PropertyRepository;
import com.bhstays.pms.repository.ReservationRepository;
import com.bhstays.pms.repository.SeasonalRateRepository;
import com.bhstays.pms.repository.UserRepository;
import com.bhstays.pms.security.UserPrincipal;
import com.bhstays.pms.service.DashboardService;
import com.bhstays.pms.service.EmailService;
import com.bhstays.pms.service.FinancialReportService;
import com.bhstays.pms.service.OwnerService;
import com.bhstays.pms.service.OwnerStatementService;
import com.bhstays.pms.service.PropertyCommissionReportService;
import com.bhstays.pms.service.PropertyService;
import com.bhstays.pms.service.ReservationService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The same money movements must produce the same figures in every
 * financial view - property report, dashboard, /finance, owner statements,
 * owner dashboard and owner property cards - against a real Postgres,
 * including after the property's percent changes and after a refund in a
 * later month. Each test uses its own owner, properties and transaction
 * year, because the database is shared with the other integration tests.
 */
@AutoConfigureMockMvc
class FinancialViewsConsistencyIntegrationTest extends AbstractIntegrationTest {

    private static final LocalDate JAN_1 = LocalDate.of(2048, 1, 1);
    private static final LocalDate JAN_31 = LocalDate.of(2048, 1, 31);
    private static final LocalDate FEB_1 = LocalDate.of(2048, 2, 1);
    private static final LocalDate FEB_29 = LocalDate.of(2048, 2, 29);

    @Autowired private PropertyCommissionReportService commissionReportService;
    @Autowired private FinancialReportService financialReportService;
    @Autowired private DashboardService dashboardService;
    @Autowired private OwnerStatementService ownerStatementService;
    @Autowired private OwnerService ownerService;
    @Autowired private PropertyService propertyService;
    @Autowired private ReservationService reservationService;
    @Autowired private PropertyRepository propertyRepository;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private PaymentTransactionRepository paymentTransactionRepository;
    @Autowired private SeasonalRateRepository seasonalRateRepository;
    @Autowired private DynamicPricingConfigRepository dynamicPricingConfigRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private MockMvc mockMvc;

    @MockitoBean private EmailService emailService;

    // ------------------------------------------------------------------
    // fixtures
    // ------------------------------------------------------------------

    private LedgerFixtures ledger() {
        return new LedgerFixtures(paymentRepository, paymentTransactionRepository);
    }

    private User account(Role role) {
        return userRepository.save(User.builder()
                .email(role.name().toLowerCase() + "-views-" + System.nanoTime() + "@bhstays.ro")
                .passwordHash("irrelevant-for-this-test")
                .firstName("Test")
                .lastName(role.name())
                .role(role)
                .status(UserStatus.ACTIVE)
                .mfaEnabled(true)
                .build());
    }

    private Property property(User owner, String commissionPercent) {
        return propertyRepository.saveAndFlush(Property.builder()
                .name("Apartament " + UUID.randomUUID())
                .propertyType(PropertyType.APARTMENT)
                .status(PropertyStatus.ACTIVE)
                .address(new Address("Str. Consecvenței 1", "Brașov", null, null, "România", null, null))
                .bedrooms(2)
                .bathrooms(1)
                .maxGuests(4)
                .basePricePerNight(new BigDecimal("100.00"))
                .weekendPricePerNight(new BigDecimal("150.00"))
                .cleaningFee(new BigDecimal("80.00"))
                .extraGuestFee(new BigDecimal("20.00"))
                .baseGuestsIncluded(2)
                .weeklyDiscountPercent(new BigDecimal("10.00"))
                .owner(owner)
                .commissionPercent(commissionPercent != null ? new BigDecimal(commissionPercent) : null)
                .build());
    }

    /** A staff-recorded reservation; the percent snapshot is copied from the property on insert. */
    private Reservation direct(Property property, LocalDate checkIn, String currency, String total,
                               String accommodation, String cleaning, String lateCheckout, String tax, String addon) {
        return reservationRepository.saveAndFlush(Reservation.builder()
                .property(propertyRepository.findById(property.getId()).orElseThrow())
                .guestFirstName("Ana")
                .guestLastName("Pop")
                .guestEmail("ana@example.com")
                .checkInDate(checkIn)
                .checkOutDate(checkIn.plusDays(2))
                .numberOfGuests(2)
                .status(ReservationStatus.CONFIRMED)
                .source(ReservationSource.DIRECT)
                .totalAmount(new BigDecimal(total))
                .currency(currency)
                .accommodationAmount(new BigDecimal(accommodation))
                .cleaningFeeAmount(new BigDecimal(cleaning))
                .extraGuestFeeAmount(BigDecimal.ZERO)
                .lateCheckoutFeeAmount(new BigDecimal(lateCheckout))
                .taxAmount(new BigDecimal(tax))
                .addonAmount(new BigDecimal(addon))
                .build());
    }

    /** What a reservation created before the snapshots existed looks like: no breakdown, no percent. */
    private Reservation historical(Property property, LocalDate checkIn, String total) {
        Reservation reservation = direct(property, checkIn, "RON", total, total, "0", "0", "0", "0");
        jdbcTemplate.update("update reservations set accommodation_amount = null, cleaning_fee_amount = null, "
                + "extra_guest_fee_amount = null, late_checkout_fee_amount = null, tax_amount = null, "
                + "addon_amount = null, management_commission_percent_snapshot = null where id = ?", reservation.getId());
        return reservation;
    }

    private BigDecimal snapshotOf(Reservation reservation) {
        return reservationRepository.findById(reservation.getId()).orElseThrow().getManagementCommissionPercentSnapshot();
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal percentOf(BigDecimal base, String percent) {
        return base.multiply(new BigDecimal(percent)).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    private static PropertyCommissionCurrencyResponse lineOf(List<PropertyCommissionCurrencyResponse> lines,
                                                             String currency) {
        return lines.stream().filter(l -> l.currency().equals(currency)).findFirst().orElseThrow();
    }

    // ------------------------------------------------------------------
    // the same money, everywhere
    // ------------------------------------------------------------------

    @Test
    void sameFiguresEverywhere_afterAPercentChangeAndARefundInALaterMonth() {
        User admin = account(Role.ADMINISTRATOR);
        User owner = account(Role.OWNER);
        Property property = property(owner, "20.00");
        LedgerFixtures ledger = ledger();

        // --- R1: a real booking through the pricing engine at 20%: weekend + seasonal + dynamic + weekly discount
        LocalDate checkIn = LocalDate.of(2047, 3, 2);
        LocalDate seasonalNight = checkIn.plusDays(3);
        seasonalRateRepository.saveAndFlush(SeasonalRate.builder()
                .property(property).label("Festival").startDate(seasonalNight).endDate(seasonalNight)
                .pricePerNight(new BigDecimal("200.00")).build());
        dynamicPricingConfigRepository.saveAndFlush(DynamicPricingConfig.builder()
                .property(property).enabled(true).build());
        Reservation booked = reservationService.createGuestBooking(property.getId(), "Ion", "Popescu",
                "ion@example.com", "0700000000", checkIn, checkIn.plusDays(7), 3, null, null);

        BigDecimal subtotal = BigDecimal.ZERO;
        for (LocalDate night = checkIn; night.isBefore(checkIn.plusDays(7)); night = night.plusDays(1)) {
            BigDecimal rate = night.equals(seasonalNight) ? new BigDecimal("200.00")
                    : (night.getDayOfWeek() == DayOfWeek.FRIDAY || night.getDayOfWeek() == DayOfWeek.SATURDAY)
                            ? new BigDecimal("150.00") : new BigDecimal("100.00");
            subtotal = subtotal.add(rate.multiply(new BigDecimal("0.9000")).setScale(2, RoundingMode.HALF_UP));
        }
        BigDecimal accommodation = subtotal.subtract(percentOf(subtotal, "10.00"));
        BigDecimal total = accommodation.add(money("140.00")).add(money("80.00"));   // + extra guest + cleaning
        Reservation r1 = reservationRepository.findById(booked.getId()).orElseThrow();
        assertThat(r1.getTotalAmount()).isEqualByComparingTo(total);
        assertThat(r1.getAccommodationAmount()).isEqualByComparingTo(accommodation);
        assertThat(r1.getAccommodationAmount().add(r1.getCleaningFeeAmount()).add(r1.getExtraGuestFeeAmount())
                .add(r1.getLateCheckoutFeeAmount()).add(r1.getTaxAmount()).add(r1.getAddonAmount()))
                .isEqualByComparingTo(r1.getTotalAmount());
        assertThat(snapshotOf(r1)).isEqualByComparingTo("20.00");

        Payment r1Payment = ledger.capture(r1, total.toPlainString(), Instant.parse("2048-01-10T10:00:00Z"));

        // --- the property's percent changes: only reservations created from now on are affected
        propertyService.updateCommission(property.getId(), new BigDecimal("25"));
        assertThat(snapshotOf(r1)).isEqualByComparingTo("20.00");

        // R2: created after the change -> 25%; fully paid, plus a separate 50 late-checkout payment
        Reservation r2 = direct(property, LocalDate.of(2047, 4, 10), "RON", "500.00", "400.00", "100.00", "0", "0", "0");
        assertThat(snapshotOf(r2)).isEqualByComparingTo("25.00");
        ledger.capture(r2, "500.00", Instant.parse("2048-01-20T10:00:00Z"));
        ledger.capture(r2, "50.00", Instant.parse("2048-01-21T10:00:00Z"));

        // R3: historical, no verifiable snapshot -> flagged, never commissioned
        Reservation r3 = historical(property, LocalDate.of(2047, 5, 10), "250.00");
        assertThat(snapshotOf(r3)).isNull();
        ledger.capture(r3, "250.00", Instant.parse("2048-01-25T10:00:00Z"));

        // R4: EUR, 300 = 200 accommodation + 30 cleaning + 20 late checkout + 30 taxes + 20 add-ons, at 25%
        Reservation r4 = direct(property, LocalDate.of(2047, 6, 10), "EUR", "300.00", "200.00", "30.00",
                "20.00", "30.00", "20.00");
        ledger.capture(r4, "300.00", Instant.parse("2048-01-15T10:00:00Z"));

        // ---------------- January (independently derived) ----------------
        BigDecimal r1Commission = percentOf(accommodation, "20.00");
        BigDecimal janRonCaptured = total.add(money("550.00")).add(money("250.00"));
        BigDecimal janRonBase = accommodation.add(money("400.00"));
        BigDecimal janRonBhStays = r1Commission.add(money("100.00"));          // 20% of R1 + 25% of 400
        BigDecimal janRonOwner = janRonCaptured.subtract(janRonBhStays);

        List<PropertyCommissionCurrencyResponse> january =
                commissionReportService.propertyReport(property.getId(), JAN_1, JAN_31).currencies();
        PropertyCommissionCurrencyResponse janRon = lineOf(january, "RON");
        PropertyCommissionCurrencyResponse janEur = lineOf(january, "EUR");
        assertThat(janRon.capturedTotal()).isEqualByComparingTo(janRonCaptured);
        assertThat(janRon.refundedTotal()).isEqualByComparingTo("0.00");
        assertThat(janRon.commissionableBase()).isEqualByComparingTo(janRonBase);
        assertThat(janRon.commissionPercents()).containsExactly(new BigDecimal("20.00"), new BigDecimal("25.00"));
        assertThat(janRon.bhStaysRevenue()).isEqualByComparingTo(janRonBhStays);
        assertThat(janRon.ownerAmount()).isEqualByComparingTo(janRonOwner);
        assertThat(janRon.unallocatedNetRevenue()).isEqualByComparingTo("250.00");
        assertThat(janRon.unallocatedReservationCount()).isEqualTo(1);
        assertThat(janEur.commissionableBase()).isEqualByComparingTo("200.00");
        assertThat(janEur.bhStaysRevenue()).isEqualByComparingTo("50.00");
        assertThat(janEur.ownerAmount()).isEqualByComparingTo("250.00");

        OwnerStatementResponse janStatementRon = assertEverywhere(owner, admin, property, JAN_1, JAN_31, janRon, janEur)
                .stream().filter(s -> s.currency().equals("RON")).findFirst().orElseThrow();

        // ---------------- February: a 100 refund of R1 is an adjustment of February ----------------
        ledger.refund(r1Payment, "100.00", Instant.parse("2048-02-10T10:00:00Z"));
        BigDecimal r1BaseAfter = accommodation.multiply(total.subtract(money("100.00")))
                .divide(total, 2, RoundingMode.HALF_UP);
        BigDecimal febBase = r1BaseAfter.subtract(accommodation);
        BigDecimal febBhStays = percentOf(r1BaseAfter, "20.00").subtract(r1Commission);

        List<PropertyCommissionCurrencyResponse> february =
                commissionReportService.propertyReport(property.getId(), FEB_1, FEB_29).currencies();
        assertThat(february).extracting(PropertyCommissionCurrencyResponse::currency).containsExactly("RON");
        PropertyCommissionCurrencyResponse febRon = february.get(0);
        assertThat(febRon.capturedTotal()).isEqualByComparingTo("0.00");
        assertThat(febRon.refundedTotal()).isEqualByComparingTo("100.00");
        assertThat(febRon.netRevenue()).isEqualByComparingTo("-100.00");
        assertThat(febRon.commissionableBase()).isEqualByComparingTo(febBase);
        assertThat(febRon.bhStaysRevenue()).isEqualByComparingTo(febBhStays);
        assertThat(febRon.ownerAmount()).isEqualByComparingTo(money("-100.00").subtract(febBhStays));
        assertEverywhere(owner, admin, property, FEB_1, FEB_29, febRon, null);

        // January did not change - neither the report nor the statement already issued
        PropertyCommissionCurrencyResponse janAgain = lineOf(
                commissionReportService.propertyReport(property.getId(), JAN_1, JAN_31).currencies(), "RON");
        assertThat(janAgain).isEqualTo(janRon);
        OwnerStatementResponse janReread = ownerStatementService.get(janStatementRon.id());
        assertThat(janReread.netPayout()).isEqualByComparingTo(janStatementRon.netPayout());
        assertThat(janReread.commissionAmount()).isEqualByComparingTo(janStatementRon.commissionAmount());
        assertThat(janReread.refundedTotal()).isEqualByComparingTo("0.00");

        // January + February add up exactly to the two months together
        PropertyCommissionCurrencyResponse both = lineOf(
                commissionReportService.propertyReport(property.getId(), JAN_1, FEB_29).currencies(), "RON");
        assertThat(janRon.bhStaysRevenue().add(febRon.bhStaysRevenue())).isEqualByComparingTo(both.bhStaysRevenue());
        assertThat(janRon.ownerAmount().add(febRon.ownerAmount())).isEqualByComparingTo(both.ownerAmount());
        assertThat(janRon.commissionableBase().add(febRon.commissionableBase()))
                .isEqualByComparingTo(both.commissionableBase());

        // --- owner portal (all time) = all-time report; RON + EUR: the deprecated flat fields are not filled
        List<PropertyCommissionCurrencyResponse> allTime =
                commissionReportService.propertyReport(property.getId(), null, null).currencies();
        OwnerDashboardSummaryResponse dashboard = ownerService.getMyDashboardSummary(owner.getId());
        assertThat(dashboard.revenueByCurrency()).extracting(OwnerRevenueLine::currency).containsExactly("EUR", "RON");
        for (OwnerRevenueLine line : dashboard.revenueByCurrency()) {
            PropertyCommissionCurrencyResponse expected = lineOf(allTime, line.currency());
            assertThat(line.netRevenue()).isEqualByComparingTo(expected.netRevenue());
            assertThat(line.bhStaysCommission()).isEqualByComparingTo(expected.bhStaysRevenue());
            assertThat(line.ownerAmount()).isEqualByComparingTo(expected.ownerAmount());
        }
        assertThat(dashboard.currency()).isNull();
        assertThat(dashboard.grossRevenue()).isNull();
        assertThat(dashboard.commissionAmount()).isNull();
        assertThat(dashboard.netRevenue()).isNull();
        OwnerPropertyResponse card = ownerService.getMyProperty(owner.getId(), property.getId());
        assertThat(card.revenueByCurrency()).hasSize(2);
        assertThat(card.currency()).isNull();
        assertThat(card.grossRevenue()).isNull();
        assertThat(card.netRevenue()).isNull();
        assertThat(lineOf(allTime, "RON").ownerAmount())
                .isEqualByComparingTo(card.revenueByCurrency().get(1).ownerAmount());
    }

    /**
     * Property report line = dashboard = /finance (property and portfolio) = owner statement for the period.
     * Returns the statements it issued for the period.
     */
    private List<OwnerStatementResponse> assertEverywhere(User owner, User admin, Property property, LocalDate from, LocalDate to,
                                  PropertyCommissionCurrencyResponse ron, PropertyCommissionCurrencyResponse eur) {
        // dashboard (this test is alone in its transaction year)
        List<CommissionSummaryCurrencyTotals> dashboard = commissionReportService.summary(from, to).totals();
        assertThat(dashboard).hasSize(eur != null ? 2 : 1);
        for (CommissionSummaryCurrencyTotals totals : dashboard) {
            PropertyCommissionCurrencyResponse line = totals.currency().equals("RON") ? ron : eur;
            assertThat(totals.capturedTotal()).isEqualByComparingTo(line.capturedTotal());
            assertThat(totals.refundedTotal()).isEqualByComparingTo(line.refundedTotal());
            assertThat(totals.propertiesNetRevenue()).isEqualByComparingTo(line.netRevenue());
            assertThat(totals.bhStaysRevenue()).isEqualByComparingTo(line.bhStaysRevenue());
            assertThat(totals.ownersAmount()).isEqualByComparingTo(line.ownerAmount());
            assertThat(totals.unallocatedReservationCount()).isEqualTo(line.unallocatedReservationCount());
        }

        // /finance, for the property and for the portfolio
        for (FinancialReportRowResponse row : financialReportService.summary(property.getId(), from, to).rows()) {
            PropertyCommissionCurrencyResponse line = row.currency().equals("RON") ? ron : eur;
            assertThat(row.capturedTotal()).isEqualByComparingTo(line.capturedTotal());
            assertThat(row.refundedTotal()).isEqualByComparingTo(line.refundedTotal());
            assertThat(row.netRevenue()).isEqualByComparingTo(line.netRevenue());
            assertThat(row.commissionableBase()).isEqualByComparingTo(line.commissionableBase());
            assertThat(row.commissionPercents()).isEqualTo(line.commissionPercents());
            assertThat(row.bhStaysRevenue()).isEqualByComparingTo(line.bhStaysRevenue());
            assertThat(row.ownerAmount()).isEqualByComparingTo(line.ownerAmount());
        }
        List<FinancialReportCurrencyTotals> financeTotals = financialReportService.summary(null, from, to).totals();
        assertThat(financeTotals).extracting(FinancialReportCurrencyTotals::revenue).containsExactlyElementsOf(dashboard);

        // owner statement of the period - one per currency
        List<OwnerStatementResponse> statements = ownerStatementService.generate(owner.getId(), from, to, admin);
        assertThat(statements).hasSize(eur != null ? 2 : 1);
        for (OwnerStatementResponse statement : statements) {
            PropertyCommissionCurrencyResponse line = statement.currency().equals("RON") ? ron : eur;
            assertThat(statement.calculationMethod()).isEqualTo(OwnerStatement.CALCULATION_CAPTURED_ACCOMMODATION);
            assertThat(statement.capturedTotal()).isEqualByComparingTo(line.capturedTotal());
            assertThat(statement.refundedTotal()).isEqualByComparingTo(line.refundedTotal());
            assertThat(statement.grossRevenue()).isEqualByComparingTo(line.netRevenue());
            assertThat(statement.commissionableBase()).isEqualByComparingTo(line.commissionableBase());
            assertThat(statement.commissionAmount()).isEqualByComparingTo(line.bhStaysRevenue());
            assertThat(statement.ownerAmount()).isEqualByComparingTo(line.ownerAmount());
            assertThat(statement.netPayout()).isEqualByComparingTo(line.ownerAmount());
            assertThat(statement.unallocatedReservationCount()).isEqualTo(line.unallocatedReservationCount());
        }
        return statements;
    }

    @Test
    void reservationsKeepThePercentOfTheirCreation_evenWhileThePropertyWasUnconfigured() {
        User owner = account(Role.OWNER);
        Property property = property(owner, null);
        LedgerFixtures ledger = ledger();

        Reservation before = direct(property, LocalDate.of(2049, 3, 1), "RON", "500.00", "400.00", "100.00", "0", "0", "0");
        propertyService.updateCommission(property.getId(), new BigDecimal("15"));
        Reservation after = direct(property, LocalDate.of(2049, 4, 1), "RON", "500.00", "400.00", "100.00", "0", "0", "0");
        assertThat(snapshotOf(before)).isNull();
        assertThat(snapshotOf(after)).isEqualByComparingTo("15.00");

        ledger.capture(before, "500.00", Instant.parse("2049-01-10T10:00:00Z"));
        ledger.capture(after, "500.00", Instant.parse("2049-01-11T10:00:00Z"));

        PropertyCommissionCurrencyResponse line = commissionReportService
                .propertyReport(property.getId(), LocalDate.of(2049, 1, 1), LocalDate.of(2049, 1, 31)).currencies().get(0);
        assertThat(line.bhStaysRevenue()).isEqualByComparingTo("60.00");      // 15% of 400, only the later booking
        assertThat(line.unallocatedNetRevenue()).isEqualByComparingTo("500.00");
        assertThat(line.unallocatedReservationCount()).isEqualTo(1);
        assertThat(line.ownerAmount()).isEqualByComparingTo("940.00");
    }

    @Test
    void ownerWithASingleCurrency_getsItInTheDeprecatedFieldsWithItsCode() {
        User owner = account(Role.OWNER);
        Property property = property(owner, "10.00");
        ledger().capture(direct(property, LocalDate.of(2050, 3, 1), "EUR", "200.00", "150.00", "50.00", "0", "0", "0"),
                "200.00", Instant.parse("2050-01-10T10:00:00Z"));

        OwnerDashboardSummaryResponse dashboard = ownerService.getMyDashboardSummary(owner.getId());

        assertThat(dashboard.revenueByCurrency()).hasSize(1);
        assertThat(dashboard.currency()).isEqualTo("EUR");
        assertThat(dashboard.grossRevenue()).isEqualByComparingTo("200.00");
        assertThat(dashboard.commissionAmount()).isEqualByComparingTo("15.00");
        assertThat(dashboard.netRevenue()).isEqualByComparingTo("185.00");
        OwnerPropertyResponse card = ownerService.getMyProperty(owner.getId(), property.getId());
        assertThat(card.currency()).isEqualTo("EUR");
        assertThat(card.netRevenue()).isEqualByComparingTo("185.00");
    }

    @Test
    void aStatementOverlappingOneAlreadyIssuedIsRejected_soNoTransactionIsCountedTwice() {
        User admin = account(Role.ADMINISTRATOR);
        User owner = account(Role.OWNER);
        Property property = property(owner, "20.00");
        LedgerFixtures ledger = ledger();
        ledger.capture(direct(property, LocalDate.of(2052, 3, 1), "RON", "500.00", "400.00", "100.00", "0", "0", "0"),
                "500.00", Instant.parse("2052-01-20T10:00:00Z"));
        ledger.capture(direct(property, LocalDate.of(2052, 4, 1), "RON", "300.00", "200.00", "100.00", "0", "0", "0"),
                "300.00", Instant.parse("2052-02-10T10:00:00Z"));

        ownerStatementService.generate(owner.getId(), LocalDate.of(2052, 1, 1), LocalDate.of(2052, 1, 31), admin);

        // the same period, and a period sharing days with it, would count the January capture again
        assertThatThrownBy(() -> ownerStatementService.generate(owner.getId(),
                LocalDate.of(2052, 1, 1), LocalDate.of(2052, 1, 31), admin))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> ownerStatementService.generate(owner.getId(),
                LocalDate.of(2052, 1, 15), LocalDate.of(2052, 2, 29), admin))
                .isInstanceOf(ConflictException.class);

        // the next period is fine and holds only its own movements
        List<OwnerStatementResponse> february = ownerStatementService.generate(owner.getId(),
                LocalDate.of(2052, 2, 1), LocalDate.of(2052, 2, 29), admin);
        assertThat(february).hasSize(1);
        assertThat(february.get(0).capturedTotal()).isEqualByComparingTo("300.00");
        assertThat(february.get(0).commissionAmount()).isEqualByComparingTo("40.00");
    }

    @Test
    void adminDashboardRevenue_isListedPerCurrency_neverAMixedTotal() {
        Property property = property(account(Role.OWNER), "20.00");
        direct(property, LocalDate.of(2053, 3, 1), "RON", "500.00", "400.00", "100.00", "0", "0", "0");
        direct(property, LocalDate.of(2053, 4, 1), "EUR", "300.00", "200.00", "100.00", "0", "0", "0");

        DashboardSummaryResponse summary = dashboardService.getSummary();

        // the database is shared, so other tests' reservations are in the totals too
        assertThat(summary.totalRevenueByCurrency()).extracting(CurrencyAmountResponse::currency)
                .contains("EUR", "RON").doesNotHaveDuplicates().isSorted();
        assertThat(summary.totalRevenue()).isNull();
        assertThat(summary.currency()).isNull();
    }

    @Test
    void financeAndStatementsAreNotVisibleToOwnersOrOperationalRoles() throws Exception {
        for (Role denied : List.of(Role.OWNER, Role.CLEANER, Role.MAINTENANCE, Role.SUPPORT_AGENT)) {
            UserPrincipal principal = new UserPrincipal(account(denied));
            mockMvc.perform(get("/api/v1/reports/financial/summary").with(user(principal)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/v1/owner-statements").with(user(principal)))
                    .andExpect(status().isForbidden());
        }
        for (Role allowed : List.of(Role.SUPER_ADMIN, Role.ADMINISTRATOR, Role.ACCOUNTANT)) {
            UserPrincipal principal = new UserPrincipal(account(allowed));
            mockMvc.perform(get("/api/v1/reports/financial/summary?from=2051-01-01&to=2051-01-31")
                            .with(user(principal)))
                    .andExpect(status().isOk());
        }
    }
}
