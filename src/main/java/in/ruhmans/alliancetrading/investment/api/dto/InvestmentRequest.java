package in.ruhmans.alliancetrading.investment.api.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/** The payment screenshot uploaded via {@code POST /files} with docType PAYMENT_PROOF. */
public record InvestmentRequest(@NotNull UUID proofDocumentId) {}
