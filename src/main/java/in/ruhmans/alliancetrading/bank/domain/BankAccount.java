package in.ruhmans.alliancetrading.bank.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "bank_accounts")
public class BankAccount {

    @Id
    @Column(updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "holder_name", nullable = false, length = 200)
    private String holderName;

    @Column(name = "account_number_enc", nullable = false)
    private String accountNumberEnc;

    @Column(name = "account_number_hash", nullable = false, length = 64)
    private String accountNumberHash;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "account_number_last4", nullable = false, length = 4)
    private String accountNumberLast4;

    @Column(nullable = false, length = 11)
    private String ifsc;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 10)
    private BankAccountType accountType;

    @Column(length = 100)
    private String vpa;

    @Column(name = "proof_document_id")
    private UUID proofDocumentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private BankAccountStatus status;

    @Column(name = "cooloff_until")
    private Instant cooloffUntil;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    protected BankAccount() {}

    public BankAccount(UUID userId, String holderName, String accountNumberEnc,
                       String accountNumberHash, String accountNumberLast4, String ifsc,
                       BankAccountType accountType, String vpa, UUID proofDocumentId,
                       Instant cooloffUntil, Instant now) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.holderName = holderName;
        this.accountNumberEnc = accountNumberEnc;
        this.accountNumberHash = accountNumberHash;
        this.accountNumberLast4 = accountNumberLast4;
        this.ifsc = ifsc;
        this.accountType = accountType;
        this.vpa = vpa;
        this.proofDocumentId = proofDocumentId;
        this.status = BankAccountStatus.PENDING_VERIFICATION;
        this.cooloffUntil = cooloffUntil;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getHolderName() { return holderName; }
    public String getAccountNumberEnc() { return accountNumberEnc; }
    public String getAccountNumberHash() { return accountNumberHash; }
    public String getAccountNumberLast4() { return accountNumberLast4; }
    public String getIfsc() { return ifsc; }
    public BankAccountType getAccountType() { return accountType; }
    public String getVpa() { return vpa; }
    public UUID getProofDocumentId() { return proofDocumentId; }
    public BankAccountStatus getStatus() { return status; }
    public Instant getCooloffUntil() { return cooloffUntil; }
    public String getRejectionReason() { return rejectionReason; }
    public UUID getReviewedBy() { return reviewedBy; }
    public Instant getReviewedAt() { return reviewedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void markVerified(UUID reviewerId, Instant now) {
        this.status = BankAccountStatus.VERIFIED;
        this.reviewedBy = reviewerId;
        this.reviewedAt = now;
        this.updatedAt = now;
    }

    public void markRejected(UUID reviewerId, String reason, Instant now) {
        this.status = BankAccountStatus.REJECTED;
        this.reviewedBy = reviewerId;
        this.reviewedAt = now;
        this.rejectionReason = reason;
        this.updatedAt = now;
    }

    public void markReplaced(Instant now) {
        this.status = BankAccountStatus.REPLACED;
        this.updatedAt = now;
    }
}
