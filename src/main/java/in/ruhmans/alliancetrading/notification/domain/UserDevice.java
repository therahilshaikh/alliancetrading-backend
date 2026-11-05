package in.ruhmans.alliancetrading.notification.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_devices")
public class UserDevice {

    @Id
    @Column(name = "user_id", updatable = false)
    private UUID userId;

    @Column(name = "push_token", nullable = false, length = 500)
    private String pushToken;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserDevice() {}

    public UserDevice(UUID userId, String pushToken, Instant now) {
        this.userId = userId;
        this.pushToken = pushToken;
        this.updatedAt = now;
    }

    public UUID getUserId() { return userId; }
    public String getPushToken() { return pushToken; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void updateToken(String pushToken, Instant now) {
        this.pushToken = pushToken;
        this.updatedAt = now;
    }
}
