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

    @Column(name = "investment_id")
    private UUID investmentId;

    /** The line's total, computed from its targets when the batch was created (re-checked on approval). */
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    /** PERCENTAGE batches: percent of each target's principal, e.g. 1.2500 = 1.25%. */
    @Column(precision = 7, scale = 4)
    private BigDecimal percentage;

    /** FIXED batches: rupees each target investment receives. */
    @Column(name = "amount_per_investment", precision = 15, scale = 2)
    private BigDecimal amountPerInvestment;

    protected PnlLine() {}

    /** Exactly one of {@code percentage} / {@code amountPerInvestment} is set. */
    public PnlLine(UUID batchId, PnlTargetType targetType, UUID investmentId,
                   BigDecimal amount, BigDecimal percentage, BigDecimal amountPerInvestment) {
        this.id = UUID.randomUUID();
        this.batchId = batchId;
        this.targetType = targetType;
        this.investmentId = investmentId;
        this.amount = amount;
        this.percentage = percentage;
        this.amountPerInvestment = amountPerInvestment;
    }

    public UUID getId() { return id; }
    public UUID getBatchId() { return batchId; }
    public PnlTargetType getTargetType() { return targetType; }
    public UUID getInvestmentId() { return investmentId; }
    public BigDecimal getAmount() { return amount; }
    public BigDecimal getPercentage() { return percentage; }
    public BigDecimal getAmountPerInvestment() { return amountPerInvestment; }
}
