package in.ruhmans.alliancetrading.auth.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "otp_requests")
public class OtpRequest {

    @Id
    @Column(updatable = false)
    private UUID id;

    @Column(nullable = false, length = 10)
    private String mobile;

    @Column(name = "otp_hash", nullable = false, length = 64)
    private String otpHash;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected OtpRequest() {}

    public OtpRequest(String mobile, String otpHash, Instant expiresAt, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.mobile = mobile;
        this.otpHash = otpHash;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public String getMobile() { return mobile; }
    public String getOtpHash() { return otpHash; }
    public int getAttempts() { return attempts; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getCreatedAt() { return createdAt; }

    public void incrementAttempts() {
        this.attempts++;
    }

    /** Ends the code's validity once it has been used, so it cannot log in again. */
    public void consume(Instant now) {
        this.expiresAt = now;
    }

    public boolean isExpired(Instant now) {
        return now.isAfter(expiresAt);
    }
}
