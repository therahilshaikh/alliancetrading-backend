package in.ruhmans.alliancetrading.auth.service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import in.ruhmans.alliancetrading.admin.domain.AdminRole;
import in.ruhmans.alliancetrading.admin.domain.AdminUser;
import in.ruhmans.alliancetrading.admin.repository.AdminUserRepository;
import in.ruhmans.alliancetrading.audit.service.AuditService;
import in.ruhmans.alliancetrading.auth.domain.RevokeReason;
import in.ruhmans.alliancetrading.common.config.SecurityProperties;
import in.ruhmans.alliancetrading.common.error.ApiException;
import in.ruhmans.alliancetrading.common.error.ErrorCode;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminAuthService {

    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final SessionService sessionService;
    private final AuditService auditService;
    private final Clock clock;
    private final SecurityProperties.Admin config;

    /** Same rule as admin creation. */
    public static final int PASSWORD_MIN = 10;
    public static final int PASSWORD_MAX = 100;

    public AdminAuthService(AdminUserRepository adminUserRepository, PasswordEncoder passwordEncoder,
                            SessionService sessionService, AuditService auditService, Clock clock,
                            SecurityProperties securityProperties) {
        this.adminUserRepository = adminUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.sessionService = sessionService;
        this.auditService = auditService;
        this.clock = clock;
        this.config = securityProperties.admin();
    }

    /** Email + password login. Repeated failures lock the account temporarily. */
    @Transactional(noRollbackFor = ApiException.class)
    public SessionService.SessionTokens login(String email, String password) {
        Instant now = clock.instant();

        AdminUser admin = adminUserRepository.findByEmail(email)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED, "Invalid credentials"));

        if (!admin.isActive()) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED, "Account is disabled");
        }

        if (admin.isLocked(now)) {
            throw new ApiException(ErrorCode.ACCOUNT_LOCKED, "Account temporarily locked. Try again later.");
        }

        if (!passwordEncoder.matches(password, admin.getPasswordHash())) {
            admin.recordFailedLogin(config.maxFailedLogins(), now.plus(config.lockDuration()));
            admin.setUpdatedAt(now);
            adminUserRepository.save(admin);
            throw new ApiException(ErrorCode.UNAUTHENTICATED, "Invalid credentials");
        }

        admin.resetLoginAttempts();
        admin.setUpdatedAt(now);
        adminUserRepository.save(admin);

        return sessionService.createAdminSession(admin);
    }

    @Transactional
    public AdminUser createAdmin(String email, String rawPassword, AdminRole role, String displayName) {
        String name = requireDisplayName(displayName);
        if (adminUserRepository.findByEmail(email).isPresent()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Email already in use");
        }
        AdminUser admin = new AdminUser(email, passwordEncoder.encode(rawPassword), role, name, clock.instant());
        return adminUserRepository.save(admin);
    }

    @Transactional
    public AdminUser rename(UUID adminId, String displayName) {
        String name = requireDisplayName(displayName);
        AdminUser admin = adminUserRepository.findById(adminId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Admin not found"));
        admin.rename(name, clock.instant());
        return adminUserRepository.save(admin);
    }

    /**
     * Changes the caller's own password. A wrong current password counts towards the same lockout as
     * login (kept by {@code noRollbackFor}). On success every session of this admin is revoked and a
     * fresh session is returned for the caller.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public SessionService.SessionTokens changePassword(UUID adminId, String currentPassword, String newPassword) {
        Instant now = clock.instant();
        AdminUser admin = adminUserRepository.findById(adminId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED, "Admin not found"));

        if (admin.isLocked(now)) {
            throw new ApiException(ErrorCode.ACCOUNT_LOCKED, "Account temporarily locked. Try again later.");
        }
        if (currentPassword == null || !passwordEncoder.matches(currentPassword, admin.getPasswordHash())) {
            admin.recordFailedLogin(config.maxFailedLogins(), now.plus(config.lockDuration()));
            admin.setUpdatedAt(now);
            adminUserRepository.save(admin);
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS, "Current password is incorrect");
        }
        requirePasswordRules(newPassword);
        if (passwordEncoder.matches(newPassword, admin.getPasswordHash())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "New password must be different from the current one");
        }

        admin.changePassword(passwordEncoder.encode(newPassword), now);
        admin.resetLoginAttempts();
        adminUserRepository.save(admin);
        sessionService.revokeAdminSessions(adminId, RevokeReason.PASSWORD_CHANGED);
        auditService.recordAdmin(adminId, "ADMIN_PASSWORD_CHANGED", "admin_user", adminId.toString(), null, null);
        return sessionService.createAdminSession(admin);
    }

    /** SUPER_ADMIN sets another admin's password (e.g. after a lockout); clears the lockout and signs them out everywhere. */
    @Transactional
    public void resetPassword(UUID actorId, UUID targetId, String newPassword) {
        if (actorId.equals(targetId)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Use change-password for your own account");
        }
        requirePasswordRules(newPassword);
        AdminUser target = adminUserRepository.findById(targetId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Admin not found"));

        target.changePassword(passwordEncoder.encode(newPassword), clock.instant());
        target.resetLoginAttempts();
        adminUserRepository.save(target);
        sessionService.revokeAdminSessions(targetId, RevokeReason.PASSWORD_RESET);
        auditService.recordAdmin(actorId, "ADMIN_PASSWORD_RESET", "admin_user", targetId.toString(), null, null);
    }

    static void requirePasswordRules(String password) {
        if (password == null || password.length() < PASSWORD_MIN || password.length() > PASSWORD_MAX) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "Password must be " + PASSWORD_MIN + "-" + PASSWORD_MAX + " characters");
        }
    }

    /** Trimmed, 1-100 characters. */
    static String requireDisplayName(String displayName) {
        String name = displayName == null ? "" : displayName.strip();
        if (name.isEmpty() || name.length() > 100) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "displayName must be 1-100 characters");
        }
        return name;
    }
}
