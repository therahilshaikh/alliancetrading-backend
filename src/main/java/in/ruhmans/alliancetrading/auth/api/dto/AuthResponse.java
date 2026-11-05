package in.ruhmans.alliancetrading.auth.api.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

import in.ruhmans.alliancetrading.auth.service.SessionService;

/** {@code admin} is present only for admin login and admin refresh. */
public record AuthResponse(
        String accessToken,
        String refreshToken,
        Instant expiresAt,
        boolean newUser,
        @JsonInclude(JsonInclude.Include.NON_NULL) SessionService.AdminProfile admin) {}
