package in.ruhmans.alliancetrading.kyc.api.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record KycResponse(
        UUID id,
        String fullName,
        LocalDate dob,
        String panMasked,
        String aadhaarMasked,
        Instant submittedAt,
        Instant reviewedAt,
        String rejectionReason) {}
