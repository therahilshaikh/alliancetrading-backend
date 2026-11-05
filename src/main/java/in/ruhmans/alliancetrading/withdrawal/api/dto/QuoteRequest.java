package in.ruhmans.alliancetrading.withdrawal.api.dto;

import java.math.BigDecimal;

import in.ruhmans.alliancetrading.settings.domain.WithdrawalType;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record QuoteRequest(
        @NotNull @DecimalMin("0.01") BigDecimal amount,
        @NotNull WithdrawalType withdrawalType) {}
