package in.ruhmans.alliancetrading.settings.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** A saved app setting value; the row with the latest {@code effectiveFrom <= now} per key is in force. */
@Entity
@Table(name = "app_settings")
public class AppSetting {

    public static final String MIN_PROFIT_WITHDRAWAL = "min_profit_withdrawal";
    public static final String PRINCIPAL_WITHDRAWAL_ENABLED = "principal_withdrawal_enabled";
    public static final String BANK_CHANGE_COOLOFF_HOURS = "bank_change_cooloff_hours";
    public static final String SECOND_APPROVAL_THRESHOLD = "withdrawal_second_approval_threshold";
    public static final String MIN_APP_VERSION = "min_app_version";

    @Id
    @Column(updatable = false)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String key;

    @Column(nullable = false, length = 500)
    private String value;

    @Column(name = "effective_from", nullable = false)
    private Instant effectiveFrom;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Version
    private Long version;

    protected AppSetting() {}

    public AppSetting(String key, String value, Instant effectiveFrom, UUID createdBy, Instant now) {
        this.id = UUID.randomUUID();
        this.key = key;
        this.value = value;
        this.effectiveFrom = effectiveFrom;
        this.createdBy = createdBy;
        this.createdAt = now;
    }

    public UUID getId() { return id; }
    public String getKey() { return key; }
    public String getValue() { return value; }
    public Instant getEffectiveFrom() { return effectiveFrom; }
    public UUID getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
}
