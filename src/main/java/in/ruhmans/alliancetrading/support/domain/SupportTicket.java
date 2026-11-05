package in.ruhmans.alliancetrading.support.domain;

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
@Table(name = "support_tickets")
public class SupportTicket {

    @Id
    @Column(updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, length = 150, updatable = false)
    private String subject;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TicketStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TicketPriority priority;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_message_at", nullable = false)
    private Instant lastMessageAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolved_by")
    private UUID resolvedBy;

    @Version
    private Long version;

    protected SupportTicket() {}

    public SupportTicket(UUID userId, String subject, Instant now) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.subject = subject;
        this.status = TicketStatus.OPEN;
        this.priority = TicketPriority.NORMAL;
        this.createdAt = now;
        this.lastMessageAt = now;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getSubject() { return subject; }
    public TicketStatus getStatus() { return status; }
    public TicketPriority getPriority() { return priority; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastMessageAt() { return lastMessageAt; }
    public Instant getResolvedAt() { return resolvedAt; }
    public UUID getResolvedBy() { return resolvedBy; }

    public void touch(Instant now) {
        this.lastMessageAt = now;
    }

    public void resolve(UUID adminId, Instant now) {
        this.status = TicketStatus.RESOLVED;
        this.resolvedBy = adminId;
        this.resolvedAt = now;
    }

    public void reopen() {
        this.status = TicketStatus.OPEN;
        this.resolvedBy = null;
        this.resolvedAt = null;
    }

    public void setPriority(TicketPriority priority) {
        this.priority = priority;
    }
}
