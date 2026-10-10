package com.bhstays.pms.controller;

import com.bhstays.pms.common.csv.CsvWriter;
import com.bhstays.pms.common.exception.ResourceNotFoundException;
import com.bhstays.pms.common.response.ApiResponse;
import com.bhstays.pms.common.response.PageResponse;
import com.bhstays.pms.domain.OwnerStatementStatus;
import com.bhstays.pms.domain.User;
import com.bhstays.pms.dto.ownerstatement.OwnerStatementGenerateRequest;
import com.bhstays.pms.dto.ownerstatement.OwnerStatementMarkPaidRequest;
import com.bhstays.pms.dto.ownerstatement.OwnerStatementResponse;
import com.bhstays.pms.dto.ownerstatement.OwnerStatementSummaryResponse;
import com.bhstays.pms.repository.UserRepository;
import com.bhstays.pms.security.SecurityUtils;
import com.bhstays.pms.service.OwnerStatementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/owner-statements")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMINISTRATOR','ACCOUNTANT')")
@Tag(name = "Owner Statements", description = "Periodic payout statements generated for property owners")
public class OwnerStatementController {

    private final OwnerStatementService ownerStatementService;
    private final UserRepository userRepository;

    @GetMapping
    @Operation(summary = "List owner statements with filters")
    public ResponseEntity<ApiResponse<PageResponse<OwnerStatementSummaryResponse>>> list(
            @RequestParam(required = false) UUID ownerId,
            @RequestParam(required = false) OwnerStatementStatus status,
            Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(ownerStatementService.list(ownerId, status, pageable)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an owner statement by id, including its per-property lines")
    public ResponseEntity<ApiResponse<OwnerStatementResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(ownerStatementService.get(id)));
    }

    @GetMapping("/export")
    @Operation(summary = "Export owner statements matching the given filters as CSV")
    public void export(
            @RequestParam(required = false) UUID ownerId,
            @RequestParam(required = false) OwnerStatementStatus status,
            HttpServletResponse response) throws IOException {
        CsvWriter.write(response, "deconturi-proprietari.csv",
                List.of("Proprietar", "Început perioadă", "Sfârșit perioadă", "Metodă calcul", "Încasat",
                        "Refunduri", "Venit net", "Bază comisionabilă", "Comision BH Stays", "Sumă proprietar",
                        "Cheltuieli", "Net de plată", "Monedă", "Status", "Plătit la"),
                ownerStatementService.exportRows(ownerId, status));
    }

    @PostMapping("/generate")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMINISTRATOR')")
    @Operation(summary = "Generate statement(s) for an owner and period (one per currency with activity)")
    public ResponseEntity<ApiResponse<List<OwnerStatementResponse>>> generate(
            @Valid @RequestBody OwnerStatementGenerateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                ownerStatementService.generate(request.ownerId(), request.periodStart(), request.periodEnd(), currentUser()),
                "Decont generat cu succes"));
    }

    @PatchMapping("/{id}/mark-paid")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMINISTRATOR')")
    @Operation(summary = "Mark a statement as paid")
    public ResponseEntity<ApiResponse<OwnerStatementResponse>> markPaid(
            @PathVariable UUID id, @RequestBody(required = false) OwnerStatementMarkPaidRequest request) {
        String reference = request != null ? request.paymentReference() : null;
        return ResponseEntity.ok(ApiResponse.success(
                ownerStatementService.markPaid(id, reference, currentUser()), "Decont marcat ca plătit"));
    }

    private User currentUser() {
        return userRepository.findById(SecurityUtils.requireCurrentUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}
