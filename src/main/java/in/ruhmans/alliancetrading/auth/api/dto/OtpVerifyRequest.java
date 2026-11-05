package in.ruhmans.alliancetrading.auth.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record OtpVerifyRequest(
        @NotBlank @Pattern(regexp = "^[6-9]\\d{9}$", message = "Invalid Indian mobile number")
        String mobile,

        @NotBlank @Pattern(regexp = "^\\d{6}$", message = "OTP must be 6 digits")
        String otp,

        @NotBlank @Size(max = 200)
        String deviceId,

        @Size(max = 500)
        String deviceInfo) {}
