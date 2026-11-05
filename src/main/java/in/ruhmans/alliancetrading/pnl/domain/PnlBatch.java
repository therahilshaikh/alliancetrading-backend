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
import jakarta.persistence.Version;

@Entity
@Table(name = "pnl_batches")
public class PnlBatch {

    @Id
    @Column(updatable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "batch_type", nullable = false, length = 12)
    private PnlBatchType batchType;

    @Column(name = "session_date", nullable = false)
    private LocalDate sessionDate;

    @Column(name = "original_batch_id")
    private UUID originalBatchId;

    @Column(name = "control_total", nullable = false, precision = 15, scale = 2)
    private BigDecimal controlTotal;

    @Column(name = "evidence_document_id")
    private UUID evidenceDocumentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private PnlBatchStatus status;

    @Column(name = "maker_id", nullable = false, updatable = false)
    private UUID makerId;

    /** The admin the maker chose to approve this batch; nullable in the schema, but always set by PnlService. */
    @Column(name = "assigned_checker_id", updatable = false)
    private UUID assignedCheckerId;

    @Column(name = "checker_id")
    private UUID checkerId;

    @Column(length = 1000)
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Version
    private Long version;

    @Enumerated(EnumType.STRING)
    @Column(name = "value_type", nullable = false, length = 12, updatable = false)
    private PnlValueType valueType;

    protected PnlBatch() {}

    public PnlBatch(PnlBatchType batchType, PnlValueType valueType, LocalDate sessionDate, UUID originalBatchId,
                    BigDecimal controlTotal, UUID evidenceDocumentId, UUID makerId,
                    UUID assignedCheckerId, String notes, Instant now) {
        this.id = UUID.randomUUID();
        this.batchType = batchType;
        this.valueType = valueType;
        this.sessionDate = sessionDate;
        this.originalBatchId = originalBatchId;
        this.controlTotal = controlTotal;
        this.evidenceDocumentId = evidenceDocumentId;
        this.makerId = makerId;
        this.assignedCheckerId = assignedCheckerId;
        this.notes = notes;
        this.status = PnlBatchStatus.DRAFT;
        this.createdAt = now;
    }

    public UUID getId() { return id; }
    public PnlBatchType getBatchType() { return batchType; }
    public PnlValueType getValueType() { return valueType; }
    public LocalDate getSessionDate() { return sessionDate; }
    public UUID getOriginalBatchId() { return originalBatchId; }
    public BigDecimal getControlTotal() { return controlTotal; }
    public UUID getEvidenceDocumentId() { return evidenceDocumentId; }
    public PnlBatchStatus getStatus() { return status; }
    public UUID getMakerId() { return makerId; }
    public UUID getAssignedCheckerId() { return assignedCheckerId; }
    public UUID getCheckerId() { return checkerId; }
    public String getNotes() { return notes; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getApprovedAt() { return approvedAt; }

    /** Set from the computed line totals before the batch is saved. */
    public void setControlTotal(BigDecimal controlTotal) {
        this.controlTotal = controlTotal;
    }

    public void approve(UUID checkerId, Instant now) {
        this.status = PnlBatchStatus.APPROVED;
        this.checkerId = checkerId;
        this.approvedAt = now;
    }

    public void reject(UUID checkerId, Instant now) {
        this.status = PnlBatchStatus.REJECTED;
        this.checkerId = checkerId;
        this.approvedAt = now;
    }
}
