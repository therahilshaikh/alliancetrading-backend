package in.ruhmans.alliancetrading.withdrawal.api;

import java.util.List;
import java.util.UUID;

import in.ruhmans.alliancetrading.common.idempotency.IdempotencyService;
import in.ruhmans.alliancetrading.common.security.SecurityUtils;
import in.ruhmans.alliancetrading.withdrawal.api.dto.CreateWithdrawalRequest;
import in.ruhmans.alliancetrading.withdrawal.api.dto.QuoteRequest;
import in.ruhmans.alliancetrading.withdrawal.api.dto.QuoteResponse;
import in.ruhmans.alliancetrading.withdrawal.api.dto.WithdrawalResponse;
import in.ruhmans.alliancetrading.withdrawal.domain.WithdrawalRequest;
import in.ruhmans.alliancetrading.withdrawal.service.WithdrawalService;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/withdrawals")
public class WithdrawalController {

    private final WithdrawalService withdrawalService;

    public WithdrawalController(WithdrawalService withdrawalService) {
        this.withdrawalService = withdrawalService;
    }

    @PostMapping("/quote")
    public ResponseEntity<QuoteResponse> quote(@Valid @RequestBody QuoteRequest request) {
        var result = withdrawalService.quote(SecurityUtils.currentUserId(),
                request.amount(), request.withdrawalType());
        var q = result.quote();
        String masked = "****" + result.bank().getAccountNumberLast4()
                + " (" + result.bank().getIfsc() + ")";
        return ResponseEntity.ok(new QuoteResponse(q.getId(), q.getGrossAmount(), q.getFeePct(),
                q.getFeeAmount(), q.getNetAmount(), masked, q.getExpiresAt()));
    }

    @PostMapping
    public ResponseEntity<WithdrawalResponse> create(
            @Valid @RequestBody CreateWithdrawalRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        String key = IdempotencyService.requireKey(idempotencyKey);
        var wr = withdrawalService.request(SecurityUtils.currentUserId(), request.quoteId(), key);
        return ResponseEntity.ok(toResponse(wr));
    }

    @GetMapping
    public ResponseEntity<List<WithdrawalResponse>> listOwn() {
        return ResponseEntity.ok(withdrawalService.listOwn(SecurityUtils.currentUserId()).stream()
                .map(WithdrawalController::toResponse).toList());
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<Void> cancel(@PathVariable UUID id) {
        withdrawalService.cancel(SecurityUtils.currentUserId(), id);
        return ResponseEntity.ok().build();
    }

    static WithdrawalResponse toResponse(WithdrawalRequest wr) {
        return new WithdrawalResponse(wr.getId(), wr.getWithdrawalType(), wr.getGrossAmount(),
                wr.getFeePct(), wr.getFeeAmount(), wr.getNetAmount(),
                "****" + wr.getBankAccountLast4() + " (" + wr.getBankIfsc() + ")",
                wr.getStatus(), wr.getRejectionReason(), wr.getFailureReason(),
                wr.getPaidAt(), wr.getCreatedAt());
    }
}
