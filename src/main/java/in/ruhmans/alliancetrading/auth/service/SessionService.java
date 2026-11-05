package in.ruhmans.alliancetrading.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import in.ruhmans.alliancetrading.admin.domain.AdminRole;
import in.ruhmans.alliancetrading.admin.domain.AdminUser;
import in.ruhmans.alliancetrading.admin.repository.AdminUserRepository;
import in.ruhmans.alliancetrading.auth.domain.RevokeReason;
import in.ruhmans.alliancetrading.auth.domain.Session;
import in.ruhmans.alliancetrading.auth.domain.SessionType;
import in.ruhmans.alliancetrading.auth.repository.SessionRepository;
import in.ruhmans.alliancetrading.common.error.ApiException;
import in.ruhmans.alliancetrading.common.error.ErrorCode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionService.class);
    private static final int REFRESH_TOKEN_BYTES = 32;

    private final SessionRepository sessionRepository;
    private final AdminUserRepository adminUserRepository;
    private final JwtService jwtService;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public SessionService(SessionRepository sessionRepository, AdminUserRepository adminUserRepository,
                          JwtService jwtService, Clock clock) {
        this.sessionRepository = sessionRepository;
        this.adminUserRepository = adminUserRepository;
        this.jwtService = jwtService;
        this.clock = clock;
    }

    /** {@code admin} is the signed-in admin for admin sessions and null for member sessions. */
    public record SessionTokens(String accessToken, String refreshToken, Instant expiresAt, UUID sessionId,
                                AdminProfile admin) {}

    public record AdminProfile(UUID id, String email, String displayName, AdminRole role) {
        static AdminProfile of(AdminUser a) {
            return new AdminProfile(a.getId(), a.getEmail(), a.getDisplayName(), a.getRole());
        }
    }

    @Transactional
    public SessionTokens createUserSession(UUID userId, String role, String deviceId, String deviceInfo) {
        Instant now = clock.instant();
        sessionRepository.revokeActiveUserSessions(userId, now, RevokeReason.SESSION_REPLACED.name());

        String rawRefreshToken = generateRawRefreshToken();
        String hash = sha256Hex(rawRefreshToken);
        Instant refreshExpires = now.plus(jwtService.getRefreshTokenTtl());

        Session session = Session.forUser(userId, deviceId, deviceInfo, hash, refreshExpires, now);
        sessionRepository.save(session);

        String accessToken = jwtService.generateAccessToken(userId, session.getId(), role, "USER");
        Instant accessExpires = now.plus(jwtService.getRefreshTokenTtl()); // refresh token is the longer-lived one
        // The access token expiry is in the JWT itself; we return the refresh token expiry for client reference
        return new SessionTokens(accessToken, rawRefreshToken, refreshExpires, session.getId(), null);
    }

    /** Admins may be signed in on several devices at once, so existing sessions are kept. */
    @Transactional
    public SessionTokens createAdminSession(AdminUser admin) {
        Instant now = clock.instant();

        String rawRefreshToken = generateRawRefreshToken();
        String hash = sha256Hex(rawRefreshToken);
        Instant refreshExpires = now.plus(jwtService.getRefreshTokenTtl());

        Session session = Session.forAdmin(admin.getId(), hash, refreshExpires, now);
        sessionRepository.save(session);

        String accessToken = jwtService.generateAccessToken(admin.getId(), session.getId(), admin.getRole().name(), "ADMIN");
        return new SessionTokens(accessToken, rawRefreshToken, refreshExpires, session.getId(), AdminProfile.of(admin));
    }

    /**
     * Rotates the refresh token. Presenting an already-rotated token revokes the session;
     * {@code noRollbackFor} keeps that revocation committed even though the call fails.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public SessionTokens refresh(String rawRefreshToken) {
        String hash = sha256Hex(rawRefreshToken);
        Instant now = clock.instant();

        Optional<Session> active = sessionRepository.findActiveByRefreshTokenHash(hash);
        if (active.isPresent()) {
            Session session = active.get();
            if (now.isAfter(session.getRefreshTokenExpiresAt())) {
                throw new ApiException(ErrorCode.TOKEN_EXPIRED, "Refresh token expired");
            }
            String newRaw = generateRawRefreshToken();
            String newHash = sha256Hex(newRaw);
            Instant newExpires = now.plus(jwtService.getRefreshTokenTtl());
            session.rotateRefreshToken(newHash, newExpires);
            sessionRepository.save(session);

            AdminUser admin = session.getSessionType() == SessionType.USER ? null
                    : adminUserRepository.findById(session.getAdminUserId()).orElse(null);
            String role = session.getSessionType() == SessionType.USER ? "USER"
                    : admin != null ? admin.getRole().name() : "ADMIN";
            String type = session.getSessionType().name();
            String accessToken = jwtService.generateAccessToken(session.getSubjectId(), session.getId(), role, type);
            return new SessionTokens(accessToken, newRaw, newExpires, session.getId(),
                    admin != null ? AdminProfile.of(admin) : null);
        }

        // Check if this is a reused (rotated) token
        Optional<Session> reused = sessionRepository.findActiveByPrevRefreshTokenHash(hash);
        if (reused.isPresent()) {
            Session compromised = reused.get();
            compromised.revoke(now, RevokeReason.REFRESH_TOKEN_REUSE);
            sessionRepository.save(compromised);
            log.warn("Refresh token reuse detected for session {}, revoking", compromised.getId());
            throw new ApiException(ErrorCode.SESSION_REPLACED, "Session revoked due to refresh token reuse");
        }

        throw new ApiException(ErrorCode.UNAUTHENTICATED, "Invalid refresh token");
    }

    @Transactional
    public void logout(UUID sessionId) {
        Session session = sessionRepository.findActiveById(sessionId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED, "Session not found"));
        session.revoke(clock.instant(), RevokeReason.LOGOUT);
        sessionRepository.save(session);
    }

    @Transactional(readOnly = true)
    public boolean isSessionActive(UUID sessionId) {
        return sessionRepository.findActiveById(sessionId).isPresent();
    }

    @Transactional
    public void forceLogout(UUID userId) {
        Instant now = clock.instant();
        sessionRepository.revokeActiveUserSessions(userId, now, RevokeReason.FORCE_LOGOUT.name());
    }

    /** Signs an admin out on every device. */
    @Transactional
    public int revokeAdminSessions(UUID adminUserId, RevokeReason reason) {
        return sessionRepository.revokeActiveAdminSessions(adminUserId, clock.instant(), reason.name());
    }

    private String generateRawRefreshToken() {
        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
