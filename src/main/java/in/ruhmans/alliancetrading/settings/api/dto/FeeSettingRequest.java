package in.ruhmans.alliancetrading.settings.api.dto;

import java.math.BigDecimal;
import java.time.Instant;

import in.ruhmans.alliancetrading.settings.domain.WithdrawalType;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record FeeSettingRequest(
        @NotNull WithdrawalType withdrawalType,
        @NotNull @DecimalMin("0.0000") @DecimalMax("0.9999") BigDecimal feePct,
        @NotNull Instant effectiveFrom) {}
