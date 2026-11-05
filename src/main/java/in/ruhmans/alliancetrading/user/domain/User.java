package in.ruhmans.alliancetrading.user.domain;

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
@Table(name = "users")
public class User {

    @Id
    @Column(updatable = false)
    private UUID id;

    @Column(nullable = false, unique = true, length = 10)
    private String mobile;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status;

    /** Status to restore on reactivation; set only while SUSPENDED. */
    @Enumerated(EnumType.STRING)
    @Column(name = "suspended_from_status", length = 20)
    private UserStatus suspendedFromStatus;

    @Column(name = "suspended_at")
    private Instant suspendedAt;

    @Column(name = "suspension_reason", length = 500)
    private String suspensionReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    protected User() {}

    public User(String mobile, Instant now) {
        this.id = UUID.randomUUID();
        this.mobile = mobile;
        this.status = UserStatus.REGISTERED;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public String getMobile() { return mobile; }
    public UserStatus getStatus() { return status; }
    public UserStatus getSuspendedFromStatus() { return suspendedFromStatus; }
    public Instant getSuspendedAt() { return suspendedAt; }
    public String getSuspensionReason() { return suspensionReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }

    public void setStatus(UserStatus status) {
        this.status = status;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public boolean isSuspended() {
        return status == UserStatus.SUSPENDED;
    }

    public void suspend(String reason, Instant now) {
        if (isSuspended()) {
            throw new IllegalStateException("User is already suspended");
        }
        this.suspendedFromStatus = this.status;
        this.status = UserStatus.SUSPENDED;
        this.suspendedAt = now;
        this.suspensionReason = reason;
        this.updatedAt = now;
    }

    public void reactivate(Instant now) {
        if (!isSuspended()) {
            throw new IllegalStateException("User is not suspended");
        }
        this.status = this.suspendedFromStatus;
        this.suspendedFromStatus = null;
        this.suspendedAt = null;
        this.suspensionReason = null;
        this.updatedAt = now;
    }
}
