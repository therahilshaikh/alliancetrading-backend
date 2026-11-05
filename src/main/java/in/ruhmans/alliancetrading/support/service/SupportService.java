package in.ruhmans.alliancetrading.support.service;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import in.ruhmans.alliancetrading.audit.service.AuditService;
import in.ruhmans.alliancetrading.common.error.ApiException;
import in.ruhmans.alliancetrading.common.error.ErrorCode;
import in.ruhmans.alliancetrading.notification.service.OutboxService;
import in.ruhmans.alliancetrading.support.domain.SupportMessage;
import in.ruhmans.alliancetrading.support.domain.SupportMessage.AuthorType;
import in.ruhmans.alliancetrading.support.domain.SupportTicket;
import in.ruhmans.alliancetrading.support.domain.TicketPriority;
import in.ruhmans.alliancetrading.support.domain.TicketStatus;
import in.ruhmans.alliancetrading.support.repository.SupportMessageRepository;
import in.ruhmans.alliancetrading.support.repository.SupportTicketRepository;
import in.ruhmans.alliancetrading.user.repository.UserRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Member support conversations. A member reply on a resolved ticket reopens it;
 * admin replies notify the member by push.
 */
@Service
public class SupportService {

    public record TicketThread(SupportTicket ticket, List<SupportMessage> messages) {}

    private final SupportTicketRepository ticketRepository;
    private final SupportMessageRepository messageRepository;
    private final UserRepository userRepository;
    private final OutboxService outboxService;
    private final AuditService auditService;
    private final Clock clock;

    public SupportService(SupportTicketRepository ticketRepository, SupportMessageRepository messageRepository,
                          UserRepository userRepository, OutboxService outboxService,
                          AuditService auditService, Clock clock) {
        this.ticketRepository = ticketRepository;
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.outboxService = outboxService;
        this.auditService = auditService;
        this.clock = clock;
    }

    // ---- Member ----

    @Transactional
    public TicketThread open(UUID userId, String subject, String message) {
        if (!userRepository.existsById(userId)) {
            throw new ApiException(ErrorCode.NOT_FOUND, "User not found");
        }
        Instant now = clock.instant();
        SupportTicket ticket = ticketRepository.save(new SupportTicket(userId, subject.trim(), now));
        SupportMessage first = messageRepository.save(
                new SupportMessage(ticket.getId(), AuthorType.USER, userId, message.trim(), now));
        return new TicketThread(ticket, List.of(first));
    }

    @Transactional(readOnly = true)
    public List<SupportTicket> listOwn(UUID userId) {
        return ticketRepository.findByUserIdOrderByLastMessageAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public TicketThread getOwn(UUID userId, UUID ticketId) {
        SupportTicket ticket = requireOwnTicket(userId, ticketId);
        return new TicketThread(ticket, messageRepository.findByTicketIdOrderByCreatedAtAsc(ticketId));
    }

    @Transactional
    public TicketThread replyAsUser(UUID userId, UUID ticketId, String body) {
        SupportTicket ticket = requireOwnTicket(userId, ticketId);
        if (ticket.getStatus() == TicketStatus.RESOLVED) {
            ticket.reopen();
        }
        addMessage(ticket, AuthorType.USER, userId, body);
        return new TicketThread(ticket, messageRepository.findByTicketIdOrderByCreatedAtAsc(ticketId));
    }

    // ---- Admin ----

    /** {@code status} / {@code priority} null means any. */
    @Transactional(readOnly = true)
    public Page<SupportTicket> queue(TicketStatus status, TicketPriority priority, int page, int size) {
        return ticketRepository.findQueue(
                status != null ? EnumSet.of(status) : EnumSet.allOf(TicketStatus.class),
                priority != null ? EnumSet.of(priority) : EnumSet.allOf(TicketPriority.class),
                PageRequest.of(Math.max(page, 0), size));
    }

    @Transactional(readOnly = true)
    public TicketThread get(UUID ticketId) {
        SupportTicket ticket = requireTicket(ticketId);
        return new TicketThread(ticket, messageRepository.findByTicketIdOrderByCreatedAtAsc(ticketId));
    }

    @Transactional
    public TicketThread replyAsAdmin(UUID adminId, UUID ticketId, String body) {
        SupportTicket ticket = requireTicket(ticketId);
        addMessage(ticket, AuthorType.ADMIN, adminId, body);
        outboxService.notifyUser("SUPPORT_REPLY", ticket.getUserId(),
                AuditService.json("ticketId", ticketId));
        auditService.recordAdmin(adminId, "SUPPORT_REPLIED", "support_ticket", ticketId.toString(), null, null);
        return new TicketThread(ticket, messageRepository.findByTicketIdOrderByCreatedAtAsc(ticketId));
    }

    @Transactional
    public SupportTicket resolve(UUID adminId, UUID ticketId) {
        SupportTicket ticket = requireTicket(ticketId);
        if (ticket.getStatus() == TicketStatus.RESOLVED) {
            throw new ApiException(ErrorCode.ILLEGAL_STATE_TRANSITION, "Ticket is already resolved");
        }
        ticket.resolve(adminId, clock.instant());
        auditService.recordAdmin(adminId, "SUPPORT_RESOLVED", "support_ticket", ticketId.toString(), null, null);
        return ticketRepository.save(ticket);
    }

    @Transactional
    public SupportTicket reopen(UUID adminId, UUID ticketId) {
        SupportTicket ticket = requireTicket(ticketId);
        if (ticket.getStatus() == TicketStatus.OPEN) {
            throw new ApiException(ErrorCode.ILLEGAL_STATE_TRANSITION, "Ticket is already open");
        }
        ticket.reopen();
        auditService.recordAdmin(adminId, "SUPPORT_REOPENED", "support_ticket", ticketId.toString(), null, null);
        return ticketRepository.save(ticket);
    }

    @Transactional
    public SupportTicket setPriority(UUID adminId, UUID ticketId, TicketPriority priority) {
        SupportTicket ticket = requireTicket(ticketId);
        TicketPriority previous = ticket.getPriority();
        ticket.setPriority(priority);
        auditService.recordAdmin(adminId, "SUPPORT_PRIORITY_CHANGED", "support_ticket", ticketId.toString(),
                AuditService.json("priority", previous), AuditService.json("priority", priority));
        return ticketRepository.save(ticket);
    }

    // ---- Internal ----

    private void addMessage(SupportTicket ticket, AuthorType authorType, UUID authorId, String body) {
        Instant now = clock.instant();
        messageRepository.save(new SupportMessage(ticket.getId(), authorType, authorId, body.trim(), now));
        ticket.touch(now);
        ticketRepository.save(ticket);
    }

    private SupportTicket requireTicket(UUID ticketId) {
        return ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Support ticket not found"));
    }

    private SupportTicket requireOwnTicket(UUID userId, UUID ticketId) {
        SupportTicket ticket = requireTicket(ticketId);
        if (!ticket.getUserId().equals(userId)) {
            // Same response as a missing ticket, so ids of other members' tickets are not confirmed
            throw new ApiException(ErrorCode.NOT_FOUND, "Support ticket not found");
        }
        return ticket;
    }
}
