package in.ruhmans.alliancetrading.withdrawal.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record QuoteResponse(
        UUID quoteId,
        BigDecimal grossAmount,
        BigDecimal feePct,
        BigDecimal feeAmount,
        BigDecimal netAmount,
        String destinationMasked,
        Instant expiresAt) {}
