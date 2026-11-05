package in.ruhmans.alliancetrading.common.idempotency;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "idempotency_keys")
@IdClass(IdempotencyRecordId.class)
public class IdempotencyRecord {

    @Id
    @Column(name = "scope")
    private String scope;

    @Id
    @Column(name = "key")
    private String key;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "request_hash", nullable = false)
    private String requestHash;

    @Column(name = "response_status")
    private Integer responseStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_body", columnDefinition = "jsonb")
    private String responseBody;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected IdempotencyRecord() {}

    public IdempotencyRecord(String scope, String key, UUID actorId, String requestHash, Instant createdAt) {
        this.scope = scope;
        this.key = key;
        this.actorId = actorId;
        this.requestHash = requestHash;
        this.createdAt = createdAt;
    }

    public String getScope() { return scope; }
    public String getKey() { return key; }
    public UUID getActorId() { return actorId; }
    public String getRequestHash() { return requestHash; }
    public Integer getResponseStatus() { return responseStatus; }
    public String getResponseBody() { return responseBody; }
    public Instant getCreatedAt() { return createdAt; }

    public void complete(int responseStatus, String responseBody) {
        this.responseStatus = responseStatus;
        this.responseBody = responseBody;
    }
}
