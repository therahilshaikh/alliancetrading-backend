package in.ruhmans.alliancetrading.auth.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record OtpSendRequest(
        @NotBlank @Pattern(regexp = "^[6-9]\\d{9}$", message = "Invalid Indian mobile number")
        String mobile) {}
