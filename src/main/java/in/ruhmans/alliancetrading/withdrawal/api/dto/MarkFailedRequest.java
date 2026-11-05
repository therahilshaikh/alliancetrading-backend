package in.ruhmans.alliancetrading.withdrawal.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MarkFailedRequest(
        @NotBlank @Size(max = 500) String failureReason) {}
