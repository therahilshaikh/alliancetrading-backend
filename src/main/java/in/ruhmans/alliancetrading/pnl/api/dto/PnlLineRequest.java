package in.ruhmans.alliancetrading.pnl.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/**
 * {@code targetType}: ALL (every member with an ACTIVE investment) or MEMBER (needs userId; send one line per
 * selected member). Send {@code amount} (rupees each target member receives, however many investments they
 * hold) for FIXED batches, or {@code percentage} (percent of each member's total remaining principal,
 * e.g. 1.25 = 1.25%) for PERCENTAGE batches. Negative values post a loss. A member may appear in only one line.
 */
public record PnlLineRequest(
        @NotNull String targetType,
        UUID userId,
        BigDecimal amount,
        BigDecimal percentage) {}
