package in.ruhmans.alliancetrading.settings.api.dto;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AppSettingRequest(
        @NotBlank @Size(max = 100) String key,
        @NotBlank @Size(max = 500) String value,
        @NotNull Instant effectiveFrom) {}
