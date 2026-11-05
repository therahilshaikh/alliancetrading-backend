package in.ruhmans.alliancetrading.pnl.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/**
 * {@code targetType}: ALL (every active investment) or INVESTMENT (needs investmentId; send one line per
 * selected member). Send {@code amount} (rupees each target receives) for FIXED batches, or
 * {@code percentage} (percent of each target's principal, e.g. 1.25 = 1.25%) for PERCENTAGE batches.
 * Negative values post a loss.
 */
public record PnlLineRequest(
        @NotNull String targetType,
        UUID investmentId,
        BigDecimal amount,
        BigDecimal percentage) {}
