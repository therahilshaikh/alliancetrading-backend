package in.ruhmans.alliancetrading.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import in.ruhmans.alliancetrading.auth.domain.OtpRequest;
import in.ruhmans.alliancetrading.auth.repository.OtpRequestRepository;
import in.ruhmans.alliancetrading.common.config.SecurityProperties;
import in.ruhmans.alliancetrading.common.error.ApiException;
import in.ruhmans.alliancetrading.common.error.ErrorCode;
import in.ruhmans.alliancetrading.notification.sms.SmsDeliveryException;
import in.ruhmans.alliancetrading.notification.sms.SmsMessage;
import in.ruhmans.alliancetrading.notification.sms.SmsSender;
import in.ruhmans.alliancetrading.notification.sms.SmsTemplate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OtpService {

    private static final Logger log = LoggerFactory.getLogger(OtpService.class);
    private static final int OTP_LENGTH = 6;

    private final OtpRequestRepository otpRequestRepository;
    private final SmsSender smsSender;
    private final Clock clock;
    private final SecurityProperties.Otp config;
    private final byte[] pepper;
    private final String fixedCode;
    private final SecureRandom random = new SecureRandom();

    public OtpService(OtpRequestRepository otpRequestRepository, SmsSender smsSender,
                      Clock clock, SecurityProperties securityProperties,
                      @Value("${app.sms.provider:logging}") String smsProvider) {
        this.otpRequestRepository = otpRequestRepository;
        this.smsSender = smsSender;
        this.clock = clock;
        this.config = securityProperties.otp();
        this.pepper = Base64.getDecoder().decode(config.pepper());
        this.fixedCode = config.fixedCode() == null || config.fixedCode().isBlank() ? null : config.fixedCode().trim();
        if (fixedCode != null) {
            // A fixed OTP would let anyone sign in as any member, so it is refused outside local testing
            if (!"logging".equals(smsProvider)) {
                throw new IllegalStateException("app.security.otp.fixed-code is only allowed with app.sms.provider=logging");
            }
            if (!fixedCode.matches("\\d{" + OTP_LENGTH + "}")) {
                throw new IllegalStateException("app.security.otp.fixed-code must be " + OTP_LENGTH + " digits");
            }
            log.warn("Fixed test OTP is enabled; every OTP is the configured code. Never use this outside local/dev.");
        }
    }

    @Transactional
    public void requestOtp(String mobile) {
        Instant now = clock.instant();
        Instant since = now.minus(config.mobileWindow());
        long recentCount = otpRequestRepository.countRecentByMobile(mobile, since);
        if (recentCount >= config.maxRequestsPerMobile()) {
            throw new ApiException(ErrorCode.RATE_LIMITED,
                    "Too many OTP requests. Try again later.");
        }

        String code = generateCode();
        String hash = hmacHash(code);
        Instant expiresAt = now.plus(config.ttl());

        OtpRequest request = new OtpRequest(mobile, hash, expiresAt, now);
        otpRequestRepository.save(request);

        String validMinutes = String.valueOf(config.ttl().toMinutes());
        try {
            smsSender.send(mobile, SmsMessage.of(SmsTemplate.OTP, code, validMinutes));
        } catch (SmsDeliveryException e) {
            // Roll back the OTP row so a failed send does not count against the rate limit
            log.error("OTP SMS delivery failed: {}", e.getMessage());
            throw new ApiException(ErrorCode.SMS_SEND_FAILED,
                    "Could not send the OTP right now. Please try again.");
        }
    }

    /**
     * Each check counts as an attempt and a correct code is consumed so it cannot be replayed.
     * {@code noRollbackFor} keeps the attempt count committed when the code is wrong.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public void verifyOtp(String mobile, String code) {
        Instant now = clock.instant();
        List<OtpRequest> active = otpRequestRepository.findActiveByMobile(mobile, now);

        if (active.isEmpty()) {
            throw new ApiException(ErrorCode.OTP_EXPIRED, "No active OTP found. Please request a new one.");
        }

        OtpRequest latest = active.getFirst();
        if (latest.getAttempts() >= config.maxVerifyAttempts()) {
            throw new ApiException(ErrorCode.OTP_ATTEMPTS_EXCEEDED,
                    "Maximum verification attempts exceeded. Please request a new OTP.");
        }

        latest.incrementAttempts();

        String hash = hmacHash(code);
        if (!hash.equals(latest.getOtpHash())) {
            otpRequestRepository.save(latest);
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Invalid OTP");
        }

        latest.consume(now);
        otpRequestRepository.save(latest);
    }

    private String generateCode() {
        if (fixedCode != null) {
            return fixedCode;
        }
        int code = random.nextInt((int) Math.pow(10, OTP_LENGTH));
        return String.format("%0" + OTP_LENGTH + "d", code);
    }

    private String hmacHash(String code) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(pepper, "HmacSHA256"));
            byte[] out = mac.doFinal(code.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("OTP hashing failed", e);
        }
    }
}
