package in.ruhmans.alliancetrading.kyc.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import in.ruhmans.alliancetrading.user.domain.UserStatus;

/** Read model for the admin account-approval queue (KYC profile joined with its user). */
public record KycQueueRow(
        UUID userId,
        String mobile,
        UserStatus status,
        String fullName,
        LocalDate dob,
        String panLast4,
        String aadhaarLast4,
        Instant submittedAt,
        Instant reviewedAt,
        String rejectionReason) {}
