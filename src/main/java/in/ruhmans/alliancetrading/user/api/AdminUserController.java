package in.ruhmans.alliancetrading.user.api;

import java.util.UUID;

import in.ruhmans.alliancetrading.common.pagination.CursorPage;
import in.ruhmans.alliancetrading.common.pagination.PageResponse;
import in.ruhmans.alliancetrading.common.security.SecurityUtils;
import in.ruhmans.alliancetrading.ledger.service.LedgerQueryService;
import in.ruhmans.alliancetrading.user.domain.UserStatus;
import in.ruhmans.alliancetrading.user.repository.AdminUserRow;
import in.ruhmans.alliancetrading.user.service.AdminUserService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Member management for admins (distinct from admin-account management under /admin/admin-users). */
@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {

    private final AdminUserService adminUserService;
    private final LedgerQueryService ledgerQueryService;

    public AdminUserController(AdminUserService adminUserService, LedgerQueryService ledgerQueryService) {
        this.adminUserService = adminUserService;
        this.ledgerQueryService = ledgerQueryService;
    }

    public record SuspendRequest(@NotBlank @Size(max = 500) String reason) {}

    @GetMapping
    public ResponseEntity<PageResponse<AdminUserRow>> search(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(adminUserService.search(search, status, page, size));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<AdminUserService.UserDetail> detail(@PathVariable UUID userId) {
        return ResponseEntity.ok(adminUserService.detail(userId));
    }

    @GetMapping("/{userId}/ledger")
    public ResponseEntity<CursorPage<LedgerQueryService.LedgerEntryView>> ledger(
            @PathVariable UUID userId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(ledgerQueryService.page(userId, cursor, limit));
    }

    @PostMapping("/{userId}/suspend")
    public ResponseEntity<Void> suspend(@PathVariable UUID userId, @Valid @RequestBody SuspendRequest request) {
        adminUserService.suspend(userId, SecurityUtils.currentAdminId(), request.reason());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{userId}/reactivate")
    public ResponseEntity<Void> reactivate(@PathVariable UUID userId) {
        adminUserService.reactivate(userId, SecurityUtils.currentAdminId());
        return ResponseEntity.ok().build();
    }
}
