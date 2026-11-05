package in.ruhmans.alliancetrading.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import in.ruhmans.alliancetrading.audit.service.AuditService;
import in.ruhmans.alliancetrading.common.error.ApiException;
import in.ruhmans.alliancetrading.common.error.ErrorCode;
import in.ruhmans.alliancetrading.notification.service.OutboxService;
import in.ruhmans.alliancetrading.support.domain.SupportMessage;
import in.ruhmans.alliancetrading.support.domain.SupportTicket;
import in.ruhmans.alliancetrading.support.domain.TicketStatus;
import in.ruhmans.alliancetrading.support.repository.SupportMessageRepository;
import in.ruhmans.alliancetrading.support.repository.SupportTicketRepository;
import in.ruhmans.alliancetrading.support.service.SupportService;
import in.ruhmans.alliancetrading.user.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class SupportServiceTest {

    private SupportTicketRepository ticketRepository;
    private SupportMessageRepository messageRepository;
    private OutboxService outboxService;
    private SupportService supportService;
    private Clock clock;

    @BeforeEach
    void setUp() {
        ticketRepository = Mockito.mock(SupportTicketRepository.class);
        messageRepository = Mockito.mock(SupportMessageRepository.class);
        outboxService = Mockito.mock(OutboxService.class);
        UserRepository userRepository = Mockito.mock(UserRepository.class);
        when(userRepository.existsById(any())).thenReturn(true);
        when(ticketRepository.save(any(SupportTicket.class))).thenAnswer(i -> i.getArgument(0));
        when(messageRepository.save(any(SupportMessage.class))).thenAnswer(i -> i.getArgument(0));
        when(messageRepository.findByTicketIdOrderByCreatedAtAsc(any())).thenReturn(List.of());
        clock = Clock.fixed(Instant.now(), ZoneId.of("Asia/Kolkata"));

        supportService = new SupportService(ticketRepository, messageRepository, userRepository,
                outboxService, Mockito.mock(AuditService.class), clock);
    }

    private SupportTicket ticketOf(UUID userId) {
        SupportTicket ticket = new SupportTicket(userId, "Withdrawal delay", clock.instant());
        when(ticketRepository.findById(ticket.getId())).thenReturn(Optional.of(ticket));
        return ticket;
    }

    @Test
    void openCreatesTicketWithFirstMessage() {
        UUID userId = UUID.randomUUID();

        var thread = supportService.open(userId, "  Withdrawal delay ", "Still in review?");

        assertThat(thread.ticket().getSubject()).isEqualTo("Withdrawal delay");
        assertThat(thread.ticket().getStatus()).isEqualTo(TicketStatus.OPEN);
        assertThat(thread.messages()).singleElement()
                .satisfies(m -> assertThat(m.getAuthorType()).isEqualTo(SupportMessage.AuthorType.USER));
    }

    @Test
    void membersCannotSeeOtherMembersTickets() {
        SupportTicket ticket = ticketOf(UUID.randomUUID());

        assertThatThrownBy(() -> supportService.getOwn(UUID.randomUUID(), ticket.getId()))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).code()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    void memberReplyReopensResolvedTicket() {
        UUID userId = UUID.randomUUID();
        SupportTicket ticket = ticketOf(userId);
        ticket.resolve(UUID.randomUUID(), clock.instant());

        supportService.replyAsUser(userId, ticket.getId(), "Not fixed yet");

        assertThat(ticket.getStatus()).isEqualTo(TicketStatus.OPEN);
        assertThat(ticket.getResolvedAt()).isNull();
    }

    @Test
    void adminReplyNotifiesMemberByPushOnly() {
        UUID userId = UUID.randomUUID();
        SupportTicket ticket = ticketOf(userId);

        supportService.replyAsAdmin(UUID.randomUUID(), ticket.getId(), "Released today");

        verify(outboxService).notifyUser(eq("SUPPORT_REPLY"), eq(userId), any());
    }

    @Test
    void resolveTwiceFails() {
        SupportTicket ticket = ticketOf(UUID.randomUUID());
        UUID admin = UUID.randomUUID();
        supportService.resolve(admin, ticket.getId());

        assertThatThrownBy(() -> supportService.resolve(admin, ticket.getId()))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).code()).isEqualTo(ErrorCode.ILLEGAL_STATE_TRANSITION));
    }
}
