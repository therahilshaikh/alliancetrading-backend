package in.ruhmans.alliancetrading.auth.api;

import java.time.Clock;

import in.ruhmans.alliancetrading.auth.api.dto.AuthResponse;
import in.ruhmans.alliancetrading.auth.api.dto.OtpSendRequest;
import in.ruhmans.alliancetrading.auth.api.dto.OtpVerifyRequest;
import in.ruhmans.alliancetrading.auth.api.dto.RefreshRequest;
import in.ruhmans.alliancetrading.auth.service.OtpService;
import in.ruhmans.alliancetrading.auth.service.SessionService;
import in.ruhmans.alliancetrading.common.config.SecurityProperties;
import in.ruhmans.alliancetrading.common.error.ApiException;
import in.ruhmans.alliancetrading.common.error.ErrorCode;
import in.ruhmans.alliancetrading.common.security.SecurityUtils;
import in.ruhmans.alliancetrading.common.web.IpRateLimiter;
import in.ruhmans.alliancetrading.user.domain.User;
import in.ruhmans.alliancetrading.user.repository.UserRepository;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final OtpService otpService;
    private final SessionService sessionService;
    private final UserRepository userRepository;
    private final IpRateLimiter ipRateLimiter;
    private final SecurityProperties.Otp otpConfig;
    private final Clock clock;

    public AuthController(OtpService otpService, SessionService sessionService,
                          UserRepository userRepository, IpRateLimiter ipRateLimiter,
                          SecurityProperties securityProperties, Clock clock) {
        this.otpService = otpService;
        this.sessionService = sessionService;
        this.userRepository = userRepository;
        this.ipRateLimiter = ipRateLimiter;
        this.otpConfig = securityProperties.otp();
        this.clock = clock;
    }

    @PostMapping("/otp/request")
    public ResponseEntity<Void> requestOtp(@Valid @RequestBody OtpSendRequest request,
                                           HttpServletRequest httpRequest) {
        String ip = extractIp(httpRequest);
        if (!ipRateLimiter.tryConsume("otp:" + ip, otpConfig.maxRequestsPerIp(), otpConfig.ipWindow())) {
            throw new ApiException(ErrorCode.RATE_LIMITED, "Too many requests from this IP");
        }

        otpService.requestOtp(request.mobile());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/otp/verify")
    public ResponseEntity<AuthResponse> verifyOtp(@Valid @RequestBody OtpVerifyRequest request) {
        otpService.verifyOtp(request.mobile(), request.otp());

        boolean newUser = false;
        User user = userRepository.findByMobile(request.mobile()).orElse(null);
        if (user == null) {
            user = new User(request.mobile(), clock.instant());
            user = userRepository.save(user);
            newUser = true;
        } else if (user.isSuspended()) {
            throw new ApiException(ErrorCode.FORBIDDEN,
                    "This account has been suspended. Please contact support.");
        }

        SessionService.SessionTokens tokens = sessionService.createUserSession(
                user.getId(), "USER", request.deviceId(), request.deviceInfo());

        return ResponseEntity.ok(new AuthResponse(
                tokens.accessToken(), tokens.refreshToken(), tokens.expiresAt(), newUser, null));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        SessionService.SessionTokens tokens = sessionService.refresh(request.refreshToken());
        return ResponseEntity.ok(new AuthResponse(
                tokens.accessToken(), tokens.refreshToken(), tokens.expiresAt(), false, tokens.admin()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        sessionService.logout(SecurityUtils.currentSessionId());
        return ResponseEntity.noContent().build();
    }

    private String extractIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
