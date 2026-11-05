package in.ruhmans.alliancetrading.audit.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_type", nullable = false, length = 10)
    private String actorType;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(nullable = false, length = 100)
    private String action;

    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType;

    @Column(name = "entity_id", nullable = false, length = 64)
    private String entityId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_data", columnDefinition = "jsonb")
    private String beforeData;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "after_data", columnDefinition = "jsonb")
    private String afterData;

    @Column(length = 64)
    private String ip;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "request_id", length = 100)
    private String requestId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AuditLog() {}

    public AuditLog(String actorType, UUID actorId, String action, String entityType,
                    String entityId, String beforeData, String afterData,
                    String ip, String userAgent, String requestId, Instant now) {
        this.actorType = actorType;
        this.actorId = actorId;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.beforeData = beforeData;
        this.afterData = afterData;
        this.ip = ip;
        this.userAgent = userAgent;
        this.requestId = requestId;
        this.createdAt = now;
    }

    public Long getId() { return id; }
    public String getActorType() { return actorType; }
    public UUID getActorId() { return actorId; }
    public String getAction() { return action; }
    public String getEntityType() { return entityType; }
    public String getEntityId() { return entityId; }
    public String getBeforeData() { return beforeData; }
    public String getAfterData() { return afterData; }
    public String getIp() { return ip; }
    public String getUserAgent() { return userAgent; }
    public String getRequestId() { return requestId; }
    public Instant getCreatedAt() { return createdAt; }
}
