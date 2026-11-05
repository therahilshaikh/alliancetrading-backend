package in.ruhmans.alliancetrading.support.api;

import java.util.List;
import java.util.UUID;

import in.ruhmans.alliancetrading.common.security.SecurityUtils;
import in.ruhmans.alliancetrading.support.api.SupportDtos.OpenTicketRequest;
import in.ruhmans.alliancetrading.support.api.SupportDtos.ReplyRequest;
import in.ruhmans.alliancetrading.support.api.SupportDtos.ThreadView;
import in.ruhmans.alliancetrading.support.api.SupportDtos.TicketSummary;
import in.ruhmans.alliancetrading.support.service.SupportService;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in member's own support tickets. */
@RestController
@RequestMapping("/api/v1/support/tickets")
public class SupportController {

    private final SupportService supportService;

    public SupportController(SupportService supportService) {
        this.supportService = supportService;
    }

    @PostMapping
    public ResponseEntity<ThreadView> open(@Valid @RequestBody OpenTicketRequest request) {
        var thread = supportService.open(SecurityUtils.currentUserId(), request.subject(), request.message());
        return ResponseEntity.ok(ThreadView.of(thread, null, null));
    }

    @GetMapping
    public ResponseEntity<List<TicketSummary>> list() {
        return ResponseEntity.ok(supportService.listOwn(SecurityUtils.currentUserId()).stream()
                .map(t -> TicketSummary.of(t, null, null)).toList());
    }

    @GetMapping("/{ticketId}")
    public ResponseEntity<ThreadView> get(@PathVariable UUID ticketId) {
        return ResponseEntity.ok(ThreadView.of(
                supportService.getOwn(SecurityUtils.currentUserId(), ticketId), null, null));
    }

    @PostMapping("/{ticketId}/messages")
    public ResponseEntity<ThreadView> reply(@PathVariable UUID ticketId, @Valid @RequestBody ReplyRequest request) {
        return ResponseEntity.ok(ThreadView.of(
                supportService.replyAsUser(SecurityUtils.currentUserId(), ticketId, request.message()), null, null));
    }
}
