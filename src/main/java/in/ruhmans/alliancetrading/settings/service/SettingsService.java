package in.ruhmans.alliancetrading.settings.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.TreeMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Payment default, withdrawal fees and app settings. A SUPER_ADMIN's save is final: it is stored as a new
 * row that takes effect at {@code effectiveFrom} (now or later). Older rows are kept as history.
 */
@Service
public class SettingsService {

    /** Allows for clock skew between the console and the server when it sends "now". */
    static final Duration EFFECTIVE_FROM_GRACE = Duration.ofMinutes(5);

    public enum Kind { PAYMENT, FEE, APP }

    public enum Status { ACTIVE, SCHEDULED }

    private final PaymentSettingRepository paymentSettingRepository;
    private final FeeSettingRepository feeSettingRepository;
    private final AppSettingRepository appSettingRepository;
    private final PaymentAccountRepository paymentAccountRepository;
    private final AuditService auditService;
    private final Clock clock;

    public SettingsService(PaymentSettingRepository paymentSettingRepository,
                           FeeSettingRepository feeSettingRepository,
                           AppSettingRepository appSettingRepository,
                           PaymentAccountRepository paymentAccountRepository,
                           AuditService auditService, Clock clock) {
        this.paymentSettingRepository = paymentSettingRepository;
        this.feeSettingRepository = feeSettingRepository;
        this.appSettingRepository = appSettingRepository;
        this.paymentAccountRepository = paymentAccountRepository;
        this.auditService = auditService;
        this.clock = clock;
    }

    // ---- Save (applies directly) ----

    /**
     * Saves the default payment. {@code account} is an ACTIVE saved payment account, already resolved by
     * {@code DefaultPaymentService} (picked, or created inline in the same transaction).
     */
    @Transactional
    public PaymentSetting savePayment(BigDecimal fixedAmount, PaymentAccount account,
                                      Instant effectiveFrom, UUID actorId) {
        Instant now = clock.instant();
        Instant from = resolveEffectiveFrom(effectiveFrom, now);
        String oldAudit = paymentSettingRepository.findCurrent(now).map(o -> AuditService.json(
                "fixedAmount", o.getFixedAmount(), "paymentAccountId", o.getPaymentAccountId(),
                "vpa", paymentAccountRepository.findById(o.getPaymentAccountId()).map(PaymentAccount::getVpa).orElse(null)))
                .orElse(null);
        PaymentSetting s = paymentSettingRepository.save(
                new PaymentSetting(fixedAmount, account.getId(), from, actorId, now));
        auditService.recordAdmin(actorId, "SETTING_CHANGED", "payment_setting", s.getId().toString(), oldAudit,
                AuditService.json("fixedAmount", fixedAmount, "paymentAccountId", account.getId(),
                        "vpa", account.getVpa(), "effectiveFrom", from));
        return s;
    }

    @Transactional
    public FeeSetting saveFee(WithdrawalType type, BigDecimal feePct, Instant effectiveFrom, UUID actorId) {
        Instant now = clock.instant();
        Instant from = resolveEffectiveFrom(effectiveFrom, now);
        Optional<FeeSetting> old = feeSettingRepository.findCurrent(type, now);
        FeeSetting s = feeSettingRepository.save(new FeeSetting(type, feePct, from, actorId, now));
        auditService.recordAdmin(actorId, "SETTING_CHANGED", "fee_setting", s.getId().toString(),
                old.map(o -> AuditService.json("withdrawalType", type, "feePct", o.getFeePct())).orElse(null),
                AuditService.json("withdrawalType", type, "feePct", feePct, "effectiveFrom", from));
        return s;
    }

    @Transactional
    public AppSetting saveAppSetting(String key, String value, Instant effectiveFrom, UUID actorId) {
        Instant now = clock.instant();
        String normalizedKey = key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
        String normalizedValue = validateAppSetting(normalizedKey, value == null ? "" : value.trim());
        Instant from = resolveEffectiveFrom(effectiveFrom, now);
        String oldValue = appSettingValue(normalizedKey, APP_SETTING_DEFAULTS.get(normalizedKey));
        AppSetting s = appSettingRepository.save(
                new AppSetting(normalizedKey, normalizedValue, from, actorId, now));
        auditService.recordAdmin(actorId, "SETTING_CHANGED", "app_setting", s.getId().toString(),
                AuditService.json("key", normalizedKey, "value", oldValue),
                AuditService.json("key", normalizedKey, "value", normalizedValue, "effectiveFrom", from));
        return s;
    }

    /** ACTIVE once {@code effectiveFrom} has passed, otherwise SCHEDULED. */
    public Status statusOf(Instant effectiveFrom) {
        return effectiveFrom.isAfter(clock.instant()) ? Status.SCHEDULED : Status.ACTIVE;
    }

    /** Removes a saved change that has not taken effect yet (409 once it is active). */
    @Transactional
    public void cancelScheduled(Kind kind, UUID id, UUID actorId) {
        Instant now = clock.instant();
        String entity;
        String detail;
        switch (kind) {
            case PAYMENT -> {
                PaymentSetting s = paymentSettingRepository.findById(id)
                        .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Payment setting not found"));
                requireScheduled(s.getEffectiveFrom(), now);
                paymentSettingRepository.delete(s);
                entity = "payment_setting";
                detail = AuditService.json("fixedAmount", s.getFixedAmount(), "paymentAccountId", s.getPaymentAccountId(),
                        "effectiveFrom", s.getEffectiveFrom());
            }
            case FEE -> {
                FeeSetting s = feeSettingRepository.findById(id)
                        .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Fee setting not found"));
                requireScheduled(s.getEffectiveFrom(), now);
                feeSettingRepository.delete(s);
                entity = "fee_setting";
                detail = AuditService.json("withdrawalType", s.getWithdrawalType(), "feePct", s.getFeePct(),
                        "effectiveFrom", s.getEffectiveFrom());
            }
            default -> {
                AppSetting s = appSettingRepository.findById(id)
                        .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "App setting not found"));
                requireScheduled(s.getEffectiveFrom(), now);
                appSettingRepository.delete(s);
                entity = "app_setting";
                detail = AuditService.json("key", s.getKey(), "value", s.getValue(),
                        "effectiveFrom", s.getEffectiveFrom());
            }
        }
        auditService.recordAdmin(actorId, "SETTING_SCHEDULE_CANCELLED", entity, id.toString(), detail, null);
    }

    // ---- Current-value readers (used by other modules) ----

    @Transactional(readOnly = true)
    public PaymentSetting currentPaymentSetting() {
        return paymentSettingRepository.findCurrent(clock.instant())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "No active payment setting"));
    }

    /** Default investment amount pre-filled when an admin assigns a payment account. */
    @Transactional(readOnly = true)
    public Optional<BigDecimal> defaultInvestmentAmount() {
        return paymentSettingRepository.findCurrent(clock.instant()).map(PaymentSetting::getFixedAmount);
    }

    /** Saved payment account pre-selected when an admin assigns payment details. */
    @Transactional(readOnly = true)
    public Optional<UUID> defaultPaymentAccountId() {
        return paymentSettingRepository.findCurrent(clock.instant()).map(PaymentSetting::getPaymentAccountId);
    }

    /** True when the account is the default payment now or in a scheduled change, so it must not be archived. */
    @Transactional(readOnly = true)
    public boolean isDefaultPaymentAccount(UUID accountId) {
        Instant now = clock.instant();
        return paymentSettingRepository.findCurrent(now).filter(p -> p.getPaymentAccountId().equals(accountId)).isPresent()
                || paymentSettingRepository.existsByPaymentAccountIdAndEffectiveFromAfter(accountId, now);
    }

    /** Accounts referenced by the given payment settings, keyed by ID (one query). */
    @Transactional(readOnly = true)
    public Map<UUID, PaymentAccount> accountsFor(List<PaymentSetting> settings) {
        Map<UUID, PaymentAccount> accounts = new java.util.HashMap<>();
        paymentAccountRepository.findAllById(settings.stream().map(PaymentSetting::getPaymentAccountId)
                .collect(java.util.stream.Collectors.toSet())).forEach(a -> accounts.put(a.getId(), a));
        return accounts;
    }

    @Transactional(readOnly = true)
    public FeeSetting currentFeeSetting(WithdrawalType type) {
        return feeSettingRepository.findCurrent(type, clock.instant())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "No active fee setting for " + type));
    }

    @Transactional(readOnly = true)
    public String appSettingValue(String key, String defaultValue) {
        return appSettingRepository.findCurrent(key, clock.instant())
                .map(AppSetting::getValue)
                .orElse(defaultValue);
    }

    @Transactional(readOnly = true)
    public BigDecimal minProfitWithdrawal() {
        return new BigDecimal(appSettingValue(AppSetting.MIN_PROFIT_WITHDRAWAL, "0"));
    }

    @Transactional(readOnly = true)
    public boolean principalWithdrawalEnabled() {
        return Boolean.parseBoolean(appSettingValue(AppSetting.PRINCIPAL_WITHDRAWAL_ENABLED, "false"));
    }

    @Transactional(readOnly = true)
    public long bankChangeCooloffHours() {
        return Long.parseLong(appSettingValue(AppSetting.BANK_CHANGE_COOLOFF_HOURS, "48"));
    }

    @Transactional(readOnly = true)
    public BigDecimal secondApprovalThreshold() {
        return new BigDecimal(appSettingValue(AppSetting.SECOND_APPROVAL_THRESHOLD, "0"));
    }

    @Transactional(readOnly = true)
    public String minAppVersion() {
        return appSettingValue(AppSetting.MIN_APP_VERSION, "0.0.0");
    }

    // ---- Overview (admin settings page) ----

    /** Values in force now (with built-in defaults for app settings) and saved changes that start later. */
    public record Overview(Optional<PaymentSetting> payment, Map<WithdrawalType, FeeSetting> fees,
                           Map<String, String> appValues, List<PaymentSetting> scheduledPayments,
                           List<FeeSetting> scheduledFees, List<AppSetting> scheduledApp) {}

    @Transactional(readOnly = true)
    public Overview overview() {
        Instant now = clock.instant();
        Map<WithdrawalType, FeeSetting> fees = new EnumMap<>(WithdrawalType.class);
        for (WithdrawalType type : WithdrawalType.values()) {
            feeSettingRepository.findCurrent(type, now).ifPresent(f -> fees.put(type, f));
        }
        Map<String, String> appValues = new TreeMap<>();
        APP_SETTING_DEFAULTS.forEach((key, def) -> appValues.put(key, appSettingValue(key, def)));
        return new Overview(paymentSettingRepository.findCurrent(now), fees, appValues,
                paymentSettingRepository.findScheduled(now),
                feeSettingRepository.findScheduled(now),
                appSettingRepository.findScheduled(now));
    }

    // ---- Helpers ----

    /** Every supported app setting key and the value used when none has been saved. */
    public static final Map<String, String> APP_SETTING_DEFAULTS = Map.of(
            AppSetting.MIN_PROFIT_WITHDRAWAL, "0",
            AppSetting.PRINCIPAL_WITHDRAWAL_ENABLED, "false",
            AppSetting.BANK_CHANGE_COOLOFF_HOURS, "48",
            AppSetting.SECOND_APPROVAL_THRESHOLD, "0",
            AppSetting.MIN_APP_VERSION, "0.0.0");

    /** Missing means now; a time a few minutes in the past (clock skew) is treated as now; earlier is rejected. */
    static Instant resolveEffectiveFrom(Instant requested, Instant now) {
        if (requested == null || !requested.isAfter(now)) {
            if (requested != null && requested.isBefore(now.minus(EFFECTIVE_FROM_GRACE))) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "effectiveFrom must be now or later");
            }
            return now;
        }
        return requested;
    }

    private static void requireScheduled(Instant effectiveFrom, Instant now) {
        if (!effectiveFrom.isAfter(now)) {
            throw new ApiException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "This change is already in effect; save a new value instead");
        }
    }

    /** Rejects unknown keys (they would be stored but never read) and values the readers cannot parse. */
    private static String validateAppSetting(String key, String value) {
        if (!APP_SETTING_DEFAULTS.containsKey(key)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "Unknown setting key '" + key + "'. Allowed: " + String.join(", ",
                            APP_SETTING_DEFAULTS.keySet().stream().sorted().toList()));
        }
        boolean valid = switch (key) {
            case AppSetting.PRINCIPAL_WITHDRAWAL_ENABLED ->
                    value.equals("true") || value.equals("false");
            case AppSetting.BANK_CHANGE_COOLOFF_HOURS -> value.matches("\\d{1,4}");
            case AppSetting.MIN_PROFIT_WITHDRAWAL, AppSetting.SECOND_APPROVAL_THRESHOLD ->
                    value.matches("\\d{1,13}(\\.\\d{1,2})?");
            case AppSetting.MIN_APP_VERSION -> value.matches("\\d+\\.\\d+\\.\\d+");
            default -> false;
        };
        if (!valid) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Invalid value '" + value + "' for " + key);
        }
        return value;
    }
}
