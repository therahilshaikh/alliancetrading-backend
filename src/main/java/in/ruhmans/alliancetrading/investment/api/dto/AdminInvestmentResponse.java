package in.ruhmans.alliancetrading.investment.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import in.ruhmans.alliancetrading.investment.domain.Investment;
import in.ruhmans.alliancetrading.investment.domain.InvestmentStatus;
import in.ruhmans.alliancetrading.payment.service.PaymentService;
import in.ruhmans.alliancetrading.user.service.UserDirectory;

/**
 * Admin view of an investment, with the member's name and mobile and the UPI ID and amount the member
 * was told to pay ({@code expectedVpa}, {@code expectedAmount}), so the review panel needs no extra requests.
 */
public record AdminInvestmentResponse(
        UUID id,
        UUID userId,
        String fullName,
        String mobile,
        BigDecimal amount,
        UUID paymentAssignmentId,
        String expectedVpa,
        BigDecimal expectedAmount,
        UUID proofDocumentId,
        InvestmentStatus status,
        String rejectionReason,
        Instant approvedAt,
        BigDecimal earnedSoFar,
        Instant createdAt) {

    public static AdminInvestmentResponse from(Investment i, UserDirectory.UserLabel label,
                                               PaymentService.ExpectedPayment expected,
                                               BigDecimal earnedSoFar) {
        return new AdminInvestmentResponse(i.getId(), i.getUserId(),
                label != null ? label.fullName() : null, label != null ? label.mobile() : null,
                i.getAmount(), i.getPaymentAssignmentId(),
                expected != null ? expected.vpa() : null, expected != null ? expected.amount() : null,
                i.getProofDocumentId(), i.getStatus(),
                i.getRejectionReason(), i.getApprovedAt(), earnedSoFar, i.getCreatedAt());
    }
}
