package in.ruhmans.alliancetrading.withdrawal.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import in.ruhmans.alliancetrading.bank.api.dto.BankRejectRequest;
import in.ruhmans.alliancetrading.common.pagination.PageResponse;
import in.ruhmans.alliancetrading.common.security.SecurityUtils;
import in.ruhmans.alliancetrading.settings.service.SettingsService;
import in.ruhmans.alliancetrading.user.service.UserDirectory;
import in.ruhmans.alliancetrading.withdrawal.api.dto.AdminWithdrawalResponse;
import in.ruhmans.alliancetrading.withdrawal.api.dto.MarkFailedRequest;
import in.ruhmans.alliancetrading.withdrawal.api.dto.MarkPaidRequest;
import in.ruhmans.alliancetrading.withdrawal.domain.WithdrawalRequest;
import in.ruhmans.alliancetrading.withdrawal.domain.WithdrawalStatus;
import in.ruhmans.alliancetrading.withdrawal.service.WithdrawalService;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/withdrawals")
public class AdminWithdrawalController {

    private final WithdrawalService withdrawalService;
    private final UserDirectory userDirectory;
    private final SettingsService settingsService;

    public AdminWithdrawalController(WithdrawalService withdrawalService, UserDirectory userDirectory,
                                     SettingsService settingsService) {
        this.withdrawalService = withdrawalService;
        this.userDirectory = userDirectory;
        this.settingsService = settingsService;
    }

    /** Newest first; all statuses when {@code status} is omitted. */
    @GetMapping
    public ResponseEntity<PageResponse<AdminWithdrawalResponse>> list(
            @RequestParam(required = false) WithdrawalStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var withdrawals = withdrawalService.listByStatus(status, PageResponse.newestFirst(page, size));
        return ResponseEntity.ok(PageResponse.of(withdrawals, toResponses(withdrawals.getContent())));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AdminWithdrawalResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(toResponses(List.of(withdrawalService.getById(id))).getFirst());
    }

    private List<AdminWithdrawalResponse> toResponses(List<WithdrawalRequest> withdrawals) {
        var labels = userDirectory.labels(withdrawals.stream().map(WithdrawalRequest::getUserId)
                .collect(Collectors.toSet()));
        BigDecimal threshold = settingsService.secondApprovalThreshold();
        return withdrawals.stream()
                .map(wr -> AdminWithdrawalResponse.from(wr, labels.get(wr.getUserId()), threshold))
                .toList();
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<Void> approve(@PathVariable UUID id) {
        withdrawalService.approve(id, SecurityUtils.currentAdminId());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/second-approve")
    public ResponseEntity<Void> secondApprove(@PathVariable UUID id) {
        withdrawalService.secondApprove(id, SecurityUtils.currentAdminId());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<Void> reject(@PathVariable UUID id,
                                       @Valid @RequestBody BankRejectRequest request) {
        withdrawalService.reject(id, SecurityUtils.currentAdminId(), request.reason());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/mark-paid")
    public ResponseEntity<Void> markPaid(@PathVariable UUID id,
                                         @Valid @RequestBody(required = false) MarkPaidRequest request) {
        withdrawalService.markPaid(id, SecurityUtils.currentAdminId(),
                request != null ? request.payoutUtr() : null,
                request != null ? request.payoutProofDocumentId() : null);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/mark-failed")
    public ResponseEntity<Void> markFailed(@PathVariable UUID id,
                                           @Valid @RequestBody MarkFailedRequest request) {
        withdrawalService.markFailed(id, SecurityUtils.currentAdminId(), request.failureReason());
        return ResponseEntity.ok().build();
    }
}
