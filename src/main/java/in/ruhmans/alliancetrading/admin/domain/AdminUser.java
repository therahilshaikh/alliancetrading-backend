package in.ruhmans.alliancetrading.admin.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "admin_users")
public class AdminUser {

    @Id
    @Column(updatable = false)
    private UUID id;

    // citext: case-insensitive uniqueness and lookup
    @Column(nullable = false, unique = true, columnDefinition = "citext")
    private String email;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AdminRole role;

    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "failed_login_count", nullable = false)
    private int failedLoginCount;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    protected AdminUser() {}

    public AdminUser(String email, String passwordHash, AdminRole role, String displayName, Instant now) {
        this.id = UUID.randomUUID();
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.displayName = displayName;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getDisplayName() { return displayName; }

    public void rename(String displayName, Instant now) {
        this.displayName = displayName;
        this.updatedAt = now;
    }
    public String getPasswordHash() { return passwordHash; }
    public AdminRole getRole() { return role; }
    public String getStatus() { return status; }
    public int getFailedLoginCount() { return failedLoginCount; }
    public Instant getLockedUntil() { return lockedUntil; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }

    public boolean isActive() { return "ACTIVE".equals(status); }

    public boolean isLocked(Instant now) {
        return lockedUntil != null && now.isBefore(lockedUntil);
    }

    public void recordFailedLogin(int maxAttempts, Instant lockUntil) {
        this.failedLoginCount++;
        if (this.failedLoginCount >= maxAttempts) {
            this.lockedUntil = lockUntil;
        }
    }

    public void resetLoginAttempts() {
        this.failedLoginCount = 0;
        this.lockedUntil = null;
    }

    public void changePassword(String passwordHash, Instant now) {
        this.passwordHash = passwordHash;
        this.updatedAt = now;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
