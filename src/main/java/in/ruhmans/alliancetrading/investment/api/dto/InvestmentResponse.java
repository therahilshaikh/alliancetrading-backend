package in.ruhmans.alliancetrading.investment.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import in.ruhmans.alliancetrading.investment.domain.Investment;
import in.ruhmans.alliancetrading.investment.domain.InvestmentStatus;

public record InvestmentResponse(
        UUID id,
        UUID userId,
        BigDecimal amount,
        UUID paymentAssignmentId,
        UUID proofDocumentId,
        InvestmentStatus status,
        String rejectionReason,
        Instant approvedAt,
        BigDecimal earnedSoFar,
        Instant createdAt) {

    public static InvestmentResponse from(Investment i, BigDecimal earnedSoFar) {
        return new InvestmentResponse(i.getId(), i.getUserId(), i.getAmount(), i.getPaymentAssignmentId(),
                i.getProofDocumentId(), i.getStatus(), i.getRejectionReason(),
                i.getApprovedAt(), earnedSoFar, i.getCreatedAt());
    }
}
