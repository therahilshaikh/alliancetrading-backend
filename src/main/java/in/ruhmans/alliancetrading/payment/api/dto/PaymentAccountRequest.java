package in.ruhmans.alliancetrading.payment.api.dto;

import java.util.UUID;

import in.ruhmans.alliancetrading.payment.service.PaymentService;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** {@code label} defaults to the UPI ID; {@code qrDocumentId} is an optional uploaded PAYMENT_QR. */
public record PaymentAccountRequest(
        @Size(max = 100) String label,
        @NotBlank @Size(max = 100)
        @Pattern(regexp = "^[a-zA-Z0-9._-]{2,256}@[a-zA-Z]{2,64}$", message = "Invalid UPI ID")
        String vpa,
        @NotBlank @Size(max = 100) String payeeName,
        UUID qrDocumentId) {

    public PaymentService.NewAccount toNewAccount() {
        return new PaymentService.NewAccount(label, vpa, payeeName, qrDocumentId);
    }
}
