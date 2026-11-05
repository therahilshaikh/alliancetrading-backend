package in.ruhmans.alliancetrading.pnl.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "pnl_allocations")
public class PnlAllocation {

    @Id
    @Column(updatable = false)
    private UUID id;

    @Column(name = "batch_id", nullable = false, updatable = false)
    private UUID batchId;

    @Enumerated(EnumType.STRING)
    @Column(name = "batch_type", nullable = false, length = 12, updatable = false)
    private PnlBatchType batchType;

    @Column(name = "investment_id", nullable = false, updatable = false)
    private UUID investmentId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "session_date", nullable = false, updatable = false)
    private LocalDate sessionDate;

    @Column(nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PnlAllocation() {}

    public PnlAllocation(UUID batchId, PnlBatchType batchType, UUID investmentId, UUID userId,
                         LocalDate sessionDate, BigDecimal amount, Instant now) {
        this.id = UUID.randomUUID();
        this.batchId = batchId;
        this.batchType = batchType;
        this.investmentId = investmentId;
        this.userId = userId;
        this.sessionDate = sessionDate;
        this.amount = amount;
        this.createdAt = now;
    }

    public UUID getId() { return id; }
    public UUID getBatchId() { return batchId; }
    public PnlBatchType getBatchType() { return batchType; }
    public UUID getInvestmentId() { return investmentId; }
    public UUID getUserId() { return userId; }
    public LocalDate getSessionDate() { return sessionDate; }
    public BigDecimal getAmount() { return amount; }
    public Instant getCreatedAt() { return createdAt; }
}
