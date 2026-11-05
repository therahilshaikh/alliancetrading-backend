package in.ruhmans.alliancetrading.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import in.ruhmans.alliancetrading.audit.service.AuditService;
import in.ruhmans.alliancetrading.common.error.ApiException;
import in.ruhmans.alliancetrading.common.error.ErrorCode;
import in.ruhmans.alliancetrading.payment.domain.PaymentAccount;
import in.ruhmans.alliancetrading.payment.repository.PaymentAccountRepository;
import in.ruhmans.alliancetrading.settings.domain.AppSetting;
import in.ruhmans.alliancetrading.settings.domain.FeeSetting;
import in.ruhmans.alliancetrading.settings.domain.PaymentSetting;
import in.ruhmans.alliancetrading.settings.domain.WithdrawalType;
import in.ruhmans.alliancetrading.settings.repository.AppSettingRepository;
import in.ruhmans.alliancetrading.settings.repository.FeeSettingRepository;
import in.ruhmans.alliancetrading.settings.repository.PaymentSettingRepository;
import in.ruhmans.alliancetrading.settings.service.SettingsService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class SettingsServiceTest {

    private PaymentSettingRepository paymentRepo;
    private FeeSettingRepository feeRepo;
    private AppSettingRepository appRepo;
    private AuditService auditService;
    private PaymentAccountRepository accountRepo;
    private SettingsService service;
    private Clock clock;
    private final UUID actor = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        paymentRepo = Mockito.mock(PaymentSettingRepository.class);
        feeRepo = Mockito.mock(FeeSettingRepository.class);
        appRepo = Mockito.mock(AppSettingRepository.class);
        auditService = Mockito.mock(AuditService.class);
        clock = Clock.fixed(Instant.now(), ZoneId.of("Asia/Kolkata"));
        accountRepo = Mockito.mock(PaymentAccountRepository.class);
        service = new SettingsService(paymentRepo, feeRepo, appRepo, accountRepo, auditService, clock);
        when(paymentRepo.save(any(PaymentSetting.class))).thenAnswer(i -> i.getArgument(0));
        when(feeRepo.save(any(FeeSetting.class))).thenAnswer(i -> i.getArgument(0));
        when(appRepo.save(any(AppSetting.class))).thenAnswer(i -> i.getArgument(0));
    }

    // ---- Save applies directly ----

    private PaymentAccount account(String vpa) {
        PaymentAccount a = new PaymentAccount(vpa, vpa, "Alliance", null, actor, clock.instant());
        when(accountRepo.findById(a.getId())).thenReturn(Optional.of(a));
        return a;
    }

    @Test
    void savingNowIsActiveImmediatelyAndAuditsOldAndNewAccounts() {
        PaymentAccount oldAccount = account("old@upi");
        PaymentSetting old = new PaymentSetting(new BigDecimal("10000"), oldAccount.getId(),
                clock.instant().minusSeconds(86400), actor, clock.instant());
        when(paymentRepo.findCurrent(any())).thenReturn(Optional.of(old));
        PaymentAccount newAccount = account("new@upi");

        PaymentSetting s = service.savePayment(new BigDecimal("50000.00"), newAccount, clock.instant(), actor);

        assertThat(s.getPaymentAccountId()).isEqualTo(newAccount.getId());
        assertThat(service.statusOf(s.getEffectiveFrom())).isEqualTo(SettingsService.Status.ACTIVE);
        verify(auditService).recordAdmin(eq(actor), eq("SETTING_CHANGED"), eq("payment_setting"),
                eq(s.getId().toString()), contains(oldAccount.getId() + "\",\"vpa\":\"old@upi"),
                contains(newAccount.getId() + "\",\"vpa\":\"new@upi"));
    }

    @Test
    void savingForLaterIsScheduled() {
        Instant tomorrow = clock.instant().plusSeconds(86400);

        FeeSetting s = service.saveFee(WithdrawalType.PROFIT, new BigDecimal("0.0300"), tomorrow, actor);

        assertThat(s.getEffectiveFrom()).isEqualTo(tomorrow);
        assertThat(service.statusOf(s.getEffectiveFrom())).isEqualTo(SettingsService.Status.SCHEDULED);
    }

    @Test
    void effectiveFromInThePastIsRejected() {
        assertThatThrownBy(() -> service.savePayment(new BigDecimal("50000"), account("a@upi"),
                clock.instant().minusSeconds(3600), actor))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("now or later");
        verify(paymentRepo, never()).save(any());
    }

    @Test
    void effectiveFromSlightlyInThePastCountsAsNow() {
        FeeSetting s = service.saveFee(WithdrawalType.PROFIT, new BigDecimal("0.0300"),
                clock.instant().minusSeconds(30), actor);

        assertThat(s.getEffectiveFrom()).isEqualTo(clock.instant());
    }

    // ---- Cancel a scheduled change ----

    @Test
    void cancellingAScheduledChangeDeletesItAndAudits() {
        AppSetting s = new AppSetting(AppSetting.MIN_APP_VERSION, "2.0.0",
                clock.instant().plusSeconds(3600), actor, clock.instant());
        when(appRepo.findById(s.getId())).thenReturn(Optional.of(s));

        service.cancelScheduled(SettingsService.Kind.APP, s.getId(), actor);

        verify(appRepo).delete(s);
        verify(auditService).recordAdmin(eq(actor), eq("SETTING_SCHEDULE_CANCELLED"), eq("app_setting"),
                eq(s.getId().toString()), contains("2.0.0"), eq(null));
    }

    @Test
    void cancellingAnActiveChangeIsAConflict() {
        PaymentSetting s = new PaymentSetting(new BigDecimal("50000"), UUID.randomUUID(),
                clock.instant().minusSeconds(60), actor, clock.instant());
        when(paymentRepo.findById(s.getId())).thenReturn(Optional.of(s));

        assertThatThrownBy(() -> service.cancelScheduled(SettingsService.Kind.PAYMENT, s.getId(), actor))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).code()).isEqualTo(ErrorCode.ILLEGAL_STATE_TRANSITION));
        verify(paymentRepo, never()).delete(any());
    }

    // ---- Default payment account ----

    @Test
    void defaultAccountIsInUseWhenCurrentOrScheduled() {
        UUID current = UUID.randomUUID();
        UUID scheduled = UUID.randomUUID();
        when(paymentRepo.findCurrent(any())).thenReturn(Optional.of(new PaymentSetting(new BigDecimal("10000"), current,
                clock.instant().minusSeconds(60), actor, clock.instant())));
        when(paymentRepo.existsByPaymentAccountIdAndEffectiveFromAfter(eq(scheduled), any())).thenReturn(true);

        assertThat(service.isDefaultPaymentAccount(current)).isTrue();
        assertThat(service.isDefaultPaymentAccount(scheduled)).isTrue();
        assertThat(service.isDefaultPaymentAccount(UUID.randomUUID())).isFalse();
        assertThat(service.defaultPaymentAccountId()).contains(current);
    }

    // ---- Readers ----

    @Test
    void currentPaymentReturnsTheRowInForce() {
        PaymentSetting older = new PaymentSetting(new BigDecimal("10000"), UUID.randomUUID(),
                clock.instant().minusSeconds(86400), UUID.randomUUID(), clock.instant());
        when(paymentRepo.findCurrent(any())).thenReturn(Optional.of(older));

        assertThat(service.currentPaymentSetting().getFixedAmount()).isEqualByComparingTo("10000");
    }

    @Test
    void currentPaymentThrowsWhenNone() {
        when(paymentRepo.findCurrent(any())).thenReturn(Optional.empty());
        assertThatThrownBy(service::currentPaymentSetting)
                .isInstanceOf(ApiException.class);
    }

    @Test
    void appSettingValueFallsBackToDefault() {
        when(appRepo.findCurrent(any(), any())).thenReturn(Optional.empty());
        assertThat(service.minAppVersion()).isEqualTo("0.0.0");
        assertThat(service.principalWithdrawalEnabled()).isFalse();
        assertThat(service.bankChangeCooloffHours()).isEqualTo(48L);
    }

    @Test
    void appSettingValueReadsFromRepo() {
        AppSetting s = new AppSetting(AppSetting.MIN_APP_VERSION, "1.2.0",
                clock.instant().minusSeconds(60), UUID.randomUUID(), clock.instant());
        when(appRepo.findCurrent(eq(AppSetting.MIN_APP_VERSION), any())).thenReturn(Optional.of(s));

        assertThat(service.minAppVersion()).isEqualTo("1.2.0");
    }

    // ---- App setting validation ----

    @Test
    void appSettingKeyIsNormalisedToTheKeyTheReadersUse() {
        AppSetting s = service.saveAppSetting(" MIN_PROFIT_WITHDRAWAL ", "1000", clock.instant(), actor);

        assertThat(s.getKey()).isEqualTo(AppSetting.MIN_PROFIT_WITHDRAWAL);
    }

    @Test
    void unknownAppSettingKeyRejected() {
        assertThatThrownBy(() -> service.saveAppSetting("max_daily_profit", "10", clock.instant(), actor))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Unknown setting key");
    }

    @Test
    void appSettingValueMustMatchItsType() {
        assertThatThrownBy(() -> service.saveAppSetting(AppSetting.SECOND_APPROVAL_THRESHOLD, "-5",
                clock.instant(), actor))
                .isInstanceOf(ApiException.class);
    }

    // ---- Overview ----

    @Test
    void overviewListsScheduledChangesWithTheirValues() {
        FeeSetting scheduled = new FeeSetting(WithdrawalType.PROFIT, new BigDecimal("0.0100"),
                clock.instant().plusSeconds(3600), UUID.randomUUID(), clock.instant());
        when(feeRepo.findScheduled(any())).thenReturn(List.of(scheduled));
        when(paymentRepo.findCurrent(any())).thenReturn(Optional.empty());

        var overview = service.overview();

        assertThat(overview.scheduledFees()).singleElement()
                .satisfies(f -> assertThat(f.getFeePct()).isEqualByComparingTo("0.0100"));
        assertThat(overview.appValues()).containsEntry(AppSetting.BANK_CHANGE_COOLOFF_HOURS, "48");
    }
}
