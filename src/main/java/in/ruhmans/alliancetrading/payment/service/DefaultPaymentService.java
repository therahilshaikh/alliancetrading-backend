package in.ruhmans.alliancetrading.payment.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import in.ruhmans.alliancetrading.payment.domain.PaymentAccount;
import in.ruhmans.alliancetrading.settings.domain.PaymentSetting;
import in.ruhmans.alliancetrading.settings.service.SettingsService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Saves the default payment setting against a saved payment account. Picking an account, or creating one
 * inline, happens in the same transaction as the setting, so a failed save leaves no stray account.
 */
@Service
public class DefaultPaymentService {

    private final PaymentService paymentService;
    private final SettingsService settingsService;

    public DefaultPaymentService(PaymentService paymentService, SettingsService settingsService) {
        this.paymentService = paymentService;
        this.settingsService = settingsService;
    }

    /** Exactly one of {@code paymentAccountId} / {@code newAccount}; the account must be ACTIVE. */
    @Transactional
    public PaymentSetting save(BigDecimal fixedAmount, UUID paymentAccountId, PaymentService.NewAccount newAccount,
                               Instant effectiveFrom, UUID actorId) {
        PaymentAccount account = paymentService.resolveDefaultAccount(actorId, paymentAccountId, newAccount);
        return settingsService.savePayment(fixedAmount, account, effectiveFrom, actorId);
    }
}
