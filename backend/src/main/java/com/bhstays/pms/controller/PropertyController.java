package com.bhstays.pms.controller;

import com.bhstays.pms.common.csv.CsvWriter;
import com.bhstays.pms.common.exception.ResourceNotFoundException;
import com.bhstays.pms.common.response.ApiResponse;
import com.bhstays.pms.common.response.PageResponse;
import com.bhstays.pms.dto.property.PropertyCommissionUpdateRequest;
import com.bhstays.pms.dto.property.PropertyCreateRequest;
import com.bhstays.pms.dto.property.PropertyDocumentResponse;
import com.bhstays.pms.dto.property.PropertyIntegrationModeUpdateRequest;
import com.bhstays.pms.dto.property.PropertyPhotoResponse;
import com.bhstays.pms.dto.property.PropertyResponse;
import com.bhstays.pms.dto.property.PropertySummaryResponse;
import com.bhstays.pms.dto.property.PropertyUpdateRequest;
import com.bhstays.pms.dto.property.SeasonalRateRequest;
import com.bhstays.pms.dto.property.SeasonalRateResponse;
import com.bhstays.pms.security.SecurityUtils;
import com.bhstays.pms.domain.User;
import com.bhstays.pms.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.bhstays.pms.domain.Property;
import com.bhstays.pms.domain.PropertyDocument;
import com.bhstays.pms.domain.PropertyDocumentType;
import com.bhstays.pms.domain.PropertyStatus;
import com.bhstays.pms.domain.PropertyType;
import com.bhstays.pms.service.PropertyService;
@RestController
@RequestMapping("/api/v1/properties")
@RequiredArgsConstructor
@Tag(name = "Properties", description = "Property management: CRUD, photos and documents")
public class PropertyController {

    private final PropertyService propertyService;
    private final UserRepository userRepository;

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMINISTRATOR')")
    @Operation(summary = "List properties with search and filters")
    public ResponseEntity<ApiResponse<PageResponse<PropertySummaryResponse>>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) PropertyStatus status,
            @RequestParam(required = false) PropertyType type,
            Pageable pageable) {
        var result = propertyService.list(search, status, type, pageable);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMINISTRATOR')")
    @Operation(summary = "Get a property by id")
    public ResponseEntity<ApiResponse<PropertyResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(propertyService.get(id)));
    }

    @GetMapping("/export")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMINISTRATOR')")
    @Operation(summary = "Export properties matching the given filters as CSV")
    public void export(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) PropertyStatus status,
            @RequestParam(required = false) PropertyType type,
            HttpServletResponse response) throws IOException {
        CsvWriter.write(response, "proprietati.csv",
                List.of("Nume", "Tip", "Status", "Oraș", "Adresă", "Dormitoare", "Băi", "Max. oaspeți", "Preț/noapte"),
                propertyService.exportRows(search, status, type));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMINISTRATOR')")
    @Operation(summary = "Create a property")
    public ResponseEntity<ApiResponse<PropertyResponse>> create(@Valid @RequestBody PropertyCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(propertyService.create(request), "Property created successfully"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMINISTRATOR')")
    @Operation(summary = "Update a property")
    public ResponseEntity<ApiResponse<PropertyResponse>> update(@PathVariable UUID id,
                                                                 @Valid @RequestBody PropertyUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(propertyService.update(id, request), "Property updated successfully"));
    }

    @PatchMapping("/{id}/integration-mode")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMINISTRATOR')")
    @Operation(summary = "Change which system owns a property's availability (manual, iCal, channel manager)")
    public ResponseEntity<ApiResponse<PropertyResponse>> updateIntegrationMode(
            @PathVariable UUID id, @Valid @RequestBody PropertyIntegrationModeUpdateRequest request) {
        User currentUser = userRepository.findById(SecurityUtils.requireCurrentUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return ResponseEntity.ok(ApiResponse.success(
                propertyService.updateIntegrationMode(id, request.mode(), currentUser),
                "Integration mode updated successfully"));
    }

    @PatchMapping("/{id}/management-commission")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMINISTRATOR')")
    @Operation(summary = "Set or clear the BH Stays management commission (0.00-100.00%) for a property")
    public ResponseEntity<ApiResponse<PropertyResponse>> updateManagementCommission(
            @PathVariable UUID id, @Valid @RequestBody PropertyCommissionUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                propertyService.updateCommission(id, request.commissionPercent()),
                "Management commission updated successfully"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMINISTRATOR')")
    @Operation(summary = "Delete a property")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        propertyService.delete(id);
        return ResponseEntity.ok(ApiResponse.message("Property deleted successfully"));
    }

    @PostMapping(value = "/{id}/photos", consumes = "multipart/form-data")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMINISTRATOR')")
    @Operation(summary = "Upload a property photo")
    public ResponseEntity<ApiResponse<PropertyPhotoResponse>> addPhoto(@PathVariable UUID id,
                                                                        @RequestParam("file") MultipartFile file,
                                                                        @RequestParam(required = false) String caption) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(propertyService.addPhoto(id, file, caption)));
    }

    @DeleteMapping("/{id}/photos/{photoId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMINISTRATOR')")
    @Operation(summary = "Delete a property photo")
    public ResponseEntity<ApiResponse<Void>> deletePhoto(@PathVariable UUID id, @PathVariable UUID photoId) {
        propertyService.deletePhoto(id, photoId);
        return ResponseEntity.ok(ApiResponse.message("Photo deleted successfully"));
    }

    @PatchMapping("/{id}/photos/{photoId}/cover")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMINISTRATOR')")
    @Operation(summary = "Set a photo as the property cover image")
    public ResponseEntity<ApiResponse<Void>> setCoverPhoto(@PathVariable UUID id, @PathVariable UUID photoId) {
        propertyService.setCoverPhoto(id, photoId);
        return ResponseEntity.ok(ApiResponse.message("Cover photo updated"));
    }

    @PostMapping(value = "/{id}/documents", consumes = "multipart/form-data")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMINISTRATOR')")
    @Operation(summary = "Upload a property document")
    public ResponseEntity<ApiResponse<PropertyDocumentResponse>> addDocument(
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file,
            @RequestParam PropertyDocumentType documentType,
            @RequestParam(required = false)
            @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
            java.time.LocalDate expiresAt) {
        User currentUser = userRepository.findById(SecurityUtils.requireCurrentUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(propertyService.addDocument(id, file, documentType, currentUser, expiresAt)));
    }

    @GetMapping("/{id}/documents/{documentId}/download")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMINISTRATOR')")
    @Operation(summary = "Download a property document")
    public ResponseEntity<Resource> downloadDocument(@PathVariable UUID id, @PathVariable UUID documentId) {
        PropertyDocument doc = propertyService.getDocumentOrThrow(id, documentId);
        Resource resource = propertyService.loadDocumentResource(doc);
        return ResponseEntity.ok()
                .contentType(MediaTypeFactory.getMediaType(doc.getFileName()).orElse(MediaType.APPLICATION_OCTET_STREAM))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + doc.getFileName() + "\"")
                .body(resource);
    }

    @DeleteMapping("/{id}/documents/{documentId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMINISTRATOR')")
    @Operation(summary = "Delete a property document")
    public ResponseEntity<ApiResponse<Void>> deleteDocument(@PathVariable UUID id, @PathVariable UUID documentId) {
        propertyService.deleteDocument(id, documentId);
        return ResponseEntity.ok(ApiResponse.message("Document deleted successfully"));
    }

    @GetMapping("/{id}/seasonal-rates")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMINISTRATOR')")
    @Operation(summary = "List seasonal price overrides for a property")
    public ResponseEntity<ApiResponse<List<SeasonalRateResponse>>> listSeasonalRates(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(propertyService.listSeasonalRates(id)));
    }

    @PostMapping("/{id}/seasonal-rates")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMINISTRATOR')")
    @Operation(summary = "Add a seasonal price override")
    public ResponseEntity<ApiResponse<SeasonalRateResponse>> addSeasonalRate(
            @PathVariable UUID id, @Valid @RequestBody SeasonalRateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(propertyService.addSeasonalRate(id, request)));
    }

    @PutMapping("/{id}/seasonal-rates/{rateId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMINISTRATOR')")
    @Operation(summary = "Update a seasonal price override")
    public ResponseEntity<ApiResponse<SeasonalRateResponse>> updateSeasonalRate(
            @PathVariable UUID id, @PathVariable UUID rateId, @Valid @RequestBody SeasonalRateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(propertyService.updateSeasonalRate(id, rateId, request)));
    }

    @DeleteMapping("/{id}/seasonal-rates/{rateId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMINISTRATOR')")
    @Operation(summary = "Delete a seasonal price override")
    public ResponseEntity<ApiResponse<Void>> deleteSeasonalRate(@PathVariable UUID id, @PathVariable UUID rateId) {
        propertyService.deleteSeasonalRate(id, rateId);
        return ResponseEntity.ok(ApiResponse.message("Seasonal rate deleted successfully"));
    }
}
