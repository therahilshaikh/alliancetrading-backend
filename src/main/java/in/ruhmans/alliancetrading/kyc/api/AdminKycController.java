package in.ruhmans.alliancetrading.kyc.api;

import java.util.UUID;

import in.ruhmans.alliancetrading.common.pagination.PageResponse;
import in.ruhmans.alliancetrading.common.security.SecurityUtils;
import in.ruhmans.alliancetrading.kyc.api.dto.KycQueueItem;
import in.ruhmans.alliancetrading.kyc.service.AccountApprovalService;
import in.ruhmans.alliancetrading.kyc.service.KycQueueService;
import in.ruhmans.alliancetrading.kyc.service.KycService;
import in.ruhmans.alliancetrading.payment.api.dto.PaymentAssignmentRequest;
import in.ruhmans.alliancetrading.payment.api.dto.PaymentAssignmentResponse;
import in.ruhmans.alliancetrading.payment.service.PaymentService;
import in.ruhmans.alliancetrading.user.domain.UserStatus;

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

@RestController
@RequestMapping("/api/v1/admin")
public class AdminKycController {

    private final KycService kycService;
    private final KycQueueService kycQueueService;
    private final AccountApprovalService accountApprovalService;
    private final PaymentService paymentService;

    public AdminKycController(KycService kycService, KycQueueService kycQueueService,
                              AccountApprovalService accountApprovalService, PaymentService paymentService) {
        this.kycService = kycService;
        this.kycQueueService = kycQueueService;
        this.accountApprovalService = accountApprovalService;
        this.paymentService = paymentService;
    }

    /** {@code payment} may be omitted if payment details were already assigned. */
    public record ApproveAccountRequest(@Valid PaymentAssignmentRequest payment) {}

    public record ApproveAccountResponse(PaymentAssignmentResponse paymentAssignment, boolean bankVerified) {}

    public record RejectAccountRequest(@NotBlank @Size(max = 500) String reason) {}

    public record IdentityNumberView(String value) {}

    @GetMapping("/kyc")
    public ResponseEntity<PageResponse<KycQueueItem>> queue(
            @RequestParam(defaultValue = "KYC_SUBMITTED") UserStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(kycQueueService.queue(status, page, size));
    }

    /** Full PAN or Aadhaar for manual verification; audited as a PII view. */
    @GetMapping("/kyc/{userId}/identity/{field}")
    public ResponseEntity<IdentityNumberView> revealIdentity(@PathVariable UUID userId,
                                                             @PathVariable KycService.IdentityField field) {
        return ResponseEntity.ok(new IdentityNumberView(
                kycService.revealIdentityNumber(userId, field, SecurityUtils.currentAdminId())));
    }

    @PostMapping("/users/{userId}/kyc/approve")
    public ResponseEntity<ApproveAccountResponse> approve(@PathVariable UUID userId,
                                                          @Valid @RequestBody(required = false) ApproveAccountRequest request) {
        PaymentService.AssignmentCommand payment = request != null && request.payment() != null
                ? request.payment().toCommand() : null;
        var result = accountApprovalService.approve(userId, SecurityUtils.currentAdminId(), payment);
        var account = paymentService.requireAccount(result.assignment().getPaymentAccountId());
        return ResponseEntity.ok(new ApproveAccountResponse(
                PaymentAssignmentResponse.from(result.assignment(), account), result.bankVerified()));
    }

    @PostMapping("/users/{userId}/kyc/reject")
    public ResponseEntity<Void> reject(@PathVariable UUID userId,
                                       @Valid @RequestBody RejectAccountRequest request) {
        kycService.reject(userId, SecurityUtils.currentAdminId(), request.reason());
        return ResponseEntity.ok().build();
    }
}
