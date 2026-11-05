package in.ruhmans.alliancetrading.bank.api.dto;

import java.time.Instant;
import java.util.UUID;

import in.ruhmans.alliancetrading.bank.domain.BankAccount;
import in.ruhmans.alliancetrading.bank.domain.BankAccountStatus;
import in.ruhmans.alliancetrading.bank.domain.BankAccountType;
import in.ruhmans.alliancetrading.common.pii.PiiMasker;

public record BankAccountResponse(
        UUID id,
        UUID userId,
        String holderName,
        String accountNumberMasked,
        String ifsc,
        BankAccountType accountType,
        String vpa,
        BankAccountStatus status,
        UUID proofDocumentId,
        Instant cooloffUntil,
        String rejectionReason,
        Instant createdAt) {

    public static BankAccountResponse from(BankAccount a) {
        return new BankAccountResponse(a.getId(), a.getUserId(), a.getHolderName(),
                PiiMasker.accountNumber(a.getAccountNumberLast4()), a.getIfsc(), a.getAccountType(),
                a.getVpa(), a.getStatus(), a.getProofDocumentId(),
                a.getCooloffUntil(), a.getRejectionReason(), a.getCreatedAt());
    }
}
