package in.ruhmans.alliancetrading.kyc.api.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import in.ruhmans.alliancetrading.bank.api.dto.BankAccountResponse;
import in.ruhmans.alliancetrading.payment.api.dto.PaymentAssignmentResponse;
import in.ruhmans.alliancetrading.user.domain.UserStatus;

/** One row of the admin account-approval queue: everything submitted during onboarding. */
public record KycQueueItem(
        UUID userId,
        String mobile,
        UserStatus status,
        String fullName,
        LocalDate dob,
        String panMasked,
        String aadhaarMasked,
        Instant submittedAt,
        Instant reviewedAt,
        String rejectionReason,
        BankAccountResponse bankAccount,
        PaymentAssignmentResponse paymentAssignment) {}
