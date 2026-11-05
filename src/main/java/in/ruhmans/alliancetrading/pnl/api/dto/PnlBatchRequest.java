package in.ruhmans.alliancetrading.pnl.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import in.ruhmans.alliancetrading.pnl.domain.PnlValueType;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Create (or dry-run preview) a P&L batch. Every line applies to each of its target investments:
 * FIXED gives each one {@code amount} rupees, PERCENTAGE gives each one {@code percentage}% of its
 * own principal. {@code controlTotal} is optional: the server computes it and, if sent, it must match.
 * {@code approverAdminId} (from {@code GET /admin/pnl-batches/approvers}) is required to create and
 * optional for preview. {@code evidenceDocumentId} is optional.
 */
public record PnlBatchRequest(
        @NotNull LocalDate sessionDate,
        UUID originalBatchId,
        @NotNull PnlValueType valueType,
        BigDecimal controlTotal,
        UUID evidenceDocumentId,
        @Size(max = 1000) String notes,
        UUID approverAdminId,
        @NotEmpty @Valid List<PnlLineRequest> lines) {}