package in.ruhmans.alliancetrading.auth.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "sessions")
public class Session {

    @Id
    @Column(updatable = false)
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "admin_user_id")
    private UUID adminUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "session_type", nullable = false, length = 10)
    private SessionType sessionType;

    @Column(name = "device_id", length = 200)
    private String deviceId;

    @Column(name = "device_info", length = 500)
    private String deviceInfo;

    @Column(name = "refresh_token_hash", nullable = false, length = 64)
    private String refreshTokenHash;

    @Column(name = "prev_refresh_token_hash", length = 64)
    private String prevRefreshTokenHash;

    @Column(name = "refresh_token_expires_at", nullable = false)
    private Instant refreshTokenExpiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoke_reason", length = 50)
    private String revokeReason;

    protected Session() {}

    private Session(UUID userId, UUID adminUserId, SessionType sessionType,
                    String deviceId, String deviceInfo, String refreshTokenHash,
                    Instant refreshTokenExpiresAt, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.adminUserId = adminUserId;
        this.sessionType = sessionType;
        this.deviceId = deviceId;
        this.deviceInfo = deviceInfo;
        this.refreshTokenHash = refreshTokenHash;
        this.refreshTokenExpiresAt = refreshTokenExpiresAt;
        this.createdAt = createdAt;
    }

    public static Session forUser(UUID userId, String deviceId, String deviceInfo,
                                  String refreshTokenHash, Instant refreshTokenExpiresAt, Instant now) {
        return new Session(userId, null, SessionType.USER, deviceId, deviceInfo,
                refreshTokenHash, refreshTokenExpiresAt, now);
    }

    public static Session forAdmin(UUID adminUserId, String refreshTokenHash,
                                   Instant refreshTokenExpiresAt, Instant now) {
        return new Session(null, adminUserId, SessionType.ADMIN, null, null,
                refreshTokenHash, refreshTokenExpiresAt, now);
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public UUID getAdminUserId() { return adminUserId; }
    public SessionType getSessionType() { return sessionType; }
    public String getDeviceId() { return deviceId; }
    public String getDeviceInfo() { return deviceInfo; }
    public String getRefreshTokenHash() { return refreshTokenHash; }
    public String getPrevRefreshTokenHash() { return prevRefreshTokenHash; }
    public Instant getRefreshTokenExpiresAt() { return refreshTokenExpiresAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public String getRevokeReason() { return revokeReason; }

    public boolean isActive() {
        return revokedAt == null;
    }

    public UUID getSubjectId() {
        return sessionType == SessionType.USER ? userId : adminUserId;
    }

    public void revoke(Instant now, RevokeReason reason) {
        this.revokedAt = now;
        this.revokeReason = reason.name();
    }

    public void rotateRefreshToken(String newHash, Instant newExpiresAt) {
        this.prevRefreshTokenHash = this.refreshTokenHash;
        this.refreshTokenHash = newHash;
        this.refreshTokenExpiresAt = newExpiresAt;
    }
}
