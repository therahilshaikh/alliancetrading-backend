package in.ruhmans.alliancetrading.kyc.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "kyc_profiles")
public class KycProfile {

    @Id
    @Column(updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true, updatable = false)
    private UUID userId;

    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    @Column(nullable = false)
    private LocalDate dob;

    @Column(name = "pan_enc", nullable = false)
    private String panEnc;

    @Column(name = "pan_hash", nullable = false, unique = true, length = 64)
    private String panHash;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "pan_last4", nullable = false, length = 4)
    private String panLast4;

    @Column(name = "aadhaar_enc")
    private String aadhaarEnc;

    @Column(name = "aadhaar_hash", length = 64)
    private String aadhaarHash;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "aadhaar_last4", nullable = false, length = 4)
    private String aadhaarLast4;

    @Column(name = "pan_card_document_id")
    private UUID panCardDocumentId;

    @Column(name = "aadhaar_front_document_id")
    private UUID aadhaarFrontDocumentId;

    @Column(name = "aadhaar_back_document_id")
    private UUID aadhaarBackDocumentId;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    protected KycProfile() {}

    public KycProfile(UUID userId, Instant now) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** Encrypted identity numbers plus their uniqueness hashes and display suffixes. */
    public record IdentityNumbers(String panEnc, String panHash, String panLast4,
                                  String aadhaarEnc, String aadhaarHash, String aadhaarLast4) {}

    /** Optional supporting documents; all null until document uploads are reintroduced. */
    public record SupportingDocuments(UUID panCardDocumentId, UUID aadhaarFrontDocumentId,
                                      UUID aadhaarBackDocumentId) {
        public static final SupportingDocuments NONE = new SupportingDocuments(null, null, null);
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getFullName() { return fullName; }
    public LocalDate getDob() { return dob; }
    public String getPanEnc() { return panEnc; }
    public String getPanHash() { return panHash; }
    public String getPanLast4() { return panLast4; }
    public String getAadhaarEnc() { return aadhaarEnc; }
    public String getAadhaarHash() { return aadhaarHash; }
    public String getAadhaarLast4() { return aadhaarLast4; }
    public UUID getPanCardDocumentId() { return panCardDocumentId; }
    public UUID getAadhaarFrontDocumentId() { return aadhaarFrontDocumentId; }
    public UUID getAadhaarBackDocumentId() { return aadhaarBackDocumentId; }
    public UUID getReviewedBy() { return reviewedBy; }
    public Instant getReviewedAt() { return reviewedAt; }
    public String getRejectionReason() { return rejectionReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void fill(String fullName, LocalDate dob, IdentityNumbers ids,
                     SupportingDocuments docs, Instant now) {
        this.fullName = fullName;
        this.dob = dob;
        this.panEnc = ids.panEnc();
        this.panHash = ids.panHash();
        this.panLast4 = ids.panLast4();
        this.aadhaarEnc = ids.aadhaarEnc();
        this.aadhaarHash = ids.aadhaarHash();
        this.aadhaarLast4 = ids.aadhaarLast4();
        this.panCardDocumentId = docs.panCardDocumentId();
        this.aadhaarFrontDocumentId = docs.aadhaarFrontDocumentId();
        this.aadhaarBackDocumentId = docs.aadhaarBackDocumentId();
        this.reviewedBy = null;
        this.reviewedAt = null;
        this.rejectionReason = null;
        this.updatedAt = now;
    }

    public void markReviewed(UUID reviewerId, String rejectionReason, Instant now) {
        this.reviewedBy = reviewerId;
        this.reviewedAt = now;
        this.rejectionReason = rejectionReason;
        this.updatedAt = now;
    }
}
