package in.ruhmans.alliancetrading.payment.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import in.ruhmans.alliancetrading.payment.domain.PaymentAccount;
import in.ruhmans.alliancetrading.payment.domain.UserPaymentAssignment;

public record PaymentAssignmentResponse(
        UUID id,
        UUID userId,
        PaymentAccountResponse paymentAccount,
        BigDecimal amount,
        boolean active,
        UUID assignedBy,
        Instant assignedAt) {

    public static PaymentAssignmentResponse from(UserPaymentAssignment a, PaymentAccount account) {
        return new PaymentAssignmentResponse(a.getId(), a.getUserId(),
                PaymentAccountResponse.from(account), a.getAmount(), a.isActive(),
                a.getAssignedBy(), a.getAssignedAt());
    }
}
