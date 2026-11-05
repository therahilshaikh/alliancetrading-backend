package in.ruhmans.alliancetrading.payment.api.dto;

import java.time.Instant;
import java.util.UUID;

import in.ruhmans.alliancetrading.payment.domain.PaymentAccount;
import in.ruhmans.alliancetrading.payment.domain.PaymentAccountStatus;

public record PaymentAccountResponse(
        UUID id,
        String label,
        String vpa,
        String payeeName,
        boolean hasUploadedQr,
        PaymentAccountStatus status,
        UUID createdBy,
        Instant createdAt) {

    public static PaymentAccountResponse from(PaymentAccount a) {
        return new PaymentAccountResponse(a.getId(), a.getLabel(), a.getVpa(), a.getPayeeName(),
                a.getQrDocumentId() != null, a.getStatus(), a.getCreatedBy(), a.getCreatedAt());
    }
}
