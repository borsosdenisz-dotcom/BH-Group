package com.bhstays.pms.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bhstays.pms.common.exception.BadRequestException;
import com.bhstays.pms.common.exception.ConflictException;
import com.bhstays.pms.common.exception.ResourceNotFoundException;
import com.bhstays.pms.domain.AuditAction;
import com.bhstays.pms.domain.OwnerStatement;
import com.bhstays.pms.domain.OwnerStatementStatus;
import com.bhstays.pms.domain.Role;
import com.bhstays.pms.domain.User;
import com.bhstays.pms.dto.ownerstatement.OwnerStatementResponse;
import com.bhstays.pms.repository.OwnerStatementLineRepository;
import com.bhstays.pms.repository.OwnerStatementRepository;
import com.bhstays.pms.repository.PropertyRepository;
import com.bhstays.pms.repository.UserRepository;
import com.bhstays.pms.service.mapper.OwnerStatementMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OwnerStatementServiceTest {

    @Mock private OwnerStatementRepository ownerStatementRepository;
    @Mock private OwnerStatementLineRepository ownerStatementLineRepository;
    @Mock private UserRepository userRepository;
    @Mock private PropertyRepository propertyRepository;
    @Mock private OwnerFinancialsService ownerFinancialsService;
    @Mock private AuditService auditService;
    @Mock private OwnerStatementMapper ownerStatementMapper;

    private OwnerStatementService ownerStatementService;
    private User owner;
    private User actor;
    private UUID propertyId;
    private LocalDate periodStart;
    private LocalDate periodEnd;

    @BeforeEach
    void setUp() {
        ownerStatementService = new OwnerStatementService(ownerStatementRepository, ownerStatementLineRepository,
                userRepository, propertyRepository, ownerFinancialsService, auditService, ownerStatementMapper);

        owner = new User();
        owner.setId(UUID.randomUUID());
        owner.setFirstName("Maria");
        owner.setLastName("Ionescu");
        owner.setRole(Role.OWNER);

        actor = new User();
        actor.setId(UUID.randomUUID());

        propertyId = UUID.randomUUID();
        periodStart = LocalDate.of(2026, 7, 1);
        periodEnd = LocalDate.of(2026, 7, 31);

        org.mockito.Mockito.lenient().when(userRepository.findById(owner.getId())).thenReturn(Optional.of(owner));
        org.mockito.Mockito.lenient().when(ownerStatementRepository.save(any())).thenAnswer(inv -> {
            OwnerStatement s = inv.getArgument(0);
            if (s.getId() == null) {
                s.setId(UUID.randomUUID());
            }
            return s;
        });
        org.mockito.Mockito.lenient().when(ownerStatementLineRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        org.mockito.Mockito.lenient().when(ownerStatementMapper.toResponse(any(), any())).thenReturn(mock(OwnerStatementResponse.class));
    }

    @Test
    void generate_computesNetPayoutAndPersistsStatementWithLines() {
        when(ownerFinancialsService.computeForOwner(owner.getId(), periodStart, periodEnd)).thenReturn(List.of(
                // captured 1100, refunded 100 -> net 1000; base 800 at 25% -> 200; owner 800; expenses 50 -> 750
                row(propertyId, "RON", "1100.00", "100.00", "800.00", "25.00", "200.00", "50.00")
        ));
        when(ownerStatementRepository.findOverlapping(
                owner.getId(), "RON", periodStart, periodEnd)).thenReturn(Optional.empty());
        when(propertyRepository.findById(propertyId)).thenReturn(Optional.empty());

        List<OwnerStatementResponse> result = ownerStatementService.generate(owner.getId(), periodStart, periodEnd, actor);

        assertThat(result).hasSize(1);
        verify(ownerStatementRepository).save(argThatStatement(s ->
                s.getNetPayout().compareTo(new BigDecimal("750.00")) == 0
                        && s.getGrossRevenue().compareTo(new BigDecimal("1000.00")) == 0
                        && s.getCapturedTotal().compareTo(new BigDecimal("1100.00")) == 0
                        && s.getRefundedTotal().compareTo(new BigDecimal("100.00")) == 0
                        && s.getCommissionableBase().compareTo(new BigDecimal("800.00")) == 0
                        && s.getCommissionAmount().compareTo(new BigDecimal("200.00")) == 0
                        && s.getOwnerAmount().compareTo(new BigDecimal("800.00")) == 0
                        && OwnerStatement.CALCULATION_CAPTURED_ACCOMMODATION.equals(s.getCalculationMethod())
                        && s.getStatus() == OwnerStatementStatus.ISSUED));
        verify(ownerStatementLineRepository).save(org.mockito.ArgumentMatchers.argThat(line ->
                line.getCommissionPercent().compareTo(new BigDecimal("25.00")) == 0
                        && line.getOwnerAmount().compareTo(new BigDecimal("800.00")) == 0
                        && line.getNetAmount().compareTo(new BigDecimal("750.00")) == 0));
        verify(auditService).record(eq(AuditAction.OWNER_STATEMENT_GENERATED), any(), any(), any(), any());
    }

    @Test
    void generate_rejectsDuplicatePeriodForSameOwnerAndCurrency() {
        when(ownerFinancialsService.computeForOwner(owner.getId(), periodStart, periodEnd)).thenReturn(List.of(
                row(propertyId, "RON", "1000.00", "0", "800.00", "25.00", "200.00", "0")
        ));
        when(ownerStatementRepository.findOverlapping(
                owner.getId(), "RON", periodStart, periodEnd))
                .thenReturn(Optional.of(new OwnerStatement()));

        assertThatThrownBy(() -> ownerStatementService.generate(owner.getId(), periodStart, periodEnd, actor))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void generate_rejectsWhenNoFinancialActivity() {
        when(ownerFinancialsService.computeForOwner(owner.getId(), periodStart, periodEnd)).thenReturn(List.of(
                row(propertyId, "RON", "0", "0", "0", "25.00", "0", "0")
        ));

        assertThatThrownBy(() -> ownerStatementService.generate(owner.getId(), periodStart, periodEnd, actor))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void generate_rejectsInvertedPeriod() {
        assertThatThrownBy(() -> ownerStatementService.generate(owner.getId(), periodEnd, periodStart, actor))
                .isInstanceOf(BadRequestException.class);
        verify(ownerFinancialsService, never()).computeForOwner(any(), any(), any());
    }

    @Test
    void markPaid_setsStatusAndPaidAt() {
        OwnerStatement statement = OwnerStatement.builder()
                .owner(owner).currency("RON").netPayout(new BigDecimal("500")).status(OwnerStatementStatus.ISSUED)
                .build();
        statement.setId(UUID.randomUUID());
        when(ownerStatementRepository.findById(statement.getId())).thenReturn(Optional.of(statement));
        when(ownerStatementLineRepository.findByStatementIdOrderByPropertyNameAsc(statement.getId())).thenReturn(List.of());

        ownerStatementService.markPaid(statement.getId(), "OP-123", actor);

        assertThat(statement.getStatus()).isEqualTo(OwnerStatementStatus.PAID);
        assertThat(statement.getPaidAt()).isNotNull();
        assertThat(statement.getPaymentReference()).isEqualTo("OP-123");
        verify(auditService).record(eq(AuditAction.OWNER_STATEMENT_MARKED_PAID), any(), any(), any(), any());
    }

    @Test
    void markPaid_rejectsAlreadyPaidStatement() {
        OwnerStatement statement = OwnerStatement.builder()
                .owner(owner).currency("RON").netPayout(new BigDecimal("500")).status(OwnerStatementStatus.PAID)
                .build();
        statement.setId(UUID.randomUUID());
        when(ownerStatementRepository.findById(statement.getId())).thenReturn(Optional.of(statement));

        assertThatThrownBy(() -> ownerStatementService.markPaid(statement.getId(), null, actor))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void getForOwner_throwsNotFoundWhenStatementBelongsToDifferentOwner() {
        User otherOwner = new User();
        otherOwner.setId(UUID.randomUUID());
        OwnerStatement statement = OwnerStatement.builder().owner(otherOwner).currency("RON").build();
        statement.setId(UUID.randomUUID());
        when(ownerStatementRepository.findById(statement.getId())).thenReturn(Optional.of(statement));

        assertThatThrownBy(() -> ownerStatementService.getForOwner(owner.getId(), statement.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void generate_aRefundOfAnEarlierPeriodIsANegativeAdjustment() {
        // only a 100 refund of a capture made in an earlier period: base -80 at 20% -> commission -16
        when(ownerFinancialsService.computeForOwner(owner.getId(), periodStart, periodEnd)).thenReturn(List.of(
                row(propertyId, "RON", "0", "100.00", "-80.00", "20.00", "-16.00", "0")
        ));
        when(ownerStatementRepository.findOverlapping(any(), any(), any(), any()))
                .thenReturn(Optional.empty());
        when(propertyRepository.findById(propertyId)).thenReturn(Optional.empty());

        ownerStatementService.generate(owner.getId(), periodStart, periodEnd, actor);

        verify(ownerStatementRepository).save(argThatStatement(s ->
                s.getGrossRevenue().compareTo(new BigDecimal("-100.00")) == 0
                        && s.getRefundedTotal().compareTo(new BigDecimal("100.00")) == 0
                        && s.getCommissionAmount().compareTo(new BigDecimal("-16.00")) == 0
                        && s.getOwnerAmount().compareTo(new BigDecimal("-84.00")) == 0
                        && s.getNetPayout().compareTo(new BigDecimal("-84.00")) == 0));
    }

    @Test
    void generate_lineWithSeveralSnapshotPercentsHasNoSinglePercent() {
        when(ownerFinancialsService.computeForOwner(owner.getId(), periodStart, periodEnd)).thenReturn(List.of(
                row(propertyId, "RON", "1000.00", "0", "1000.00", null, "225.00", "0", "20.00", "25.00")
        ));
        when(ownerStatementRepository.findOverlapping(any(), any(), any(), any()))
                .thenReturn(Optional.empty());
        when(propertyRepository.findById(propertyId)).thenReturn(Optional.empty());

        ownerStatementService.generate(owner.getId(), periodStart, periodEnd, actor);

        verify(ownerStatementLineRepository).save(org.mockito.ArgumentMatchers.argThat(line ->
                line.getCommissionPercent() == null
                        && line.getCommissionAmount().compareTo(new BigDecimal("225.00")) == 0));
    }

    @Test
    void generate_issuesOneStatementPerCurrency() {
        when(ownerFinancialsService.computeForOwner(owner.getId(), periodStart, periodEnd)).thenReturn(List.of(
                row(propertyId, "RON", "500.00", "0", "400.00", "20.00", "80.00", "0"),
                row(propertyId, "EUR", "100.00", "0", "100.00", "20.00", "20.00", "0")
        ));
        when(ownerStatementRepository.findOverlapping(any(), any(), any(), any()))
                .thenReturn(Optional.empty());
        when(propertyRepository.findById(propertyId)).thenReturn(Optional.empty());

        assertThat(ownerStatementService.generate(owner.getId(), periodStart, periodEnd, actor)).hasSize(2);
        verify(ownerStatementRepository).save(argThatStatement(s -> "EUR".equals(s.getCurrency())
                && s.getOwnerAmount().compareTo(new BigDecimal("80.00")) == 0));
        verify(ownerStatementRepository).save(argThatStatement(s -> "RON".equals(s.getCurrency())
                && s.getOwnerAmount().compareTo(new BigDecimal("420.00")) == 0));
    }

    /** A row as OwnerFinancialsService returns it; owner amount and payout follow the shared formula. */
    /**
     * A row as OwnerFinancialsService returns it; owner amount and payout follow the shared formula.
     * {@code percent} is the single snapshot percent, or pass several in {@code percents}.
     */
    private static OwnerFinancialsService.PropertyFinancials row(UUID propertyId, String currency, String captured,
                                                                  String refunded, String base, String percent,
                                                                  String commission, String expenses,
                                                                  String... percents) {
        BigDecimal net = new BigDecimal(captured).subtract(new BigDecimal(refunded));
        BigDecimal ownerAmount = net.subtract(new BigDecimal(commission));
        List<BigDecimal> snapshotPercents = percent != null
                ? List.of(new BigDecimal(percent))
                : java.util.Arrays.stream(percents).map(BigDecimal::new).toList();
        return new OwnerFinancialsService.PropertyFinancials(propertyId, "Casa Mare", currency,
                new BigDecimal(captured), new BigDecimal(refunded), net, new BigDecimal(base), snapshotPercents,
                new BigDecimal(commission), ownerAmount, BigDecimal.ZERO, 0, new BigDecimal(expenses),
                ownerAmount.subtract(new BigDecimal(expenses)));
    }

    private OwnerStatement argThatStatement(java.util.function.Predicate<OwnerStatement> predicate) {
        return org.mockito.ArgumentMatchers.argThat(predicate::test);
    }
}
