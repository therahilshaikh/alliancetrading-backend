package in.ruhmans.alliancetrading.pnl.domain;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "pnl_lines")
public class PnlLine {

    @Id
    @Column(updatable = false)
    private UUID id;

    @Column(name = "batch_id", nullable = false, updatable = false)
    private UUID batchId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 12)
    private PnlTargetType targetType;

    /** MEMBER lines only. */
    @Column(name = "user_id")
    private UUID userId;

    /** The line's total, computed from its targets when the batch was created (re-checked on approval). */
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    /** PERCENTAGE batches: percent of each member's total remaining principal, e.g. 1.2500 = 1.25%. */
    @Column(precision = 7, scale = 4)
    private BigDecimal percentage;

    /** FIXED batches: rupees each target member receives, however many investments they hold. */
    @Column(name = "amount_per_member", precision = 15, scale = 2)
    private BigDecimal amountPerMember;

    protected PnlLine() {}

    /** Exactly one of {@code percentage} / {@code amountPerMember} is set. */
    public PnlLine(UUID batchId, PnlTargetType targetType, UUID userId,
                   BigDecimal amount, BigDecimal percentage, BigDecimal amountPerMember) {
        this.id = UUID.randomUUID();
        this.batchId = batchId;
        this.targetType = targetType;
        this.userId = userId;
        this.amount = amount;
        this.percentage = percentage;
        this.amountPerMember = amountPerMember;
    }

    public UUID getId() { return id; }
    public UUID getBatchId() { return batchId; }
    public PnlTargetType getTargetType() { return targetType; }
    public UUID getUserId() { return userId; }
    public BigDecimal getAmount() { return amount; }
    public BigDecimal getPercentage() { return percentage; }
    public BigDecimal getAmountPerMember() { return amountPerMember; }
}
