package com.bhgroup.pms.controller;

import com.bhgroup.pms.dto.auth.UserResponse;
import com.bhgroup.pms.common.response.ApiResponse;
import com.bhgroup.pms.common.response.PageResponse;
import com.bhgroup.pms.security.SecurityUtils;
import com.bhgroup.pms.dto.user.UserCreateRequest;
import com.bhgroup.pms.dto.user.UserStatusUpdateRequest;
import com.bhgroup.pms.dto.user.UserUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
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

import com.bhgroup.pms.common.exception.ResourceNotFoundException;
import com.bhgroup.pms.domain.Role;
import com.bhgroup.pms.domain.User;
import com.bhgroup.pms.domain.UserStatus;
import com.bhgroup.pms.repository.UserRepository;
import com.bhgroup.pms.service.AuthService;
import com.bhgroup.pms.service.UserAdminService;
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMINISTRATOR')")
@Tag(name = "Users", description = "User management for administrators")
public class UserAdminController {

    private final UserAdminService userAdminService;
    private final AuthService authService;
    private final UserRepository userRepository;

    @GetMapping
    @Operation(summary = "List users with search and filters")
    public ResponseEntity<ApiResponse<PageResponse<UserResponse>>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) UserStatus status,
            Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(userAdminService.list(search, role, status, pageable)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a user by id")
    public ResponseEntity<ApiResponse<UserResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(userAdminService.get(id)));
    }

    @PostMapping
    @Operation(summary = "Create a user with an explicit role")
    public ResponseEntity<ApiResponse<UserResponse>> create(@Valid @RequestBody UserCreateRequest request) {
        UserResponse response = userAdminService.create(request, currentRole());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "User created successfully"));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a user's profile and role")
    public ResponseEntity<ApiResponse<UserResponse>> update(@PathVariable UUID id,
                                                             @Valid @RequestBody UserUpdateRequest request) {
        UserResponse response = userAdminService.update(
                id, request, SecurityUtils.requireCurrentUserId(), currentRole());
        return ResponseEntity.ok(ApiResponse.success(response, "User updated successfully"));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Change a user's account status")
    public ResponseEntity<ApiResponse<UserResponse>> updateStatus(@PathVariable UUID id,
                                                                   @Valid @RequestBody UserStatusUpdateRequest request) {
        UserResponse response = userAdminService.updateStatus(
                id, request, SecurityUtils.requireCurrentUserId(), currentRole());
        return ResponseEntity.ok(ApiResponse.success(response, "User status updated successfully"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Permanently delete a user account")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        userAdminService.delete(id, SecurityUtils.requireCurrentUserId(), currentRole());
        return ResponseEntity.ok(ApiResponse.message("User deleted successfully"));
    }

    @PostMapping("/{id}/resend-invite")
    @Operation(summary = "Resend the activation invitation for a pending user, returning a fresh invite link")
    public ResponseEntity<ApiResponse<UserResponse>> resendInvite(@PathVariable UUID id) {
        UserResponse response = userAdminService.resendInvite(id, currentRole());
        return ResponseEntity.ok(ApiResponse.success(response, "Invitation resent"));
    }

    @PostMapping("/{id}/reset-mfa")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Reset a user's MFA (lost device/recovery codes) - forces mandatory setup again on next login")
    public ResponseEntity<ApiResponse<Void>> resetMfa(@PathVariable UUID id) {
        User actor = userRepository.findById(SecurityUtils.requireCurrentUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        authService.resetMfaByAdmin(id, actor);
        return ResponseEntity.ok(ApiResponse.message("MFA reset - the user will set it up again on next login"));
    }

    private String currentRole() {
        return SecurityUtils.getCurrentPrincipal()
                .map(principal -> principal.getRole())
                .orElseThrow();
    }
}
