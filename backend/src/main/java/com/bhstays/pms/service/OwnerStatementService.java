package com.bhstays.pms.service;

import com.bhstays.pms.common.exception.BadRequestException;
import com.bhstays.pms.common.exception.ConflictException;
import com.bhstays.pms.common.exception.ResourceNotFoundException;
import com.bhstays.pms.common.response.PageResponse;
import com.bhstays.pms.domain.AuditAction;
import com.bhstays.pms.domain.OwnerStatement;
import com.bhstays.pms.domain.OwnerStatementLine;
import com.bhstays.pms.domain.OwnerStatementStatus;
import com.bhstays.pms.domain.Property;
import com.bhstays.pms.domain.Role;
import com.bhstays.pms.domain.User;
import com.bhstays.pms.dto.ownerstatement.OwnerStatementResponse;
import com.bhstays.pms.dto.ownerstatement.OwnerStatementSummaryResponse;
import com.bhstays.pms.repository.OwnerStatementLineRepository;
import com.bhstays.pms.repository.OwnerStatementRepository;
import com.bhstays.pms.repository.OwnerStatementSpecifications;
import com.bhstays.pms.repository.PropertyRepository;
import com.bhstays.pms.repository.UserRepository;
import com.bhstays.pms.service.mapper.OwnerStatementMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Generation is a one-shot, explicit admin action - not a live recomputed
 * view - because a statement is a record of what was owed for a period,
 * and that must stay stable even if payments/expenses for the period are
 * edited afterwards. Its figures are the transaction-dated movements of
 * the period (see {@link PropertyCommissionReportService}): a refund made
 * after a statement was issued never changes it - it appears, with the
 * matching commission reduction, in the statement of the period in which
 * the refund was made. A period that overlaps one already issued for the
 * same owner and currency (the exact same period included) is rejected
 * (see {@link #generate}): the shared days' transactions would otherwise be
 * counted in two statements.
 */
@Service
@RequiredArgsConstructor
public class OwnerStatementService {

    private final OwnerStatementRepository ownerStatementRepository;
    private final OwnerStatementLineRepository ownerStatementLineRepository;
    private final UserRepository userRepository;
    private final PropertyRepository propertyRepository;
    private final OwnerFinancialsService ownerFinancialsService;
    private final AuditService auditService;
    private final OwnerStatementMapper ownerStatementMapper;

    @Transactional
    public List<OwnerStatementResponse> generate(UUID ownerId, LocalDate periodStart, LocalDate periodEnd, User actor) {
        if (periodEnd.isBefore(periodStart)) {
            throw new BadRequestException("Perioada de sfârșit trebuie să fie după perioada de început");
        }
        User owner = userRepository.findById(ownerId)
                .filter(u -> u.getRole() == Role.OWNER)
                .orElseThrow(() -> new BadRequestException("Proprietarul nu a fost găsit"));

        var financials = ownerFinancialsService.computeForOwner(ownerId, periodStart, periodEnd).stream()
                .filter(OwnerFinancialsService.PropertyFinancials::hasActivity)
                .toList();
        if (financials.isEmpty()) {
            throw new BadRequestException("Nicio activitate financiară găsită pentru acest proprietar în perioada selectată");
        }

        // One statement per currency - a statement never mixes currencies.
        Map<String, List<OwnerFinancialsService.PropertyFinancials>> byCurrency = financials.stream()
                .collect(Collectors.groupingBy(OwnerFinancialsService.PropertyFinancials::currency, TreeMap::new,
                        Collectors.toList()));

        List<OwnerStatementResponse> results = new java.util.ArrayList<>();
        for (var entry : byCurrency.entrySet()) {
            String currency = entry.getKey();
            List<OwnerFinancialsService.PropertyFinancials> rows = entry.getValue();

            // Overlapping periods would count the same transactions in two statements.
            var overlapping = ownerStatementRepository.findOverlapping(ownerId, currency, periodStart, periodEnd);
            if (overlapping.isPresent()) {
                throw new ConflictException(
                        "Există deja un decont " + currency + " pentru " + owner.getFirstName() + " " + owner.getLastName()
                                + " în perioada " + overlapping.get().getPeriodStart() + " - "
                                + overlapping.get().getPeriodEnd() + ", care se suprapune cu perioada aleasă");
            }

            BigDecimal commissionAmount = sum(rows, OwnerFinancialsService.PropertyFinancials::bhStaysCommission);
            BigDecimal ownerAmount = sum(rows, OwnerFinancialsService.PropertyFinancials::ownerAmount);
            BigDecimal expensesTotal = sum(rows, OwnerFinancialsService.PropertyFinancials::expensesTotal);
            BigDecimal netPayout = ownerAmount.subtract(expensesTotal);

            OwnerStatement statement = OwnerStatement.builder()
                    .owner(owner)
                    .periodStart(periodStart)
                    .periodEnd(periodEnd)
                    .currency(currency)
                    .calculationMethod(OwnerStatement.CALCULATION_CAPTURED_ACCOMMODATION)
                    .capturedTotal(sum(rows, OwnerFinancialsService.PropertyFinancials::capturedTotal))
                    .refundedTotal(sum(rows, OwnerFinancialsService.PropertyFinancials::refundedTotal))
                    .grossRevenue(sum(rows, OwnerFinancialsService.PropertyFinancials::netRevenue))
                    .commissionableBase(sum(rows, OwnerFinancialsService.PropertyFinancials::commissionableBase))
                    .commissionAmount(commissionAmount)
                    .ownerAmount(ownerAmount)
                    .expensesTotal(expensesTotal)
                    .netPayout(netPayout)
                    .unallocatedNetRevenue(sum(rows, OwnerFinancialsService.PropertyFinancials::unallocatedNetRevenue))
                    .unallocatedReservationCount(rows.stream()
                            .mapToInt(OwnerFinancialsService.PropertyFinancials::unallocatedReservationCount).sum())
                    .status(OwnerStatementStatus.ISSUED)
                    .generatedBy(actor)
                    .build();
            statement = ownerStatementRepository.save(statement);

            List<OwnerStatementLine> lines = new java.util.ArrayList<>();
            for (var row : rows) {
                Property property = propertyRepository.findById(row.propertyId()).orElse(null);
                BigDecimal lineOwnerAmount = row.ownerAmount();
                lines.add(ownerStatementLineRepository.save(OwnerStatementLine.builder()
                        .statement(statement)
                        .property(property)
                        .propertyName(row.propertyName())
                        .capturedTotal(row.capturedTotal())
                        .refundedTotal(row.refundedTotal())
                        .grossRevenue(row.netRevenue())
                        .commissionableBase(row.commissionableBase())
                        .commissionPercent(row.singleCommissionPercent())
                        .commissionAmount(row.bhStaysCommission())
                        .ownerAmount(lineOwnerAmount)
                        .expensesTotal(row.expensesTotal())
                        .netAmount(lineOwnerAmount.subtract(row.expensesTotal()))
                        .unallocatedNetRevenue(row.unallocatedNetRevenue())
                        .unallocatedReservationCount(row.unallocatedReservationCount())
                        .build()));
            }

            auditService.record(AuditAction.OWNER_STATEMENT_GENERATED, actor,
                    "Decont " + currency + " generat pentru " + owner.getFirstName() + " " + owner.getLastName()
                            + " (" + periodStart + " - " + periodEnd + "), net " + netPayout + " " + currency,
                    null, null);

            results.add(ownerStatementMapper.toResponse(statement, lines));
        }
        return results;
    }

    @Transactional
    public OwnerStatementResponse markPaid(UUID statementId, String paymentReference, User actor) {
        OwnerStatement statement = findOrThrow(statementId);
        if (statement.getStatus() == OwnerStatementStatus.PAID) {
            throw new BadRequestException("Decontul este deja marcat ca plătit");
        }
        statement.setStatus(OwnerStatementStatus.PAID);
        statement.setPaidAt(Instant.now());
        statement.setPaymentReference(paymentReference);
        statement = ownerStatementRepository.save(statement);

        auditService.record(AuditAction.OWNER_STATEMENT_MARKED_PAID, actor,
                "Decont " + statement.getId() + " (" + statement.getOwner().getFirstName() + " "
                        + statement.getOwner().getLastName() + ") marcat ca plătit, " + statement.getNetPayout()
                        + " " + statement.getCurrency(),
                null, null);

        return ownerStatementMapper.toResponse(statement, linesFor(statement.getId()));
    }

    @Transactional(readOnly = true)
    public OwnerStatementResponse get(UUID id) {
        OwnerStatement statement = findOrThrow(id);
        return ownerStatementMapper.toResponse(statement, linesFor(id));
    }

    @Transactional(readOnly = true)
    public OwnerStatementResponse getForOwner(UUID ownerId, UUID id) {
        OwnerStatement statement = ownerStatementRepository.findById(id)
                .filter(s -> s.getOwner().getId().equals(ownerId))
                .orElseThrow(() -> new ResourceNotFoundException("Statement not found"));
        return ownerStatementMapper.toResponse(statement, linesFor(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<OwnerStatementSummaryResponse> list(UUID ownerId, OwnerStatementStatus status, Pageable pageable) {
        Specification<OwnerStatement> spec = OwnerStatementSpecifications.combine(
                OwnerStatementSpecifications.hasOwner(ownerId),
                OwnerStatementSpecifications.hasStatus(status));
        return PageResponse.of(ownerStatementRepository.findAll(spec, pageable), ownerStatementMapper::toSummaryResponse);
    }

    @Transactional(readOnly = true)
    public PageResponse<OwnerStatementSummaryResponse> listForOwner(UUID ownerId, Pageable pageable) {
        Specification<OwnerStatement> spec = OwnerStatementSpecifications.hasOwner(ownerId);
        return PageResponse.of(ownerStatementRepository.findAll(spec, pageable), ownerStatementMapper::toSummaryResponse);
    }

    @Transactional(readOnly = true)
    public List<List<String>> exportRows(UUID ownerId, OwnerStatementStatus status) {
        Specification<OwnerStatement> spec = OwnerStatementSpecifications.combine(
                OwnerStatementSpecifications.hasOwner(ownerId),
                OwnerStatementSpecifications.hasStatus(status));
        return ownerStatementRepository.findAll(spec).stream()
                .map(s -> List.of(
                        s.getOwner().getFirstName() + " " + s.getOwner().getLastName(),
                        s.getPeriodStart().toString(),
                        s.getPeriodEnd().toString(),
                        s.getCalculationMethod(),
                        s.getCapturedTotal() != null ? s.getCapturedTotal().toString() : "",
                        s.getRefundedTotal() != null ? s.getRefundedTotal().toString() : "",
                        s.getGrossRevenue().toString(),
                        s.getCommissionableBase() != null ? s.getCommissionableBase().toString() : "",
                        s.getCommissionAmount().toString(),
                        s.getOwnerAmount() != null ? s.getOwnerAmount().toString() : "",
                        s.getExpensesTotal().toString(),
                        s.getNetPayout().toString(),
                        s.getCurrency(),
                        s.getStatus().toString(),
                        s.getPaidAt() != null ? s.getPaidAt().toString() : ""
                ))
                .toList();
    }

    private OwnerStatement findOrThrow(UUID id) {
        return ownerStatementRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Statement not found"));
    }

    private List<OwnerStatementLine> linesFor(UUID statementId) {
        return ownerStatementLineRepository.findByStatementIdOrderByPropertyNameAsc(statementId);
    }

    private BigDecimal sum(List<OwnerFinancialsService.PropertyFinancials> rows,
                            java.util.function.Function<OwnerFinancialsService.PropertyFinancials, BigDecimal> extractor) {
        return rows.stream().map(extractor).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
