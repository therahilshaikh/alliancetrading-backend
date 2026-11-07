package in.ruhmans.alliancetrading.payment.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import in.ruhmans.alliancetrading.common.security.SecurityUtils;
import in.ruhmans.alliancetrading.payment.api.dto.PaymentAccountRequest;
import in.ruhmans.alliancetrading.payment.api.dto.PaymentAccountResponse;
import in.ruhmans.alliancetrading.payment.api.dto.PaymentAssignmentRequest;
import in.ruhmans.alliancetrading.payment.api.dto.PaymentAssignmentResponse;
import in.ruhmans.alliancetrading.payment.domain.PaymentAccountStatus;
import in.ruhmans.alliancetrading.payment.service.PaymentService;
import in.ruhmans.alliancetrading.settings.service.SettingsService;

import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminPaymentController {

    private final PaymentService paymentService;
    private final SettingsService settingsService;

    public AdminPaymentController(PaymentService paymentService, SettingsService settingsService) {
        this.paymentService = paymentService;
        this.settingsService = settingsService;
    }

    /** Pre-fills the approval dialog: the amount and the saved account to pre-select (null when none is set). */
    public record PaymentDefaults(BigDecimal defaultAmount, UUID defaultPaymentAccountId) {}

    // ---- Saved payment accounts (dropdown) ----

    @GetMapping("/payment-accounts")
    public ResponseEntity<List<PaymentAccountResponse>> listAccounts(
            @RequestParam(defaultValue = "ACTIVE") PaymentAccountStatus status) {
        return ResponseEntity.ok(paymentService.listAccounts(status).stream()
                .map(PaymentAccountResponse::from).toList());
    }

    @GetMapping("/payment-accounts/defaults")
    public ResponseEntity<PaymentDefaults> defaults() {
        return ResponseEntity.ok(new PaymentDefaults(settingsService.defaultInvestmentAmount().orElse(null),
                settingsService.defaultPaymentAccountId().orElse(null)));
    }

    @PostMapping("/payment-accounts")
    public ResponseEntity<PaymentAccountResponse> createAccount(@Valid @RequestBody PaymentAccountRequest request) {
        var account = paymentService.createAccount(SecurityUtils.currentAdminId(), request.toNewAccount());
        return ResponseEntity.ok(PaymentAccountResponse.from(account));
    }

    @PostMapping("/payment-accounts/{id}/archive")
    public ResponseEntity<Void> archiveAccount(@PathVariable UUID id) {
        paymentService.archiveAccount(id, SecurityUtils.currentAdminId());
        return ResponseEntity.ok().build();
    }

    // ---- Per-user assignment ----

    @GetMapping("/users/{userId}/payment-assignment")
    public ResponseEntity<PaymentAssignmentResponse> currentAssignment(@PathVariable UUID userId) {
        var assignment = paymentService.requireActiveAssignment(userId);
        var account = paymentService.requireAccount(assignment.getPaymentAccountId());
        return ResponseEntity.ok(PaymentAssignmentResponse.from(assignment, account));
    }

    @PostMapping("/users/{userId}/payment-assignment")
    public ResponseEntity<PaymentAssignmentResponse> assign(@PathVariable UUID userId,
                                                            @Valid @RequestBody PaymentAssignmentRequest request) {
        var assignment = paymentService.assign(userId, SecurityUtils.currentAdminId(), request.toCommand());
        var account = paymentService.requireAccount(assignment.getPaymentAccountId());
        return ResponseEntity.ok(PaymentAssignmentResponse.from(assignment, account));
    }

    @DeleteMapping("/users/{userId}/payment-assignment")
    public ResponseEntity<Void> cancelAssignment(@PathVariable UUID userId) {
        paymentService.cancelAssignment(userId, SecurityUtils.currentAdminId());
        return ResponseEntity.noContent().build();
    }

    /** Preview exactly the QR the member will see. */
    @GetMapping("/users/{userId}/payment-assignment/qr")
    public ResponseEntity<byte[]> assignmentQr(@PathVariable UUID userId) {
        var qr = paymentService.qrFor(userId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(qr.mime()))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(qr.bytes());
    }
}
