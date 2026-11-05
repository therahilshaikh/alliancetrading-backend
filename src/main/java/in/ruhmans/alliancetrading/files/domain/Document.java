package in.ruhmans.alliancetrading.files.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "documents")
public class Document {

    @Id
    @Column(updatable = false)
    private UUID id;

    @Column(name = "owner_user_id")
    private UUID ownerUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "doc_type", nullable = false, length = 30)
    private DocType docType;

    @Column(nullable = false, length = 100)
    private String bucket;

    @Column(name = "object_key", nullable = false, length = 200)
    private String objectKey;

    /** Cleaned name the file was uploaded with (extension matches the detected type); used only for downloads. */
    @Column(name = "original_filename", length = 255)
    private String originalFilename;

    @Column(nullable = false, length = 50)
    private String mime;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(nullable = false, length = 64)
    private String sha256;

    @Column(name = "wrapped_key", nullable = false)
    private String wrappedKey;

    @Column(name = "key_id", nullable = false, length = 20)
    private String keyId;

    @Column(name = "uploaded_by_type", nullable = false, length = 10)
    private String uploadedByType;

    @Column(name = "uploaded_by_id", nullable = false)
    private UUID uploadedById;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Document() {}

    public Document(UUID ownerUserId, DocType docType, String bucket, String objectKey,
                    String mime, long sizeBytes, String sha256, String wrappedKey,
                    String keyId, String uploadedByType, UUID uploadedById, Instant now) {
        this(UUID.randomUUID(), ownerUserId, docType, bucket, objectKey, mime, sizeBytes, sha256, wrappedKey,
                keyId, uploadedByType, uploadedById, now, null);
    }

    /** {@code id} is chosen up front so the object key can carry the same UUID as the row. */
    public Document(UUID id, UUID ownerUserId, DocType docType, String bucket, String objectKey,
                    String mime, long sizeBytes, String sha256, String wrappedKey,
                    String keyId, String uploadedByType, UUID uploadedById, Instant now,
                    String originalFilename) {
        this.id = id;
        this.originalFilename = originalFilename;
        this.ownerUserId = ownerUserId;
        this.docType = docType;
        this.bucket = bucket;
        this.objectKey = objectKey;
        this.mime = mime;
        this.sizeBytes = sizeBytes;
        this.sha256 = sha256;
        this.wrappedKey = wrappedKey;
        this.keyId = keyId;
        this.uploadedByType = uploadedByType;
        this.uploadedById = uploadedById;
        this.createdAt = now;
    }

    public UUID getId() { return id; }
    public UUID getOwnerUserId() { return ownerUserId; }
    public DocType getDocType() { return docType; }
    public String getBucket() { return bucket; }
    public String getOriginalFilename() { return originalFilename; }
    public String getObjectKey() { return objectKey; }
    public String getMime() { return mime; }
    public long getSizeBytes() { return sizeBytes; }
    public String getSha256() { return sha256; }
    public String getWrappedKey() { return wrappedKey; }
    public String getKeyId() { return keyId; }
    public String getUploadedByType() { return uploadedByType; }
    public UUID getUploadedById() { return uploadedById; }
    public Instant getCreatedAt() { return createdAt; }
}
