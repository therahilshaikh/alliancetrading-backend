package in.ruhmans.alliancetrading.settings.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** A saved withdrawal fee; the row with the latest {@code effectiveFrom <= now} per type is in force. */
@Entity
@Table(name = "fee_settings")
public class FeeSetting {

    @Id
    @Column(updatable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "withdrawal_type", nullable = false, length = 10)
    private WithdrawalType withdrawalType;

    @Column(name = "fee_pct", nullable = false, precision = 6, scale = 4)
    private BigDecimal feePct;

    @Column(name = "effective_from", nullable = false)
    private Instant effectiveFrom;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Version
    private Long version;

    protected FeeSetting() {}

    public FeeSetting(WithdrawalType withdrawalType, BigDecimal feePct,
                      Instant effectiveFrom, UUID createdBy, Instant now) {
        this.id = UUID.randomUUID();
        this.withdrawalType = withdrawalType;
        this.feePct = feePct;
        this.effectiveFrom = effectiveFrom;
        this.createdBy = createdBy;
        this.createdAt = now;
    }

    public UUID getId() { return id; }
    public WithdrawalType getWithdrawalType() { return withdrawalType; }
    public BigDecimal getFeePct() { return feePct; }
    public Instant getEffectiveFrom() { return effectiveFrom; }
    public UUID getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
}
