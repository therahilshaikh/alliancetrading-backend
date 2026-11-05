package in.ruhmans.alliancetrading.bank.api.dto;

import java.util.UUID;

import in.ruhmans.alliancetrading.bank.domain.BankAccountType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * {@code proofDocumentId} is optional while document uploads are disabled.
 */
public record BankAccountRequest(
        @NotBlank @Size(max = 200) String holderName,
        @NotBlank @Pattern(regexp = "^\\d{9,18}$", message = "Account number must be 9-18 digits")
        String accountNumber,
        @NotBlank @Pattern(regexp = "^[A-Z]{4}0[A-Z0-9]{6}$", message = "Invalid IFSC format")
        String ifsc,
        @NotNull BankAccountType accountType,
        @NotBlank @Size(max = 100)
        @Pattern(regexp = "^[a-zA-Z0-9._-]{2,256}@[a-zA-Z]{2,64}$", message = "Invalid UPI ID")
        String vpa,
        UUID proofDocumentId) {}
