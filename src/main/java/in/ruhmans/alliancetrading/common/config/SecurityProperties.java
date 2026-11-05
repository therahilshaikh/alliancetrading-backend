package in.ruhmans.alliancetrading.common.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(Jwt jwt, Otp otp, Admin admin) {

    public record Jwt(
            String secret,
            Duration accessTokenTtl,
            Duration adminAccessTokenTtl,
            Duration refreshTokenTtl) {}

    public record Otp(
            String pepper,
            Duration ttl,
            int maxVerifyAttempts,
            int maxRequestsPerMobile,
            Duration mobileWindow,
            int maxRequestsPerIp,
            Duration ipWindow,
            /** Local/dev only: every OTP is this code (requires the logging SMS provider). */
            String fixedCode) {}

    public record Admin(
            int maxFailedLogins,
            Duration lockDuration,
            Duration idleTimeout) {}
}
