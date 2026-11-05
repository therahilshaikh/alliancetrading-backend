package in.ruhmans.alliancetrading.payment.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * A company UPI account that users can be asked to pay into. Never edited in place:
 * to change details, archive it and save a new one, so past assignments keep pointing
 * at exactly what the user was shown.
 */
@Entity
@Table(name = "payment_accounts")
public class PaymentAccount {

    @Id
    @Column(updatable = false)
    private UUID id;

    @Column(nullable = false, length = 100, updatable = false)
    private String label;

    @Column(nullable = false, length = 100, updatable = false)
    private String vpa;

    @Column(name = "payee_name", nullable = false, length = 100, updatable = false)
    private String payeeName;

    @Column(name = "qr_document_id", updatable = false)
    private UUID qrDocumentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PaymentAccountStatus status;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "archived_by")
    private UUID archivedBy;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Version
    private Long version;

    protected PaymentAccount() {}

    public PaymentAccount(String label, String vpa, String payeeName, UUID qrDocumentId,
                          UUID createdBy, Instant now) {
        this.id = UUID.randomUUID();
        this.label = label;
        this.vpa = vpa;
        this.payeeName = payeeName;
        this.qrDocumentId = qrDocumentId;
        this.status = PaymentAccountStatus.ACTIVE;
        this.createdBy = createdBy;
        this.createdAt = now;
    }

    public UUID getId() { return id; }
    public String getLabel() { return label; }
    public String getVpa() { return vpa; }
    public String getPayeeName() { return payeeName; }
    public UUID getQrDocumentId() { return qrDocumentId; }
    public PaymentAccountStatus getStatus() { return status; }
    public UUID getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public UUID getArchivedBy() { return archivedBy; }
    public Instant getArchivedAt() { return archivedAt; }

    public boolean isActive() {
        return status == PaymentAccountStatus.ACTIVE;
    }

    public void archive(UUID adminId, Instant now) {
        this.status = PaymentAccountStatus.ARCHIVED;
        this.archivedBy = adminId;
        this.archivedAt = now;
    }
}
