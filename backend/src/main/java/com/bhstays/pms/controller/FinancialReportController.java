package com.bhstays.pms.controller;

import com.bhstays.pms.common.csv.CsvWriter;
import com.bhstays.pms.common.response.ApiResponse;
import com.bhstays.pms.dto.report.CommissionSummaryResponse;
import com.bhstays.pms.dto.report.FinancialReportSummaryResponse;
import com.bhstays.pms.dto.report.PropertyCommissionReportResponse;
import com.bhstays.pms.service.FinancialReportService;
import com.bhstays.pms.service.PropertyCommissionReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports/financial")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMINISTRATOR','ACCOUNTANT')")
@Tag(name = "Financial Reports", description = "Revenue, commission and expense reporting per property")
public class FinancialReportController {

    private final FinancialReportService financialReportService;
    private final PropertyCommissionReportService propertyCommissionReportService;

    @GetMapping("/summary")
    @Operation(summary = "Get gross revenue, commission, expenses and net profit per property")
    public ResponseEntity<ApiResponse<FinancialReportSummaryResponse>> summary(
            @RequestParam(required = false) UUID propertyId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.success(financialReportService.summary(propertyId, from, to)));
    }

    @GetMapping("/properties/{propertyId}/commission")
    @Operation(summary = "Captured revenue of one property split into owner amount and BH Stays commission, per currency")
    public ResponseEntity<ApiResponse<PropertyCommissionReportResponse>> propertyCommission(
            @PathVariable UUID propertyId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.success(propertyCommissionReportService.propertyReport(propertyId, from, to)));
    }

    @GetMapping("/commission-summary")
    @Operation(summary = "Portfolio totals per currency: properties' net revenue, BH Stays commission revenue, amount owed to owners")
    public ResponseEntity<ApiResponse<CommissionSummaryResponse>> commissionSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.success(propertyCommissionReportService.summary(from, to)));
    }

    @GetMapping("/export")
    @Operation(summary = "Export the financial summary as CSV")
    public void export(
            @RequestParam(required = false) UUID propertyId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            HttpServletResponse response) throws IOException {
        CsvWriter.write(response, "raport-financiar.csv",
                List.of("Proprietate", "Proprietar", "Încasat", "Refunduri", "Venit net", "Bază comisionabilă",
                        "Comision %", "Venit BH Stays", "Sumă proprietar", "Fără defalcare", "Cheltuieli",
                        "Profit net", "Monedă"),
                financialReportService.exportRows(propertyId, from, to));
    }
}
