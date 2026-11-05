package in.ruhmans.alliancetrading.investment.domain;

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

@Entity
@Table(name = "investments")
public class Investment {

    @Id
    @Column(updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    /** Legacy rows only; new investments reference a payment assignment instead. */
    @Column(name = "payment_setting_id", updatable = false)
    private UUID paymentSettingId;

    @Column(name = "payment_assignment_id", updatable = false)
    private UUID paymentAssignmentId;

    @Column(name = "proof_document_id", nullable = false)
    private UUID proofDocumentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    private InvestmentStatus status;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    protected Investment() {}

    public Investment(UUID userId, BigDecimal amount, UUID paymentAssignmentId,
                      UUID proofDocumentId, Instant now) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.amount = amount;
        this.paymentAssignmentId = paymentAssignmentId;
        this.proofDocumentId = proofDocumentId;
        this.status = InvestmentStatus.PENDING_VERIFICATION;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public BigDecimal getAmount() { return amount; }
    public UUID getPaymentSettingId() { return paymentSettingId; }
    public UUID getPaymentAssignmentId() { return paymentAssignmentId; }
    public UUID getProofDocumentId() { return proofDocumentId; }
    public InvestmentStatus getStatus() { return status; }
    public String getRejectionReason() { return rejectionReason; }
    public UUID getApprovedBy() { return approvedBy; }
    public Instant getApprovedAt() { return approvedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void approve(UUID adminId, Instant now) {
        this.status = InvestmentStatus.ACTIVE;
        this.approvedBy = adminId;
        this.approvedAt = now;
        this.updatedAt = now;
    }

    public void reject(String reason, Instant now) {
        this.status = InvestmentStatus.REJECTED;
        this.rejectionReason = reason;
        this.updatedAt = now;
    }

    public void requestExit(Instant now) {
        this.status = InvestmentStatus.EXIT_REQUESTED;
        this.updatedAt = now;
    }

    public void close(Instant now) {
        this.status = InvestmentStatus.CLOSED;
        this.updatedAt = now;
    }
}
