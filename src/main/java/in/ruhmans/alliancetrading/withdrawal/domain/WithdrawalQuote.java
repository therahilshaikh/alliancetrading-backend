package in.ruhmans.alliancetrading.withdrawal.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import in.ruhmans.alliancetrading.settings.domain.WithdrawalType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "withdrawal_quotes")
public class WithdrawalQuote {

    @Id
    @Column(updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "withdrawal_type", nullable = false, length = 10, updatable = false)
    private WithdrawalType withdrawalType;

    @Column(name = "gross_amount", nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal grossAmount;

    @Column(name = "fee_setting_id", nullable = false, updatable = false)
    private UUID feeSettingId;

    @Column(name = "fee_pct", nullable = false, precision = 6, scale = 4, updatable = false)
    private BigDecimal feePct;

    @Column(name = "fee_amount", nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal feeAmount;

    @Column(name = "net_amount", nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal netAmount;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected WithdrawalQuote() {}

    public WithdrawalQuote(UUID userId, WithdrawalType withdrawalType, BigDecimal grossAmount,
                           UUID feeSettingId, BigDecimal feePct, BigDecimal feeAmount,
                           BigDecimal netAmount, Instant expiresAt, Instant now) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.withdrawalType = withdrawalType;
        this.grossAmount = grossAmount;
        this.feeSettingId = feeSettingId;
        this.feePct = feePct;
        this.feeAmount = feeAmount;
        this.netAmount = netAmount;
        this.expiresAt = expiresAt;
        this.createdAt = now;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public WithdrawalType getWithdrawalType() { return withdrawalType; }
    public BigDecimal getGrossAmount() { return grossAmount; }
    public UUID getFeeSettingId() { return feeSettingId; }
    public BigDecimal getFeePct() { return feePct; }
    public BigDecimal getFeeAmount() { return feeAmount; }
    public BigDecimal getNetAmount() { return netAmount; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getUsedAt() { return usedAt; }
    public Instant getCreatedAt() { return createdAt; }

    public boolean isExpired(Instant now) {
        return now.isAfter(expiresAt);
    }

    public boolean isUsed() {
        return usedAt != null;
    }

    public void markUsed(Instant now) {
        this.usedAt = now;
    }
}
