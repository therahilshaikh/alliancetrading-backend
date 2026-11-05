package in.ruhmans.alliancetrading.ledger.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Append-only ledger row. UPDATE/DELETE are blocked by a DB trigger.
 */
@Entity
@Table(name = "ledger_entries")
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "journal_id", nullable = false, updatable = false)
    private UUID journalId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "investment_id", updatable = false)
    private UUID investmentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, updatable = false)
    private Bucket bucket;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 25, updatable = false)
    private EntryType entryType;

    @Column(nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(name = "source_type", nullable = false, length = 50, updatable = false)
    private String sourceType;

    @Column(name = "source_id", nullable = false, length = 64, updatable = false)
    private String sourceId;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 100, updatable = false)
    private String idempotencyKey;

    @Column(name = "created_by_type", nullable = false, length = 10, updatable = false)
    private String createdByType;

    @Column(name = "created_by_id", updatable = false)
    private UUID createdById;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected LedgerEntry() {}

    public LedgerEntry(UUID journalId, UUID userId, UUID investmentId, Bucket bucket,
                       EntryType entryType, BigDecimal amount, String sourceType, String sourceId,
                       String idempotencyKey, String createdByType, UUID createdById, Instant now) {
        this.journalId = journalId;
        this.userId = userId;
        this.investmentId = investmentId;
        this.bucket = bucket;
        this.entryType = entryType;
        this.amount = amount;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.idempotencyKey = idempotencyKey;
        this.createdByType = createdByType;
        this.createdById = createdById;
        this.createdAt = now;
    }

    public Long getId() { return id; }
    public UUID getJournalId() { return journalId; }
    public UUID getUserId() { return userId; }
    public UUID getInvestmentId() { return investmentId; }
    public Bucket getBucket() { return bucket; }
    public EntryType getEntryType() { return entryType; }
    public BigDecimal getAmount() { return amount; }
    public String getSourceType() { return sourceType; }
    public String getSourceId() { return sourceId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getCreatedByType() { return createdByType; }
    public UUID getCreatedById() { return createdById; }
    public Instant getCreatedAt() { return createdAt; }
}
