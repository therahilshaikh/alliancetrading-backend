package in.ruhmans.alliancetrading.payment.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

import in.ruhmans.alliancetrading.payment.service.PaymentService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;

/**
 * Pick a saved account ({@code paymentAccountId}) or enter a new one ({@code newAccount}).
 * {@code saveToList} (default true) adds a new account to the dropdown for future use.
 * {@code amount} defaults to the configured investment amount.
 */
public record PaymentAssignmentRequest(
        UUID paymentAccountId,
        @Valid PaymentAccountRequest newAccount,
        Boolean saveToList,
        @DecimalMin(value = "1.00") @Digits(integer = 13, fraction = 2) BigDecimal amount) {

    public PaymentService.AssignmentCommand toCommand() {
        return new PaymentService.AssignmentCommand(paymentAccountId,
                newAccount != null ? newAccount.toNewAccount() : null,
                saveToList == null || saveToList,
                amount);
    }
}
