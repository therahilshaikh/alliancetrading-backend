package in.ruhmans.alliancetrading.bank.api.dto;

import java.time.Instant;
import java.util.UUID;

import in.ruhmans.alliancetrading.bank.domain.BankAccount;
import in.ruhmans.alliancetrading.bank.domain.BankAccountStatus;
import in.ruhmans.alliancetrading.bank.domain.BankAccountType;
import in.ruhmans.alliancetrading.common.pii.PiiMasker;
import in.ruhmans.alliancetrading.user.service.UserDirectory;

/** Admin view of a bank account, with the member's name and mobile so lists render in one request. */
public record AdminBankAccountResponse(
        UUID id,
        UUID userId,
        String fullName,
        String mobile,
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

    public static AdminBankAccountResponse from(BankAccount a, UserDirectory.UserLabel label) {
        return new AdminBankAccountResponse(a.getId(), a.getUserId(),
                label != null ? label.fullName() : null, label != null ? label.mobile() : null,
                a.getHolderName(), PiiMasker.accountNumber(a.getAccountNumberLast4()), a.getIfsc(),
                a.getAccountType(), a.getVpa(), a.getStatus(), a.getProofDocumentId(),
                a.getCooloffUntil(), a.getRejectionReason(), a.getCreatedAt());
    }
}
