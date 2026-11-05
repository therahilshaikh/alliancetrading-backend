package in.ruhmans.alliancetrading.settings.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * A saved default payment: the amount and the saved payment account members pay into. The UPI ID and QR
 * are read from the account. The row with the latest {@code effectiveFrom <= now} is in force.
 */
@Entity
@Table(name = "payment_settings")
public class PaymentSetting {

    @Id
    @Column(updatable = false)
    private UUID id;

    @Column(name = "fixed_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal fixedAmount;

    @Column(name = "payment_account_id", nullable = false, updatable = false)
    private UUID paymentAccountId;

    @Column(name = "effective_from", nullable = false)
    private Instant effectiveFrom;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Version
    private Long version;

    protected PaymentSetting() {}

    public PaymentSetting(BigDecimal fixedAmount, UUID paymentAccountId,
                          Instant effectiveFrom, UUID createdBy, Instant now) {
        this.id = UUID.randomUUID();
        this.fixedAmount = fixedAmount;
        this.paymentAccountId = paymentAccountId;
        this.effectiveFrom = effectiveFrom;
        this.createdBy = createdBy;
        this.createdAt = now;
    }

    public UUID getId() { return id; }
    public BigDecimal getFixedAmount() { return fixedAmount; }
    public UUID getPaymentAccountId() { return paymentAccountId; }
    public Instant getEffectiveFrom() { return effectiveFrom; }
    public UUID getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
}
