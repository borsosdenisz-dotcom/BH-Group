package com.bhgroup.pms.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bhgroup.pms.common.exception.BadRequestException;
import com.bhgroup.pms.config.AppProperties;
import com.bhgroup.pms.domain.Property;
import com.bhgroup.pms.domain.Role;
import com.bhgroup.pms.domain.User;
import com.bhgroup.pms.domain.UserStatus;
import com.bhgroup.pms.dto.user.UserCreateRequest;
import com.bhgroup.pms.dto.user.UserStatusUpdateRequest;
import com.bhgroup.pms.dto.user.UserUpdateRequest;
import com.bhgroup.pms.repository.PropertyRepository;
import com.bhgroup.pms.repository.RefreshTokenRepository;
import com.bhgroup.pms.repository.UserRepository;
import com.bhgroup.pms.repository.VerificationTokenRepository;
import com.bhgroup.pms.security.SecureTokenGenerator;
import com.bhgroup.pms.service.mapper.UserMapperImpl;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserAdminServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PropertyRepository propertyRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private VerificationTokenRepository verificationTokenRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private SecureTokenGenerator secureTokenGenerator;
    @Mock
    private EmailService emailService;
    @Mock
    private AuditService auditService;

    private UserAdminService userAdminService;
    private User targetUser;

    @BeforeEach
    void setUp() {
        AppProperties appProperties = new AppProperties();
        appProperties.setBaseUrl("https://app.bhgroup.io");
        appProperties.getSecurity().setUserInviteTokenExpirationMinutes(4320);

        userAdminService = new UserAdminService(
                userRepository, propertyRepository, refreshTokenRepository, verificationTokenRepository,
                passwordEncoder, secureTokenGenerator, emailService, auditService, new UserMapperImpl(),
                appProperties);

        targetUser = User.builder()
                .email("target@bhgroup.io")
                .firstName("Target")
                .lastName("User")
                .role(Role.ADMINISTRATOR)
                .status(UserStatus.ACTIVE)
                .build();
        targetUser.setId(UUID.randomUUID());
    }

    @Test
    void updateStatus_rejectsSelfDisable() {
        when(userRepository.findById(targetUser.getId())).thenReturn(Optional.of(targetUser));

        UserStatusUpdateRequest request = new UserStatusUpdateRequest(UserStatus.DISABLED, "target@bhgroup.io");

        assertThatThrownBy(() -> userAdminService.updateStatus(
                targetUser.getId(), request, targetUser.getId(), "SUPER_ADMIN"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("own account");
    }

    @Test
    void updateStatus_rejectsDisableWithoutMatchingEmailConfirmation() {
        when(userRepository.findById(targetUser.getId())).thenReturn(Optional.of(targetUser));
        UUID actingUserId = UUID.randomUUID();

        UserStatusUpdateRequest wrongEmail = new UserStatusUpdateRequest(UserStatus.DISABLED, "wrong@bhgroup.io");

        assertThatThrownBy(() -> userAdminService.updateStatus(
                targetUser.getId(), wrongEmail, actingUserId, "SUPER_ADMIN"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Type the account's email");
    }

    @Test
    void updateStatus_rejectsDisablingTheLastActiveSuperAdmin() {
        targetUser.setRole(Role.SUPER_ADMIN);
        when(userRepository.findById(targetUser.getId())).thenReturn(Optional.of(targetUser));
        when(userRepository.countByRoleAndStatusAndIdNot(Role.SUPER_ADMIN, UserStatus.ACTIVE, targetUser.getId()))
                .thenReturn(0L);
        UUID actingUserId = UUID.randomUUID();

        UserStatusUpdateRequest request = new UserStatusUpdateRequest(UserStatus.DISABLED, "target@bhgroup.io");

        assertThatThrownBy(() -> userAdminService.updateStatus(
                targetUser.getId(), request, actingUserId, "SUPER_ADMIN"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("last active Super Admin");
    }

    @Test
    void updateStatus_allowsDisablingASuperAdminWhenAnotherOneIsActive() {
        targetUser.setRole(Role.SUPER_ADMIN);
        when(userRepository.findById(targetUser.getId())).thenReturn(Optional.of(targetUser));
        when(userRepository.countByRoleAndStatusAndIdNot(Role.SUPER_ADMIN, UserStatus.ACTIVE, targetUser.getId()))
                .thenReturn(1L);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        UUID actingUserId = UUID.randomUUID();

        UserStatusUpdateRequest request = new UserStatusUpdateRequest(UserStatus.DISABLED, "target@bhgroup.io");

        var response = userAdminService.updateStatus(targetUser.getId(), request, actingUserId, "SUPER_ADMIN");

        assertThat(response.status()).isEqualTo(UserStatus.DISABLED);
    }

    @Test
    void updateStatus_rejectsDisablingAnOwnerWithProperties() {
        targetUser.setRole(Role.OWNER);
        when(userRepository.findById(targetUser.getId())).thenReturn(Optional.of(targetUser));
        Property property = Property.builder().name("Some property").build();
        when(propertyRepository.findByOwnerId(targetUser.getId())).thenReturn(List.of(property));
        UUID actingUserId = UUID.randomUUID();

        UserStatusUpdateRequest request = new UserStatusUpdateRequest(UserStatus.DISABLED, "target@bhgroup.io");

        assertThatThrownBy(() -> userAdminService.updateStatus(
                targetUser.getId(), request, actingUserId, "SUPER_ADMIN"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Reassign this owner's properties");
    }

    @Test
    void updateStatus_rejectsSelfSuspend() {
        when(userRepository.findById(targetUser.getId())).thenReturn(Optional.of(targetUser));

        UserStatusUpdateRequest request = new UserStatusUpdateRequest(UserStatus.SUSPENDED, "target@bhgroup.io");

        assertThatThrownBy(() -> userAdminService.updateStatus(
                targetUser.getId(), request, targetUser.getId(), "SUPER_ADMIN"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("own account");
    }

    @Test
    void updateStatus_rejectsSuspendingTheLastActiveSuperAdmin() {
        targetUser.setRole(Role.SUPER_ADMIN);
        when(userRepository.findById(targetUser.getId())).thenReturn(Optional.of(targetUser));
        when(userRepository.countByRoleAndStatusAndIdNot(Role.SUPER_ADMIN, UserStatus.ACTIVE, targetUser.getId()))
                .thenReturn(0L);
        UUID actingUserId = UUID.randomUUID();

        UserStatusUpdateRequest request = new UserStatusUpdateRequest(UserStatus.SUSPENDED, "target@bhgroup.io");

        assertThatThrownBy(() -> userAdminService.updateStatus(
                targetUser.getId(), request, actingUserId, "SUPER_ADMIN"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("last active Super Admin");
    }

    @Test
    void updateStatus_suspendingRequiresMatchingEmailConfirmation() {
        when(userRepository.findById(targetUser.getId())).thenReturn(Optional.of(targetUser));
        UUID actingUserId = UUID.randomUUID();

        UserStatusUpdateRequest request = new UserStatusUpdateRequest(UserStatus.SUSPENDED, null);

        assertThatThrownBy(() -> userAdminService.updateStatus(
                targetUser.getId(), request, actingUserId, "SUPER_ADMIN"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Type the account's email");
    }

    @Test
    void update_rejectsChangingOwnRoleAwayFromSuperAdmin() {
        targetUser.setRole(Role.SUPER_ADMIN);
        when(userRepository.findById(targetUser.getId())).thenReturn(Optional.of(targetUser));

        UserUpdateRequest request = new UserUpdateRequest("Target", "User", null, Role.ADMINISTRATOR);

        assertThatThrownBy(() -> userAdminService.update(
                targetUser.getId(), request, targetUser.getId(), "SUPER_ADMIN"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("own role");
    }

    @Test
    void update_rejectsRemovingTheLastActiveSuperAdminsRole() {
        targetUser.setRole(Role.SUPER_ADMIN);
        when(userRepository.findById(targetUser.getId())).thenReturn(Optional.of(targetUser));
        when(userRepository.countByRoleAndStatusAndIdNot(Role.SUPER_ADMIN, UserStatus.ACTIVE, targetUser.getId()))
                .thenReturn(0L);
        UUID actingUserId = UUID.randomUUID();

        UserUpdateRequest request = new UserUpdateRequest("Target", "User", null, Role.ADMINISTRATOR);

        assertThatThrownBy(() -> userAdminService.update(targetUser.getId(), request, actingUserId, "SUPER_ADMIN"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("last active Super Admin");
    }

    @Test
    void update_allowsRemovingASuperAdminsRoleWhenAnotherOneIsActive() {
        targetUser.setRole(Role.SUPER_ADMIN);
        when(userRepository.findById(targetUser.getId())).thenReturn(Optional.of(targetUser));
        when(userRepository.countByRoleAndStatusAndIdNot(Role.SUPER_ADMIN, UserStatus.ACTIVE, targetUser.getId()))
                .thenReturn(1L);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        UUID actingUserId = UUID.randomUUID();

        UserUpdateRequest request = new UserUpdateRequest("Target", "User", null, Role.ADMINISTRATOR);

        var response = userAdminService.update(targetUser.getId(), request, actingUserId, "SUPER_ADMIN");

        assertThat(response.role()).isEqualTo(Role.ADMINISTRATOR);
    }

    @Test
    void create_returnsAnAcceptInviteUrlBuiltFromTheFreshToken() {
        when(userRepository.existsByEmailIgnoreCase("new@bhgroup.io")).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("hash");
        when(secureTokenGenerator.generateRawToken()).thenReturn("raw-token-123");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(UUID.randomUUID());
            return user;
        });

        UserCreateRequest request = new UserCreateRequest("New", "User", "new@bhgroup.io", null, Role.CLEANER);

        var response = userAdminService.create(request, "SUPER_ADMIN");

        assertThat(response.inviteUrl()).isEqualTo("https://app.bhgroup.io/accept-invite/raw-token-123");
    }

    @Test
    void create_administratorCanAssignAnUnrestrictedRole() {
        when(userRepository.existsByEmailIgnoreCase("new@bhgroup.io")).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("hash");
        when(secureTokenGenerator.generateRawToken()).thenReturn("raw-token-789");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(UUID.randomUUID());
            return user;
        });

        UserCreateRequest request = new UserCreateRequest("New", "Cleaner", "new@bhgroup.io", null, Role.CLEANER);

        var response = userAdminService.create(request, "ADMINISTRATOR");

        assertThat(response.role()).isEqualTo(Role.CLEANER);
    }

    @Test
    void create_administratorCannotAssignSuperAdmin_escalationBlocked() {
        UserCreateRequest request = new UserCreateRequest("New", "Admin", "new@bhgroup.io", null, Role.SUPER_ADMIN);

        assertThatThrownBy(() -> userAdminService.create(request, "ADMINISTRATOR"))
                .isInstanceOf(com.bhgroup.pms.common.exception.ForbiddenException.class);
    }

    @Test
    void create_administratorCannotAssignAdministrator_escalationBlocked() {
        UserCreateRequest request = new UserCreateRequest("New", "Admin", "new@bhgroup.io", null, Role.ADMINISTRATOR);

        assertThatThrownBy(() -> userAdminService.create(request, "ADMINISTRATOR"))
                .isInstanceOf(com.bhgroup.pms.common.exception.ForbiddenException.class);
    }

    @Test
    void resendInvite_returnsARefreshedInviteUrl() {
        targetUser.setStatus(UserStatus.PENDING);
        when(userRepository.findById(targetUser.getId())).thenReturn(Optional.of(targetUser));
        when(secureTokenGenerator.generateRawToken()).thenReturn("raw-token-456");

        var response = userAdminService.resendInvite(targetUser.getId(), "SUPER_ADMIN");

        assertThat(response.inviteUrl()).isEqualTo("https://app.bhgroup.io/accept-invite/raw-token-456");
    }

    @Test
    void resendInvite_rejectsWhenUserIsNotPending() {
        targetUser.setStatus(UserStatus.ACTIVE);
        when(userRepository.findById(targetUser.getId())).thenReturn(Optional.of(targetUser));

        assertThatThrownBy(() -> userAdminService.resendInvite(targetUser.getId(), "SUPER_ADMIN"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("pending invitations");
    }

    @Test
    void delete_permanentlyRemovesADemoAccountWithNoAssociatedData() {
        targetUser.setRole(Role.CLEANER);
        when(userRepository.findById(targetUser.getId())).thenReturn(Optional.of(targetUser));
        when(propertyRepository.findByOwnerId(targetUser.getId())).thenReturn(List.of());
        UUID actingUserId = UUID.randomUUID();

        userAdminService.delete(targetUser.getId(), actingUserId, "SUPER_ADMIN");

        verify(userRepository).delete(targetUser);
        verify(auditService).record(eq(com.bhgroup.pms.domain.AuditAction.USER_DELETED), eq(null),
                any(String.class), eq(null), eq(null));
    }

    @Test
    void delete_rejectsDeletingOwnAccount() {
        when(userRepository.findById(targetUser.getId())).thenReturn(Optional.of(targetUser));

        assertThatThrownBy(() -> userAdminService.delete(targetUser.getId(), targetUser.getId(), "SUPER_ADMIN"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("own account");

        verify(userRepository, never()).delete(any(User.class));
    }

    @Test
    void delete_rejectsDeletingTheLastActiveSuperAdmin() {
        targetUser.setRole(Role.SUPER_ADMIN);
        when(userRepository.findById(targetUser.getId())).thenReturn(Optional.of(targetUser));
        when(userRepository.countByRoleAndStatusAndIdNot(Role.SUPER_ADMIN, UserStatus.ACTIVE, targetUser.getId()))
                .thenReturn(0L);
        UUID actingUserId = UUID.randomUUID();

        assertThatThrownBy(() -> userAdminService.delete(targetUser.getId(), actingUserId, "SUPER_ADMIN"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("last active Super Admin");

        verify(userRepository, never()).delete(any(User.class));
    }

    @Test
    void delete_rejectsDeletingAUserWhoOwnsProperties() {
        targetUser.setRole(Role.OWNER);
        when(userRepository.findById(targetUser.getId())).thenReturn(Optional.of(targetUser));
        Property property = Property.builder().name("Some property").build();
        when(propertyRepository.findByOwnerId(targetUser.getId())).thenReturn(List.of(property));
        UUID actingUserId = UUID.randomUUID();

        assertThatThrownBy(() -> userAdminService.delete(targetUser.getId(), actingUserId, "SUPER_ADMIN"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Reassign or delete this user's properties");

        verify(userRepository, never()).delete(any(User.class));
    }
}
