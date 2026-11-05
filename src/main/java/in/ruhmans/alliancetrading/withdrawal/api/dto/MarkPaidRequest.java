package in.ruhmans.alliancetrading.withdrawal.api.dto;

import java.util.UUID;

import jakarta.validation.constraints.Pattern;

/**
 * Marks an approved withdrawal as paid after the offline bank transfer.
 * Both fields are optional; a payout UTR, if entered, is format-checked.
 */
public record MarkPaidRequest(
        @Pattern(regexp = "^[A-Za-z0-9]{12,22}$", message = "Invalid UTR format")
        String payoutUtr,
        UUID payoutProofDocumentId) {}
