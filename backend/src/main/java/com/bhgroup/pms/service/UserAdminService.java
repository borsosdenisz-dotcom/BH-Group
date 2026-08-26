package com.bhgroup.pms.service;

import com.bhgroup.pms.domain.AuditAction;
import com.bhgroup.pms.service.mapper.UserMapper;
import com.bhgroup.pms.dto.auth.UserResponse;
import com.bhgroup.pms.common.exception.BadRequestException;
import com.bhgroup.pms.common.exception.ConflictException;
import com.bhgroup.pms.common.exception.ForbiddenException;
import com.bhgroup.pms.common.exception.ResourceNotFoundException;
import com.bhgroup.pms.common.response.PageResponse;
import com.bhgroup.pms.config.AppProperties;
import com.bhgroup.pms.repository.RefreshTokenRepository;
import com.bhgroup.pms.dto.user.UserCreateRequest;
import com.bhgroup.pms.dto.user.UserStatusUpdateRequest;
import com.bhgroup.pms.dto.user.UserUpdateRequest;
import com.bhgroup.pms.domain.VerificationToken;
import com.bhgroup.pms.domain.VerificationTokenType;
import com.bhgroup.pms.repository.VerificationTokenRepository;
import com.bhgroup.pms.security.SecureTokenGenerator;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bhgroup.pms.domain.Role;
import com.bhgroup.pms.domain.User;
import com.bhgroup.pms.domain.UserStatus;
import com.bhgroup.pms.repository.UserRepository;
import com.bhgroup.pms.repository.UserSpecifications;
@Service
@RequiredArgsConstructor
public class UserAdminService {

    private static final Set<Role> RESTRICTED_ROLES = Set.of(Role.SUPER_ADMIN, Role.ADMINISTRATOR);

    private static final java.util.Map<Role, String> ROLE_LABELS = java.util.Map.of(
            Role.SUPER_ADMIN, "Super Admin",
            Role.ADMINISTRATOR, "Administrator",
            Role.OWNER, "Proprietar",
            Role.CLEANER, "Cleaner",
            Role.MAINTENANCE, "Mentenanță",
            Role.ACCOUNTANT, "Contabil",
            Role.SUPPORT_AGENT, "Agent suport"
    );

    private final UserRepository userRepository;
    private final com.bhgroup.pms.repository.PropertyRepository propertyRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final VerificationTokenRepository verificationTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecureTokenGenerator secureTokenGenerator;
    private final EmailService emailService;
    private final AuditService auditService;
    private final UserMapper userMapper;
    private final AppProperties appProperties;

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> list(String search, Role role, UserStatus status, Pageable pageable) {
        Specification<User> spec = UserSpecifications.combine(
                UserSpecifications.search(search),
                UserSpecifications.hasRole(role),
                UserSpecifications.hasStatus(status)
        );
        Page<User> page = userRepository.findAll(spec, pageable);
        return PageResponse.of(page, userMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public UserResponse get(UUID id) {
        return userMapper.toResponse(findOrThrow(id));
    }

    @Transactional
    public UserResponse create(UserCreateRequest request, String actingRole) {
        assertCanAssignRole(request.role(), actingRole);

        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new ConflictException("An account with this email already exists");
        }

        User user = User.builder()
                .email(request.email().toLowerCase())
                .passwordHash(passwordEncoder.encode(secureTokenGenerator.generateRawToken()))
                .firstName(request.firstName())
                .lastName(request.lastName())
                .phone(request.phone())
                .role(request.role())
                .status(UserStatus.PENDING)
                .emailVerified(true)
                .build();
        user = userRepository.save(user);

        auditService.record(AuditAction.USER_INVITED, user, "User invited by administrator", null, null);
        String inviteUrl = sendInvite(user);

        return withInviteUrl(userMapper.toResponse(user), inviteUrl);
    }

    @Transactional
    public UserResponse resendInvite(UUID id, String actingRole) {
        User user = findOrThrow(id);
        assertCanAssignRole(user.getRole(), actingRole);

        if (user.getStatus() != UserStatus.PENDING) {
            throw new BadRequestException("Only pending invitations can be resent");
        }

        auditService.record(AuditAction.USER_INVITE_RESENT, user, "Invitation resent by administrator", null, null);
        String inviteUrl = sendInvite(user);

        return withInviteUrl(userMapper.toResponse(user), inviteUrl);
    }

    /**
     * Invalidates any still-active invite token, issues a fresh one, emails it
     * (best-effort - failures are only logged, see EmailService), and returns
     * the accept-invite URL so callers can also hand it off directly, since
     * SMTP isn't guaranteed to be configured in every environment.
     */
    private String sendInvite(User user) {
        verificationTokenRepository.invalidateActiveTokens(user.getId(), VerificationTokenType.USER_INVITE);

        String rawToken = secureTokenGenerator.generateRawToken();
        VerificationToken token = VerificationToken.builder()
                .user(user)
                .token(rawToken)
                .type(VerificationTokenType.USER_INVITE)
                .expiresAt(Instant.now().plus(
                        appProperties.getSecurity().getUserInviteTokenExpirationMinutes(), ChronoUnit.MINUTES))
                .build();
        verificationTokenRepository.save(token);

        emailService.sendUserInviteEmail(user.getEmail(), user.getFirstName(), ROLE_LABELS.get(user.getRole()),
                rawToken, appProperties.getSecurity().getUserInviteTokenExpirationMinutes());

        return appProperties.getBaseUrl() + "/accept-invite/" + rawToken;
    }

    private UserResponse withInviteUrl(UserResponse response, String inviteUrl) {
        return new UserResponse(response.id(), response.email(), response.firstName(), response.lastName(),
                response.phone(), response.role(), response.status(), response.emailVerified(),
                response.mfaEnabled(), response.createdAt(), inviteUrl);
    }

    @Transactional
    public UserResponse update(UUID id, UserUpdateRequest request, UUID actingUserId, String actingRole) {
        User user = findOrThrow(id);
        assertCanAssignRole(user.getRole(), actingRole);
        assertCanAssignRole(request.role(), actingRole);

        if (user.getRole() == Role.SUPER_ADMIN && request.role() != Role.SUPER_ADMIN) {
            assertCanRemoveSuperAdminRole(user, actingUserId);
        }

        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setPhone(request.phone());
        user.setRole(request.role());

        user = userRepository.save(user);
        return userMapper.toResponse(user);
    }

    @Transactional
    public UserResponse updateStatus(UUID id, UserStatusUpdateRequest request, UUID actingUserId, String actingRole) {
        User user = findOrThrow(id);
        assertCanAssignRole(user.getRole(), actingRole);

        if (request.status() == UserStatus.DISABLED || request.status() == UserStatus.SUSPENDED) {
            assertCanDeactivate(user, actingUserId, request.confirmEmail());
        }

        user.setStatus(request.status());
        if (request.status() != UserStatus.ACTIVE) {
            refreshTokenRepository.revokeAllByUserId(user.getId(), Instant.now());
        }
        user = userRepository.save(user);

        auditService.record(AuditAction.USER_STATUS_CHANGED, null,
                "User " + user.getId() + " (" + user.getEmail() + ") status changed to " + request.status(),
                null, null);

        return userMapper.toResponse(user);
    }

    /**
     * DISABLED is this system's "delete" and SUSPENDED is a temporary lockout,
     * but both immediately end the account's ability to log in - the same
     * "don't lock the whole team out or orphan an owner's properties with a
     * single misclick" guards apply to either.
     */
    private void assertCanDeactivate(User user, UUID actingUserId, String confirmEmail) {
        if (user.getId().equals(actingUserId)) {
            throw new BadRequestException("You cannot change your own account's status");
        }

        if (confirmEmail == null || !confirmEmail.trim().equalsIgnoreCase(user.getEmail())) {
            throw new BadRequestException("Type the account's email exactly to confirm this action");
        }

        if (user.getRole() == Role.SUPER_ADMIN && user.getStatus() == UserStatus.ACTIVE) {
            long otherActiveSuperAdmins = userRepository
                    .countByRoleAndStatusAndIdNot(Role.SUPER_ADMIN, UserStatus.ACTIVE, user.getId());
            if (otherActiveSuperAdmins == 0) {
                throw new BadRequestException("Cannot deactivate the last active Super Admin account");
            }
        }

        if (user.getRole() == Role.OWNER && !propertyRepository.findByOwnerId(user.getId()).isEmpty()) {
            throw new BadRequestException(
                    "Reassign this owner's properties to another owner before deactivating their account");
        }
    }

    @Transactional
    public void delete(UUID id, UUID actingUserId, String actingRole) {
        User user = findOrThrow(id);
        assertCanAssignRole(user.getRole(), actingRole);
        assertCanDelete(user, actingUserId);

        UUID deletedId = user.getId();
        String deletedEmail = user.getEmail();

        // Refresh/verification tokens, MFA recovery codes and notifications
        // cascade-delete at the DB level (ON DELETE CASCADE); properties,
        // messages, cleaning/maintenance assignments etc. are ON DELETE SET
        // NULL, so this never orphans a foreign key.
        userRepository.delete(user);

        auditService.record(AuditAction.USER_DELETED, null,
                "User " + deletedId + " (" + deletedEmail + ") permanently deleted by administrator " + actingUserId,
                null, null);
    }

    /**
     * Hard delete is strictly more destructive than deactivating, so it needs
     * at least the same guards as assertCanDeactivate (self-action, last
     * active Super Admin), plus a data-loss guard deactivate doesn't need:
     * deleting the row would silently orphan or null out anything the user
     * owns, so an owner (or anyone else) still holding properties is blocked
     * outright rather than cascaded through. Reservations are never linked to
     * a system User account (guests are captured as plain fields), so there
     * is nothing to check there.
     */
    private void assertCanDelete(User user, UUID actingUserId) {
        if (user.getId().equals(actingUserId)) {
            throw new BadRequestException("You cannot delete your own account");
        }

        if (user.getRole() == Role.SUPER_ADMIN && user.getStatus() == UserStatus.ACTIVE) {
            long otherActiveSuperAdmins = userRepository
                    .countByRoleAndStatusAndIdNot(Role.SUPER_ADMIN, UserStatus.ACTIVE, user.getId());
            if (otherActiveSuperAdmins == 0) {
                throw new BadRequestException("Cannot delete the last active Super Admin account");
            }
        }

        if (!propertyRepository.findByOwnerId(user.getId()).isEmpty()) {
            throw new BadRequestException(
                    "Reassign or delete this user's properties before deleting their account");
        }
    }

    /**
     * Same spirit as assertCanDeactivate: changing a Super Admin's role away
     * from SUPER_ADMIN is functionally equivalent to disabling their admin
     * access, so it needs the same self-action and last-admin protection
     * even though the account itself stays ACTIVE.
     */
    private void assertCanRemoveSuperAdminRole(User user, UUID actingUserId) {
        if (user.getId().equals(actingUserId)) {
            throw new BadRequestException(
                    "You cannot change your own role away from Super Admin - ask another Super Admin to do it");
        }

        if (user.getStatus() == UserStatus.ACTIVE) {
            long otherActiveSuperAdmins = userRepository
                    .countByRoleAndStatusAndIdNot(Role.SUPER_ADMIN, UserStatus.ACTIVE, user.getId());
            if (otherActiveSuperAdmins == 0) {
                throw new BadRequestException("Cannot remove the last active Super Admin's role");
            }
        }
    }

    private User findOrThrow(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private void assertCanAssignRole(Role targetRole, String actingRole) {
        if (RESTRICTED_ROLES.contains(targetRole) && !Role.SUPER_ADMIN.name().equals(actingRole)) {
            throw new ForbiddenException("Only a Super Admin can assign or modify Super Admin / Administrator accounts");
        }
    }
}
