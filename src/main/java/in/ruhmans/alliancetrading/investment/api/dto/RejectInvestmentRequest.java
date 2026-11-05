package in.ruhmans.alliancetrading.investment.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectInvestmentRequest(
        @NotBlank @Size(max = 500) String reason) {}
