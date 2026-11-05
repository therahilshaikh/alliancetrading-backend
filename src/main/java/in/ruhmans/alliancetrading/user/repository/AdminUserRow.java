package in.ruhmans.alliancetrading.user.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import in.ruhmans.alliancetrading.investment.domain.InvestmentStatus;
import in.ruhmans.alliancetrading.user.domain.UserStatus;

/**
 * Read model for the admin member list. Balances are null until the user's first ledger
 * posting; investment fields are null when the user has no live investment.
 */
public record AdminUserRow(
        UUID userId,
        String fullName,
        String mobile,
        UserStatus status,
        Instant joinedAt,
        BigDecimal principal,
        BigDecimal profit,
        BigDecimal held,
        UUID liveInvestmentId,
        InvestmentStatus liveInvestmentStatus) {}
