package in.ruhmans.alliancetrading.support.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One message in a ticket thread. Messages are immutable once written. */
@Entity
@Table(name = "support_messages")
public class SupportMessage {

    public enum AuthorType { USER, ADMIN }

    @Id
    @Column(updatable = false)
    private UUID id;

    @Column(name = "ticket_id", nullable = false, updatable = false)
    private UUID ticketId;

    @Enumerated(EnumType.STRING)
    @Column(name = "author_type", nullable = false, length = 10, updatable = false)
    private AuthorType authorType;

    @Column(name = "author_id", nullable = false, updatable = false)
    private UUID authorId;

    @Column(nullable = false, length = 4000, updatable = false)
    private String body;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected SupportMessage() {}

    public SupportMessage(UUID ticketId, AuthorType authorType, UUID authorId, String body, Instant now) {
        this.id = UUID.randomUUID();
        this.ticketId = ticketId;
        this.authorType = authorType;
        this.authorId = authorId;
        this.body = body;
        this.createdAt = now;
    }

    public UUID getId() { return id; }
    public UUID getTicketId() { return ticketId; }
    public AuthorType getAuthorType() { return authorType; }
    public UUID getAuthorId() { return authorId; }
    public String getBody() { return body; }
    public Instant getCreatedAt() { return createdAt; }
}
