package in.ruhmans.alliancetrading.withdrawal.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import in.ruhmans.alliancetrading.common.pii.PiiMasker;
import in.ruhmans.alliancetrading.settings.domain.WithdrawalType;
import in.ruhmans.alliancetrading.user.service.UserDirectory;
import in.ruhmans.alliancetrading.withdrawal.domain.WithdrawalRequest;
import in.ruhmans.alliancetrading.withdrawal.domain.WithdrawalStatus;

/**
 * Admin view of a withdrawal: who is being paid, the bank snapshot taken at request time,
 * who approved it and whether a second approval is still needed before payout.
 * The full account number is available via {@code GET /admin/bank-accounts/{bankAccountId}/account-number}.
 */
public record AdminWithdrawalResponse(
        UUID id,
        UUID userId,
        String fullName,
        String mobile,
        UUID investmentId,
        WithdrawalType withdrawalType,
        BigDecimal grossAmount,
        BigDecimal feePct,
        BigDecimal feeAmount,
        BigDecimal netAmount,
        UUID bankAccountId,
        String bankHolderName,
        String bankAccountMasked,
        String bankIfsc,
        String bankVpa,
        WithdrawalStatus status,
        UUID approvedBy,
        UUID secondApprovedBy,
        boolean secondApprovalRequired,
        boolean awaitingSecondApproval,
        UUID paidBy,
        String payoutUtr,
        UUID payoutProofDocumentId,
        Instant paidAt,
        String rejectionReason,
        String failureReason,
        Instant createdAt,
        Instant updatedAt) {

    public static AdminWithdrawalResponse from(WithdrawalRequest wr, UserDirectory.UserLabel label,
                                               BigDecimal secondApprovalThreshold) {
        boolean secondRequired = wr.getGrossAmount().compareTo(secondApprovalThreshold) >= 0;
        return new AdminWithdrawalResponse(wr.getId(), wr.getUserId(),
                label != null ? label.fullName() : null, label != null ? label.mobile() : null,
                wr.getInvestmentId(), wr.getWithdrawalType(), wr.getGrossAmount(), wr.getFeePct(),
                wr.getFeeAmount(), wr.getNetAmount(), wr.getBankAccountId(), wr.getBankHolderName(),
                PiiMasker.accountNumber(wr.getBankAccountLast4()), wr.getBankIfsc(), wr.getBankVpa(),
                wr.getStatus(), wr.getApprovedBy(), wr.getSecondApprovedBy(), secondRequired,
                secondRequired && wr.getStatus() == WithdrawalStatus.APPROVED && wr.getSecondApprovedBy() == null,
                wr.getPaidBy(), wr.getPayoutUtr(), wr.getPayoutProofDocumentId(), wr.getPaidAt(),
                wr.getRejectionReason(), wr.getFailureReason(), wr.getCreatedAt(), wr.getUpdatedAt());
    }
}
