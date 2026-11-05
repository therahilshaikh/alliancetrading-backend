package in.ruhmans.alliancetrading.support.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import in.ruhmans.alliancetrading.support.domain.SupportMessage;
import in.ruhmans.alliancetrading.support.domain.SupportTicket;
import in.ruhmans.alliancetrading.support.domain.TicketPriority;
import in.ruhmans.alliancetrading.support.domain.TicketStatus;
import in.ruhmans.alliancetrading.support.service.SupportService;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request/response shapes shared by the member and admin support endpoints. */
public final class SupportDtos {

    private SupportDtos() {}

    public record OpenTicketRequest(@NotBlank @Size(max = 150) String subject,
                                    @NotBlank @Size(max = 4000) String message) {}

    public record ReplyRequest(@NotBlank @Size(max = 4000) String message) {}

    public record TicketSummary(UUID id, UUID userId, String userName, String userMobile, String subject,
                                TicketStatus status, TicketPriority priority, Instant createdAt,
                                Instant lastMessageAt, Instant resolvedAt) {

        public static TicketSummary of(SupportTicket t, String userName, String userMobile) {
            return new TicketSummary(t.getId(), t.getUserId(), userName, userMobile, t.getSubject(),
                    t.getStatus(), t.getPriority(), t.getCreatedAt(), t.getLastMessageAt(), t.getResolvedAt());
        }
    }

    public record MessageView(UUID id, SupportMessage.AuthorType authorType, String body, Instant createdAt) {

        public static MessageView of(SupportMessage m) {
            return new MessageView(m.getId(), m.getAuthorType(), m.getBody(), m.getCreatedAt());
        }
    }

    public record ThreadView(TicketSummary ticket, List<MessageView> messages) {

        public static ThreadView of(SupportService.TicketThread thread, String userName, String userMobile) {
            return new ThreadView(TicketSummary.of(thread.ticket(), userName, userMobile),
                    thread.messages().stream().map(MessageView::of).toList());
        }
    }
}
