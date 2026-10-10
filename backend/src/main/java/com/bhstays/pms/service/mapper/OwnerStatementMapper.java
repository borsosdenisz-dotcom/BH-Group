package com.bhstays.pms.service.mapper;

import com.bhstays.pms.domain.OwnerStatement;
import com.bhstays.pms.domain.OwnerStatementLine;
import com.bhstays.pms.dto.ownerstatement.OwnerStatementLineResponse;
import com.bhstays.pms.dto.ownerstatement.OwnerStatementResponse;
import com.bhstays.pms.dto.ownerstatement.OwnerStatementSummaryResponse;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class OwnerStatementMapper {

    public OwnerStatementResponse toResponse(OwnerStatement statement, List<OwnerStatementLine> lines) {
        return new OwnerStatementResponse(
                statement.getId(),
                statement.getOwner().getId(),
                fullName(statement.getOwner().getFirstName(), statement.getOwner().getLastName()),
                statement.getPeriodStart(),
                statement.getPeriodEnd(),
                statement.getCurrency(),
                statement.getGrossRevenue(),
                statement.getCommissionAmount(),
                statement.getExpensesTotal(),
                statement.getNetPayout(),
                statement.getStatus(),
                statement.getGeneratedBy() != null
                        ? fullName(statement.getGeneratedBy().getFirstName(), statement.getGeneratedBy().getLastName())
                        : null,
                statement.getPaidAt(),
                statement.getPaymentReference(),
                statement.getCreatedAt(),
                lines.stream().map(this::toLineResponse).toList(),
                statement.getCalculationMethod(),
                statement.getCapturedTotal(),
                statement.getRefundedTotal(),
                statement.getCommissionableBase(),
                statement.getOwnerAmount(),
                statement.getUnallocatedNetRevenue(),
                statement.getUnallocatedReservationCount());
    }

    public OwnerStatementSummaryResponse toSummaryResponse(OwnerStatement statement) {
        return new OwnerStatementSummaryResponse(
                statement.getId(),
                statement.getOwner().getId(),
                fullName(statement.getOwner().getFirstName(), statement.getOwner().getLastName()),
                statement.getPeriodStart(),
                statement.getPeriodEnd(),
                statement.getCurrency(),
                statement.getNetPayout(),
                statement.getStatus(),
                statement.getCreatedAt(),
                statement.getPaidAt());
    }

    private OwnerStatementLineResponse toLineResponse(OwnerStatementLine line) {
        return new OwnerStatementLineResponse(
                line.getProperty() != null ? line.getProperty().getId() : null,
                line.getPropertyName(),
                line.getGrossRevenue(),
                line.getCommissionAmount(),
                line.getExpensesTotal(),
                line.getNetAmount(),
                line.getCapturedTotal(),
                line.getRefundedTotal(),
                line.getCommissionableBase(),
                line.getCommissionPercent(),
                line.getOwnerAmount(),
                line.getUnallocatedNetRevenue(),
                line.getUnallocatedReservationCount());
    }

    private String fullName(String firstName, String lastName) {
        return firstName + " " + lastName;
    }
}
