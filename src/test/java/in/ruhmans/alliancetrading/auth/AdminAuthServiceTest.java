package in.ruhmans.alliancetrading.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import in.ruhmans.alliancetrading.admin.domain.AdminRole;
import in.ruhmans.alliancetrading.admin.domain.AdminUser;
import in.ruhmans.alliancetrading.admin.repository.AdminUserRepository;
import in.ruhmans.alliancetrading.audit.service.AuditService;
import in.ruhmans.alliancetrading.auth.domain.Session;
import in.ruhmans.alliancetrading.auth.repository.SessionRepository;
import in.ruhmans.alliancetrading.auth.service.AdminAuthService;
import in.ruhmans.alliancetrading.auth.service.JwtService;
import in.ruhmans.alliancetrading.auth.service.SessionService;
import in.ruhmans.alliancetrading.common.config.SecurityProperties;
import in.ruhmans.alliancetrading.common.error.ApiException;
import in.ruhmans.alliancetrading.common.error.ErrorCode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.security.crypto.password.PasswordEncoder;

class AdminAuthServiceTest {

    private AdminUserRepository adminUserRepository;
    private SessionRepository sessionRepository;
    private PasswordEncoder passwordEncoder;
    private SessionService sessionService;
    private AdminAuthService adminAuthService;
    private AuditService auditService;
    private Clock clock;

    @BeforeEach
    void setUp() {
        adminUserRepository = Mockito.mock(AdminUserRepository.class);
        sessionRepository = Mockito.mock(SessionRepository.class);
        passwordEncoder = Mockito.mock(PasswordEncoder.class);
        JwtService jwtService = Mockito.mock(JwtService.class);
        clock = Clock.fixed(Instant.now(), ZoneId.of("Asia/Kolkata"));

        when(jwtService.generateAccessToken(any(), any(), anyString(), anyString())).thenReturn("jwt");
        when(jwtService.getRefreshTokenTtl()).thenReturn(Duration.ofDays(30));
        when(passwordEncoder.encode(anyString())).thenReturn("hash");
        when(adminUserRepository.save(any(AdminUser.class))).thenAnswer(i -> i.getArgument(0));
        when(adminUserRepository.findByEmail(anyString())).thenReturn(Optional.empty());

        sessionService = new SessionService(sessionRepository, adminUserRepository, jwtService, clock);
        var props = new SecurityProperties(null, null,
                new SecurityProperties.Admin(5, Duration.ofMinutes(15), Duration.ofMinutes(30)));
        auditService = Mockito.mock(AuditService.class);
        adminAuthService = new AdminAuthService(adminUserRepository, passwordEncoder, sessionService, auditService,
                clock, props);
    }

    private AdminUser existingAdmin() {
        AdminUser admin = new AdminUser("priya@x.in", "hash", AdminRole.ADMIN, "Priya Sharma", clock.instant());
        when(adminUserRepository.findByEmail("priya@x.in")).thenReturn(Optional.of(admin));
        when(adminUserRepository.findById(admin.getId())).thenReturn(Optional.of(admin));
        return admin;
    }

    @Test
    void createAdminRequiresADisplayName() {
        for (String name : new String[] {null, "", "   ", "x".repeat(101)}) {
            assertThatThrownBy(() -> adminAuthService.createAdmin("a@x.in", "SecurePassword1!", AdminRole.ADMIN, name))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("displayName");
        }
        verify(adminUserRepository, never()).save(any());
    }

    @Test
    void createAdminTrimsTheDisplayName() {
        AdminUser admin = adminAuthService.createAdmin("a@x.in", "SecurePassword1!", AdminRole.ADMIN, "  Priya Sharma ");
        assertThat(admin.getDisplayName()).isEqualTo("Priya Sharma");
    }

    @Test
    void renameChangesTheDisplayName() {
        AdminUser admin = existingAdmin();
        assertThat(adminAuthService.rename(admin.getId(), "Priya S").getDisplayName()).isEqualTo("Priya S");
        assertThatThrownBy(() -> adminAuthService.rename(admin.getId(), " "))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void loginReturnsTheSignedInAdmin() {
        AdminUser admin = existingAdmin();
        when(passwordEncoder.matches("pw", "hash")).thenReturn(true);

        var tokens = adminAuthService.login("priya@x.in", "pw");

        assertThat(tokens.admin()).isEqualTo(new SessionService.AdminProfile(
                admin.getId(), "priya@x.in", "Priya Sharma", AdminRole.ADMIN));
    }

    @Test
    void refreshReturnsTheSignedInAdmin() {
        AdminUser admin = existingAdmin();
        when(passwordEncoder.matches("pw", "hash")).thenReturn(true);
        var login = adminAuthService.login("priya@x.in", "pw");

        ArgumentCaptor<Session> saved = ArgumentCaptor.forClass(Session.class);
        verify(sessionRepository).save(saved.capture());
        when(sessionRepository.findActiveByRefreshTokenHash(anyString())).thenReturn(Optional.of(saved.getValue()));

        var refreshed = sessionService.refresh(login.refreshToken());

        assertThat(refreshed.admin().displayName()).isEqualTo("Priya Sharma");
        assertThat(refreshed.admin().id()).isEqualTo(admin.getId());
    }

    @Test
    void memberSessionsCarryNoAdmin() {
        var tokens = sessionService.createUserSession(java.util.UUID.randomUUID(), "USER", "device", "info");
        assertThat(tokens.admin()).isNull();
    }

    // ---- Change own password ----

    @Test
    void changePasswordRevokesAllSessionsAndReturnsAFreshOne() {
        AdminUser admin = existingAdmin();
        when(passwordEncoder.matches("OldPassword1!", "hash")).thenReturn(true);
        when(passwordEncoder.encode("NewPassword1!")).thenReturn("newhash");

        var tokens = adminAuthService.changePassword(admin.getId(), "OldPassword1!", "NewPassword1!");

        assertThat(admin.getPasswordHash()).isEqualTo("newhash");
        verify(sessionRepository).revokeActiveAdminSessions(Mockito.eq(admin.getId()), any(), Mockito.eq("PASSWORD_CHANGED"));
        verify(auditService).recordAdmin(admin.getId(), "ADMIN_PASSWORD_CHANGED", "admin_user", admin.getId().toString(), null, null);
        assertThat(tokens.refreshToken()).isNotBlank();
        assertThat(tokens.admin().id()).isEqualTo(admin.getId());
    }

    @Test
    void wrongCurrentPasswordIsRejectedAndCountsTowardsLockout() {
        AdminUser admin = existingAdmin();
        when(passwordEncoder.matches(any(), any())).thenReturn(false);

        assertThatThrownBy(() -> adminAuthService.changePassword(admin.getId(), "nope", "NewPassword1!"))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).code()).isEqualTo(ErrorCode.INVALID_CREDENTIALS));
        assertThat(admin.getFailedLoginCount()).isEqualTo(1);
        verify(sessionRepository, never()).revokeActiveAdminSessions(any(), any(), any());
    }

    @Test
    void repeatedWrongCurrentPasswordsLockTheAccount() {
        AdminUser admin = existingAdmin();
        when(passwordEncoder.matches(any(), any())).thenReturn(false);
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> adminAuthService.changePassword(admin.getId(), "nope", "NewPassword1!"));
        }

        assertThatThrownBy(() -> adminAuthService.changePassword(admin.getId(), "nope", "NewPassword1!"))
                .satisfies(e -> assertThat(((ApiException) e).code()).isEqualTo(ErrorCode.ACCOUNT_LOCKED));
    }

    @Test
    void changePasswordIsTransactionalWithoutRollbackForApiErrors() throws Exception {
        var tx = AdminAuthService.class.getMethod("changePassword", java.util.UUID.class, String.class, String.class)
                .getAnnotation(org.springframework.transaction.annotation.Transactional.class);
        assertThat(tx.noRollbackFor()).contains(ApiException.class);
    }

    @Test
    void newPasswordMustDifferAndFollowTheLengthRule() {
        AdminUser admin = existingAdmin();
        when(passwordEncoder.matches("OldPassword1!", "hash")).thenReturn(true);

        assertThatThrownBy(() -> adminAuthService.changePassword(admin.getId(), "OldPassword1!", "OldPassword1!"))
                .hasMessageContaining("different");
        assertThatThrownBy(() -> adminAuthService.changePassword(admin.getId(), "OldPassword1!", "short"))
                .hasMessageContaining("characters");
        verify(sessionRepository, never()).revokeActiveAdminSessions(any(), any(), any());
    }

    // ---- Reset another admin's password ----

    @Test
    void resetPasswordClearsLockoutAndSignsTheTargetOut() {
        AdminUser target = existingAdmin();
        target.recordFailedLogin(1, clock.instant().plusSeconds(600));
        java.util.UUID actor = java.util.UUID.randomUUID();
        when(passwordEncoder.encode("ResetPassword1!")).thenReturn("resethash");

        adminAuthService.resetPassword(actor, target.getId(), "ResetPassword1!");

        assertThat(target.getPasswordHash()).isEqualTo("resethash");
        assertThat(target.isLocked(clock.instant())).isFalse();
        verify(sessionRepository).revokeActiveAdminSessions(Mockito.eq(target.getId()), any(), Mockito.eq("PASSWORD_RESET"));
        verify(auditService).recordAdmin(actor, "ADMIN_PASSWORD_RESET", "admin_user", target.getId().toString(), null, null);
    }

    @Test
    void resetPasswordOnYourOwnAccountIsRefused() {
        AdminUser admin = existingAdmin();

        assertThatThrownBy(() -> adminAuthService.resetPassword(admin.getId(), admin.getId(), "ResetPassword1!"))
                .satisfies(e -> assertThat(((ApiException) e).code()).isEqualTo(ErrorCode.FORBIDDEN));
        verify(adminUserRepository, never()).save(any());
    }
}
