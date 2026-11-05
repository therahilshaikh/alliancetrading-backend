package in.ruhmans.alliancetrading.notification.domain;

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
@Table(name = "outbox")
public class OutboxMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "recipient_type", nullable = false, length = 10)
    private String recipientType;

    @Column(name = "recipient_id", nullable = false)
    private UUID recipientId;

    @Column(nullable = false, length = 10)
    private String channel;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(nullable = false, length = 12)
    private String status = "PENDING";

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected OutboxMessage() {}

    public OutboxMessage(String eventType, String recipientType, UUID recipientId,
                         String channel, String payload, Instant now) {
        this.eventType = eventType;
        this.recipientType = recipientType;
        this.recipientId = recipientId;
        this.channel = channel;
        this.payload = payload;
        this.nextAttemptAt = now;
        this.createdAt = now;
    }

    public Long getId() { return id; }
    public String getEventType() { return eventType; }
    public String getRecipientType() { return recipientType; }
    public UUID getRecipientId() { return recipientId; }
    public String getChannel() { return channel; }
    public String getPayload() { return payload; }
    public String getStatus() { return status; }
    public int getAttempts() { return attempts; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public Instant getSentAt() { return sentAt; }
    public Instant getCreatedAt() { return createdAt; }

    public void markSent(Instant now) {
        this.status = "SENT";
        this.sentAt = now;
    }

    public void markFailed(Instant nextRetry) {
        this.attempts++;
        this.nextAttemptAt = nextRetry;
        if (attempts >= 10) {
            this.status = "FAILED";
        }
    }
}
