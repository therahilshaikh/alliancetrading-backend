package in.ruhmans.alliancetrading.settings.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import in.ruhmans.alliancetrading.payment.api.dto.PaymentAccountRequest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

/**
 * Default payment: the amount plus either a saved account ({@code paymentAccountId}) or a new one created
 * inline ({@code newAccount}, always saved to the list). Exactly one of the two is required. The UPI ID and
 * QR come from the account.
 */
public record PaymentSettingRequest(
        @NotNull @DecimalMin(value = "1.00") @Digits(integer = 13, fraction = 2) BigDecimal fixedAmount,
        @NotNull Instant effectiveFrom,
        UUID paymentAccountId,
        @Valid PaymentAccountRequest newAccount) {}
