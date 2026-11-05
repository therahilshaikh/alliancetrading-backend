package in.ruhmans.alliancetrading.kyc.api.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record KycSubmitRequest(
        @NotBlank @Size(max = 200) String fullName,
        @NotNull @Past LocalDate dob,
        @NotBlank @Pattern(regexp = "^[A-Z]{5}[0-9]{4}[A-Z]$", message = "Invalid PAN format")
        String pan,
        @NotBlank @Pattern(regexp = "^\\d{12}$", message = "Aadhaar must be 12 digits")
        String aadhaar) {}
