package in.ruhmans.alliancetrading.support.api;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import in.ruhmans.alliancetrading.common.pagination.PageResponse;
import in.ruhmans.alliancetrading.common.security.SecurityUtils;
import in.ruhmans.alliancetrading.support.api.SupportDtos.ReplyRequest;
import in.ruhmans.alliancetrading.support.api.SupportDtos.ThreadView;
import in.ruhmans.alliancetrading.support.api.SupportDtos.TicketSummary;
import in.ruhmans.alliancetrading.support.domain.SupportTicket;
import in.ruhmans.alliancetrading.support.domain.TicketPriority;
import in.ruhmans.alliancetrading.support.domain.TicketStatus;
import in.ruhmans.alliancetrading.support.service.SupportService;
import in.ruhmans.alliancetrading.user.service.UserDirectory;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/support/tickets")
public class AdminSupportController {

    private final SupportService supportService;
    private final UserDirectory userDirectory;

    public AdminSupportController(SupportService supportService, UserDirectory userDirectory) {
        this.supportService = supportService;
        this.userDirectory = userDirectory;
    }

    public record PriorityRequest(@NotNull TicketPriority priority) {}

    @GetMapping
    public ResponseEntity<PageResponse<TicketSummary>> queue(
            @RequestParam(required = false) TicketStatus status,
            @RequestParam(required = false) TicketPriority priority,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var tickets = supportService.queue(status, priority, page, PageResponse.clampSize(size));
        Set<UUID> userIds = tickets.getContent().stream().map(SupportTicket::getUserId).collect(Collectors.toSet());
        var labels = userDirectory.labels(userIds);
        return ResponseEntity.ok(PageResponse.of(tickets, t -> {
            var label = labels.get(t.getUserId());
            return TicketSummary.of(t, label != null ? label.fullName() : null, label != null ? label.mobile() : null);
        }));
    }

    @GetMapping("/{ticketId}")
    public ResponseEntity<ThreadView> get(@PathVariable UUID ticketId) {
        return ResponseEntity.ok(view(supportService.get(ticketId)));
    }

    @PostMapping("/{ticketId}/messages")
    public ResponseEntity<ThreadView> reply(@PathVariable UUID ticketId, @Valid @RequestBody ReplyRequest request) {
        return ResponseEntity.ok(view(supportService.replyAsAdmin(SecurityUtils.currentAdminId(), ticketId,
                request.message())));
    }

    @PostMapping("/{ticketId}/resolve")
    public ResponseEntity<TicketSummary> resolve(@PathVariable UUID ticketId) {
        return ResponseEntity.ok(summary(supportService.resolve(SecurityUtils.currentAdminId(), ticketId)));
    }

    @PostMapping("/{ticketId}/reopen")
    public ResponseEntity<TicketSummary> reopen(@PathVariable UUID ticketId) {
        return ResponseEntity.ok(summary(supportService.reopen(SecurityUtils.currentAdminId(), ticketId)));
    }

    @PostMapping("/{ticketId}/priority")
    public ResponseEntity<TicketSummary> priority(@PathVariable UUID ticketId,
                                                  @Valid @RequestBody PriorityRequest request) {
        return ResponseEntity.ok(summary(supportService.setPriority(SecurityUtils.currentAdminId(), ticketId,
                request.priority())));
    }

    private ThreadView view(SupportService.TicketThread thread) {
        var label = userDirectory.labels(Set.of(thread.ticket().getUserId())).get(thread.ticket().getUserId());
        return ThreadView.of(thread, label != null ? label.fullName() : null, label != null ? label.mobile() : null);
    }

    private TicketSummary summary(SupportTicket ticket) {
        var label = userDirectory.labels(Set.of(ticket.getUserId())).get(ticket.getUserId());
        return TicketSummary.of(ticket, label != null ? label.fullName() : null, label != null ? label.mobile() : null);
    }
}
