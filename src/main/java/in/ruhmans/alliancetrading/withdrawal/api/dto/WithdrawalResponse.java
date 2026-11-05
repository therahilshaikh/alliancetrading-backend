package in.ruhmans.alliancetrading.withdrawal.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import in.ruhmans.alliancetrading.settings.domain.WithdrawalType;
import in.ruhmans.alliancetrading.withdrawal.domain.WithdrawalStatus;

public record WithdrawalResponse(
        UUID id,
        WithdrawalType withdrawalType,
        BigDecimal grossAmount,
        BigDecimal feePct,
        BigDecimal feeAmount,
        BigDecimal netAmount,
        String bankMasked,
        WithdrawalStatus status,
        String rejectionReason,
        String failureReason,
        Instant paidAt,
        Instant createdAt) {}
