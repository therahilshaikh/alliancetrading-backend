package in.ruhmans.alliancetrading.settings.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import in.ruhmans.alliancetrading.common.error.ApiException;
import in.ruhmans.alliancetrading.common.error.ErrorCode;
import in.ruhmans.alliancetrading.common.security.SecurityUtils;
import in.ruhmans.alliancetrading.payment.domain.PaymentAccount;
import in.ruhmans.alliancetrading.payment.service.DefaultPaymentService;
import in.ruhmans.alliancetrading.settings.domain.PaymentSetting;
import in.ruhmans.alliancetrading.settings.api.dto.AppSettingRequest;
import in.ruhmans.alliancetrading.settings.api.dto.FeeSettingRequest;
import in.ruhmans.alliancetrading.settings.api.dto.PaymentSettingRequest;
import in.ruhmans.alliancetrading.settings.domain.WithdrawalType;
import in.ruhmans.alliancetrading.settings.service.SettingsService;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * SUPER_ADMIN only. A save is final and takes effect at {@code effectiveFrom} (now or later); there is no
 * approval step. A change that has not started yet can be cancelled with DELETE.
 */
@RestController
@RequestMapping("/api/v1/admin/settings")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminSettingsController {

    private final SettingsService settingsService;
    private final DefaultPaymentService defaultPaymentService;

    public AdminSettingsController(SettingsService settingsService, DefaultPaymentService defaultPaymentService) {
        this.settingsService = settingsService;
        this.defaultPaymentService = defaultPaymentService;
    }

    /** {@code status}: ACTIVE when {@code effectiveFrom <= now}, otherwise SCHEDULED. */
    public record SettingResponse(UUID id, SettingsService.Status status, Instant effectiveFrom) {}

    /** Exactly one of {@code paymentAccountId} / {@code newAccount}; a new account is always saved to the list. */
    @PostMapping("/payment")
    public ResponseEntity<SettingResponse> savePayment(@Valid @RequestBody PaymentSettingRequest request) {
        var s = defaultPaymentService.save(request.fixedAmount(), request.paymentAccountId(),
                request.newAccount() != null ? request.newAccount().toNewAccount() : null,
                request.effectiveFrom(), SecurityUtils.currentAdminId());
        return ResponseEntity.ok(response(s.getId(), s.getEffectiveFrom()));
    }

    @PostMapping("/fees")
    public ResponseEntity<SettingResponse> saveFee(@Valid @RequestBody FeeSettingRequest request) {
        var s = settingsService.saveFee(request.withdrawalType(), request.feePct(),
                request.effectiveFrom(), SecurityUtils.currentAdminId());
        return ResponseEntity.ok(response(s.getId(), s.getEffectiveFrom()));
    }

    @PostMapping("/app")
    public ResponseEntity<SettingResponse> saveAppSetting(@Valid @RequestBody AppSettingRequest request) {
        var s = settingsService.saveAppSetting(request.key(), request.value(),
                request.effectiveFrom(), SecurityUtils.currentAdminId());
        return ResponseEntity.ok(response(s.getId(), s.getEffectiveFrom()));
    }

    /** Cancels a scheduled change before it takes effect; 409 if it is already active. {@code kind}: payment, fees or app. */
    @DeleteMapping("/{kind}/{id}")
    public ResponseEntity<Void> cancelScheduled(@PathVariable String kind, @PathVariable UUID id) {
        settingsService.cancelScheduled(parseKind(kind), id, SecurityUtils.currentAdminId());
        return ResponseEntity.noContent().build();
    }

    /** The saved account a default payment points to (its QR is the uploaded one, or generated from the UPI ID). */
    public record PaymentAccountSummary(UUID id, String label, String vpa, String payeeName, boolean hasUploadedQr) {
        static PaymentAccountSummary of(PaymentAccount a) {
            return a == null ? null
                    : new PaymentAccountSummary(a.getId(), a.getLabel(), a.getVpa(), a.getPayeeName(),
                            a.getQrDocumentId() != null);
        }
    }

    public record PaymentValue(UUID id, BigDecimal fixedAmount, PaymentAccountSummary paymentAccount,
                               Instant effectiveFrom) {}

    public record FeeValue(UUID id, WithdrawalType withdrawalType, BigDecimal feePct, Instant effectiveFrom) {}

    /** {@code kind}: PAYMENT (fixedAmount, paymentAccount), FEE (withdrawalType, feePct as a fraction) or APP (key, value). */
    public record ScheduledSetting(SettingsService.Kind kind, UUID id, BigDecimal fixedAmount,
                                   PaymentAccountSummary paymentAccount,
                                   WithdrawalType withdrawalType, BigDecimal feePct, String key, String value,
                                   Instant effectiveFrom, UUID createdBy, Instant createdAt) {}

    public record SettingsView(PaymentValue payment, List<FeeValue> fees, Map<String, String> app,
                               List<ScheduledSetting> scheduled) {}

    /** Values in force now plus saved changes that start later (soonest first). */
    @GetMapping
    public ResponseEntity<SettingsView> overview() {
        var o = settingsService.overview();
        List<PaymentSetting> payments = new ArrayList<>(o.scheduledPayments());
        o.payment().ifPresent(payments::add);
        Map<UUID, PaymentAccount> accounts = settingsService.accountsFor(payments);

        List<ScheduledSetting> scheduled = new ArrayList<>();
        o.scheduledPayments().forEach(s -> scheduled.add(new ScheduledSetting(SettingsService.Kind.PAYMENT, s.getId(),
                s.getFixedAmount(), PaymentAccountSummary.of(accounts.get(s.getPaymentAccountId())), null, null, null,
                null, s.getEffectiveFrom(), s.getCreatedBy(), s.getCreatedAt())));
        o.scheduledFees().forEach(s -> scheduled.add(new ScheduledSetting(SettingsService.Kind.FEE, s.getId(), null, null,
                s.getWithdrawalType(), s.getFeePct(), null, null, s.getEffectiveFrom(), s.getCreatedBy(),
                s.getCreatedAt())));
        o.scheduledApp().forEach(s -> scheduled.add(new ScheduledSetting(SettingsService.Kind.APP, s.getId(), null, null,
                null, null, s.getKey(), s.getValue(), s.getEffectiveFrom(), s.getCreatedBy(), s.getCreatedAt())));
        scheduled.sort(Comparator.comparing(ScheduledSetting::effectiveFrom));

        return ResponseEntity.ok(new SettingsView(
                o.payment().map(p -> new PaymentValue(p.getId(), p.getFixedAmount(),
                                PaymentAccountSummary.of(accounts.get(p.getPaymentAccountId())), p.getEffectiveFrom()))
                        .orElse(null),
                o.fees().values().stream()
                        .map(f -> new FeeValue(f.getId(), f.getWithdrawalType(), f.getFeePct(), f.getEffectiveFrom()))
                        .toList(),
                o.appValues(), scheduled));
    }

    private SettingResponse response(UUID id, Instant effectiveFrom) {
        return new SettingResponse(id, settingsService.statusOf(effectiveFrom), effectiveFrom);
    }

    private static SettingsService.Kind parseKind(String kind) {
        return switch (kind) {
            case "payment" -> SettingsService.Kind.PAYMENT;
            case "fees" -> SettingsService.Kind.FEE;
            case "app" -> SettingsService.Kind.APP;
            default -> throw new ApiException(ErrorCode.NOT_FOUND, "Unknown setting kind '" + kind + "'");
        };
    }
}
