package in.ruhmans.alliancetrading.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import in.ruhmans.alliancetrading.auth.domain.OtpRequest;
import in.ruhmans.alliancetrading.auth.repository.OtpRequestRepository;
import in.ruhmans.alliancetrading.auth.service.AdminAuthService;
import in.ruhmans.alliancetrading.auth.service.OtpService;
import in.ruhmans.alliancetrading.auth.service.SessionService;
import in.ruhmans.alliancetrading.common.config.SecurityProperties;
import in.ruhmans.alliancetrading.common.error.ApiException;
import in.ruhmans.alliancetrading.common.error.ErrorCode;
import in.ruhmans.alliancetrading.notification.sms.SmsMessage;
import in.ruhmans.alliancetrading.notification.sms.SmsSender;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.annotation.Transactional;

class OtpServiceTest {

    private static final String MOBILE = "9876543210";

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-22T06:00:00Z"), ZoneId.of("UTC"));
    private OtpRequestRepository repository;
    private SmsSender smsSender;
    private OtpService otpService;
    private final List<OtpRequest> stored = new ArrayList<>();

    @BeforeEach
    void setUp() {
        repository = mock(OtpRequestRepository.class);
        smsSender = mock(SmsSender.class);
        var otp = new SecurityProperties.Otp(Base64.getEncoder().encodeToString("test-pepper-test-pepper".getBytes()),
                Duration.ofMinutes(5), 5, 3, Duration.ofMinutes(15), 20, Duration.ofHours(1), null);
        otpService = new OtpService(repository, smsSender, clock, new SecurityProperties(null, otp, null), "logging");

        when(repository.save(any(OtpRequest.class))).thenAnswer(i -> {
            OtpRequest r = i.getArgument(0);
            if (!stored.contains(r)) stored.add(r);
            return r;
        });
        when(repository.findActiveByMobile(eq(MOBILE), any())).thenAnswer(i -> {
            Instant now = i.getArgument(1);
            return stored.stream().filter(r -> r.getExpiresAt().isAfter(now)).toList();
        });
    }

    private String requestAndCaptureCode() {
        otpService.requestOtp(MOBILE);
        ArgumentCaptor<SmsMessage> sms = ArgumentCaptor.forClass(SmsMessage.class);
        verify(smsSender).send(anyString(), sms.capture());
        return sms.getValue().variables().getFirst();
    }

    @Test
    void wrongCodeCountsAnAttemptAndPersistsIt() {
        requestAndCaptureCode();

        assertThatThrownBy(() -> otpService.verifyOtp(MOBILE, "000000"))
                .isInstanceOf(ApiException.class);

        assertThat(stored.getFirst().getAttempts()).isEqualTo(1);
    }

    @Test
    void attemptsAreCappedAfterFiveWrongCodes() {
        String code = requestAndCaptureCode();
        String wrong = code.equals("000000") ? "111111" : "000000";
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> otpService.verifyOtp(MOBILE, wrong)).isInstanceOf(ApiException.class);
        }

        // Even the correct code is refused once the limit is reached
        assertThatThrownBy(() -> otpService.verifyOtp(MOBILE, code))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).code()).isEqualTo(ErrorCode.OTP_ATTEMPTS_EXCEEDED));
    }

    @Test
    void correctCodeIsConsumedAndCannotBeReplayed() {
        String code = requestAndCaptureCode();

        otpService.verifyOtp(MOBILE, code);

        assertThatThrownBy(() -> otpService.verifyOtp(MOBILE, code))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).code()).isEqualTo(ErrorCode.OTP_EXPIRED));
    }

    @Test
    void fixedTestCodeIsUsedWithTheLoggingProvider() {
        var otp = new SecurityProperties.Otp(Base64.getEncoder().encodeToString("test-pepper-test-pepper".getBytes()),
                Duration.ofMinutes(5), 5, 3, Duration.ofMinutes(15), 20, Duration.ofHours(1), "123456");
        otpService = new OtpService(repository, smsSender, clock, new SecurityProperties(null, otp, null), "logging");

        assertThat(requestAndCaptureCode()).isEqualTo("123456");
        otpService.verifyOtp(MOBILE, "123456");
    }

    @Test
    void fixedTestCodeIsRefusedWithARealSmsProvider() {
        var otp = new SecurityProperties.Otp(Base64.getEncoder().encodeToString("test-pepper-test-pepper".getBytes()),
                Duration.ofMinutes(5), 5, 3, Duration.ofMinutes(15), 20, Duration.ofHours(1), "123456");

        assertThatThrownBy(() -> new OtpService(repository, smsSender, clock,
                new SecurityProperties(null, otp, null), "fast2sms"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("only allowed with app.sms.provider=logging");
    }

    /**
     * These methods write security state (attempt counts, revocations, lockouts) and then throw.
     * Without noRollbackFor the write is rolled back and the protection silently does nothing.
     */
    @Test
    void securityWritesSurviveTheirOwnFailure() throws Exception {
        assertKeepsWritesOnApiException(OtpService.class.getMethod("verifyOtp", String.class, String.class));
        assertKeepsWritesOnApiException(SessionService.class.getMethod("refresh", String.class));
        assertKeepsWritesOnApiException(AdminAuthService.class.getMethod("login", String.class, String.class));
    }

    private static void assertKeepsWritesOnApiException(Method method) {
        Transactional tx = method.getAnnotation(Transactional.class);
        assertThat(tx).as(method.getName() + " is transactional").isNotNull();
        assertThat(tx.noRollbackFor()).as(method.getName() + " keeps writes on ApiException")
                .contains(ApiException.class);
    }
}
