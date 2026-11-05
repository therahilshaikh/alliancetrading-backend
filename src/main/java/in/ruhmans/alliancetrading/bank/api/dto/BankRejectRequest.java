package in.ruhmans.alliancetrading.bank.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BankRejectRequest(
        @NotBlank @Size(max = 500) String reason) {}
