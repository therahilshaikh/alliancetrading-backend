package in.ruhmans.alliancetrading.user.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import in.ruhmans.alliancetrading.bank.domain.BankAccountStatus;
import in.ruhmans.alliancetrading.bank.repository.BankAccountRepository;
import in.ruhmans.alliancetrading.common.error.ApiException;
import in.ruhmans.alliancetrading.common.error.ErrorCode;
import in.ruhmans.alliancetrading.common.pagination.CursorPage;
import in.ruhmans.alliancetrading.common.security.SecurityUtils;
import in.ruhmans.alliancetrading.ledger.domain.UserBalance;
import in.ruhmans.alliancetrading.ledger.repository.UserBalanceRepository;
import in.ruhmans.alliancetrading.ledger.service.LedgerQueryService;
import in.ruhmans.alliancetrading.settings.service.SettingsService;
import in.ruhmans.alliancetrading.user.domain.UserStatus;
import in.ruhmans.alliancetrading.user.repository.UserRepository;
import in.ruhmans.alliancetrading.user.service.ProfileService;
import in.ruhmans.alliancetrading.withdrawal.repository.WithdrawalRequestRepository;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
public class MeController {

    private final UserBalanceRepository userBalanceRepository;
    private final UserRepository userRepository;
    private final BankAccountRepository bankAccountRepository;
    private final SettingsService settingsService;
    private final WithdrawalRequestRepository withdrawalRequestRepository;
    private final LedgerQueryService ledgerQueryService;
    private final ProfileService profileService;

    public MeController(UserBalanceRepository userBalanceRepository,
                        UserRepository userRepository,
                        BankAccountRepository bankAccountRepository,
                        SettingsService settingsService,
                        WithdrawalRequestRepository withdrawalRequestRepository,
                        LedgerQueryService ledgerQueryService,
                        ProfileService profileService) {
        this.userBalanceRepository = userBalanceRepository;
        this.userRepository = userRepository;
        this.bankAccountRepository = bankAccountRepository;
        this.settingsService = settingsService;
        this.withdrawalRequestRepository = withdrawalRequestRepository;
        this.ledgerQueryService = ledgerQueryService;
        this.profileService = profileService;
    }

    public record MeSummary(BigDecimal principal, BigDecimal profit, BigDecimal currentValue,
                            BigDecimal withdrawable, BigDecimal held,
                            boolean canWithdraw, String reasonIfNot) {}

    @GetMapping("/profile")
    public ResponseEntity<ProfileService.Profile> profile() {
        return ResponseEntity.ok(profileService.profile(SecurityUtils.currentUserId()));
    }

    @GetMapping("/summary")
    public ResponseEntity<MeSummary> summary() {
        UUID userId = SecurityUtils.currentUserId();

        UserBalance balance = userBalanceRepository.findById(userId)
                .orElse(new UserBalance(userId, Instant.now()));

        var user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "User not found"));

        boolean canWithdraw = true;
        String reason = null;

        if (user.getStatus() != UserStatus.APPROVED) {
            canWithdraw = false;
            reason = "KYC not approved";
        } else {
            var verifiedBank = bankAccountRepository.findByUserIdAndStatus(userId, BankAccountStatus.VERIFIED);
            if (verifiedBank.isEmpty()) {
                canWithdraw = false;
                reason = "No verified bank account";
            } else if (verifiedBank.get().getCooloffUntil() != null
                    && verifiedBank.get().getCooloffUntil().isAfter(Instant.now())) {
                canWithdraw = false;
                reason = "Bank change cooling-off period active";
            }
        }

        if (canWithdraw && withdrawalRequestRepository.hasOpenWithdrawal(userId)) {
            canWithdraw = false;
            reason = "An open withdrawal request already exists";
        }

        BigDecimal withdrawable = balance.withdrawableProfit();
        if (canWithdraw && withdrawable.compareTo(settingsService.minProfitWithdrawal()) < 0) {
            canWithdraw = false;
            reason = "Below minimum withdrawal amount";
        }

        return ResponseEntity.ok(new MeSummary(
                balance.getPrincipal(), balance.getProfit(), balance.currentValue(),
                withdrawable, balance.getHeld(), canWithdraw, reason));
    }

    @GetMapping("/ledger")
    public ResponseEntity<CursorPage<LedgerQueryService.LedgerEntryView>> ledger(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(ledgerQueryService.page(SecurityUtils.currentUserId(), cursor, limit));
    }
}
