package in.ruhmans.alliancetrading.payment.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Which payment account and amount a user must pay for their investment.
 * Reassigning deactivates the previous row instead of updating it.
 */
@Entity
@Table(name = "user_payment_assignments")
public class UserPaymentAssignment {

    @Id
    @Column(updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "payment_account_id", nullable = false, updatable = false)
    private UUID paymentAccountId;

    @Column(nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "assigned_by", nullable = false, updatable = false)
    private UUID assignedBy;

    @Column(name = "assigned_at", nullable = false, updatable = false)
    private Instant assignedAt;

    @Column(name = "deactivated_at")
    private Instant deactivatedAt;

    @Version
    private Long version;

    protected UserPaymentAssignment() {}

    public UserPaymentAssignment(UUID userId, UUID paymentAccountId, BigDecimal amount,
                                 UUID assignedBy, Instant now) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.paymentAccountId = paymentAccountId;
        this.amount = amount;
        this.active = true;
        this.assignedBy = assignedBy;
        this.assignedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public UUID getPaymentAccountId() { return paymentAccountId; }
    public BigDecimal getAmount() { return amount; }
    public boolean isActive() { return active; }
    public UUID getAssignedBy() { return assignedBy; }
    public Instant getAssignedAt() { return assignedAt; }
    public Instant getDeactivatedAt() { return deactivatedAt; }

    public void deactivate(Instant now) {
        this.active = false;
        this.deactivatedAt = now;
    }
}
