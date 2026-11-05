package in.ruhmans.alliancetrading.withdrawal.api.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record CreateWithdrawalRequest(@NotNull UUID quoteId) {}
