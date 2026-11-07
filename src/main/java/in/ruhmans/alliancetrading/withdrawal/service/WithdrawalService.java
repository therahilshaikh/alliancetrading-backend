package in.ruhmans.alliancetrading.withdrawal.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import in.ruhmans.alliancetrading.audit.service.AuditService;
import in.ruhmans.alliancetrading.bank.domain.BankAccount;
import in.ruhmans.alliancetrading.bank.domain.BankAccountStatus;
import in.ruhmans.alliancetrading.bank.repository.BankAccountRepository;
import in.ruhmans.alliancetrading.common.error.ApiException;
import in.ruhmans.alliancetrading.common.error.ErrorCode;
import in.ruhmans.alliancetrading.common.money.MoneyUtils;
import in.ruhmans.alliancetrading.investment.domain.Investment;
import in.ruhmans.alliancetrading.investment.domain.InvestmentStatus;
import in.ruhmans.alliancetrading.investment.repository.InvestmentRepository;
import in.ruhmans.alliancetrading.ledger.domain.Bucket;
import in.ruhmans.alliancetrading.ledger.domain.EntryType;
import in.ruhmans.alliancetrading.ledger.domain.UserBalance;
import in.ruhmans.alliancetrading.ledger.repository.UserBalanceRepository;
import in.ruhmans.alliancetrading.ledger.service.LedgerPostingService;
import in.ruhmans.alliancetrading.notification.service.OutboxService;
import in.ruhmans.alliancetrading.settings.domain.FeeSetting;
import in.ruhmans.alliancetrading.settings.domain.WithdrawalType;
import in.ruhmans.alliancetrading.settings.service.SettingsService;
import in.ruhmans.alliancetrading.user.domain.User;
import in.ruhmans.alliancetrading.user.domain.UserStatus;
import in.ruhmans.alliancetrading.user.repository.UserRepository;
import in.ruhmans.alliancetrading.withdrawal.domain.WithdrawalQuote;
import in.ruhmans.alliancetrading.withdrawal.domain.WithdrawalRequest;
import in.ruhmans.alliancetrading.withdrawal.domain.WithdrawalStatus;
import in.ruhmans.alliancetrading.withdrawal.repository.WithdrawalQuoteRepository;
import in.ruhmans.alliancetrading.withdrawal.repository.WithdrawalRequestRepository;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WithdrawalService {

    private static final Duration QUOTE_TTL = Duration.ofMinutes(10);

    private final WithdrawalRequestRepository withdrawalRepository;
    private final WithdrawalQuoteRepository quoteRepository;
    private final UserRepository userRepository;
    private final BankAccountRepository bankAccountRepository;
    private final InvestmentRepository investmentRepository;
    private final UserBalanceRepository userBalanceRepository;
    private final SettingsService settingsService;
    private final LedgerPostingService ledgerPostingService;
    private final OutboxService outboxService;
    private final AuditService auditService;
    private final Clock clock;

    public WithdrawalService(WithdrawalRequestRepository withdrawalRepository,
                             WithdrawalQuoteRepository quoteRepository,
                             UserRepository userRepository,
                             BankAccountRepository bankAccountRepository,
                             InvestmentRepository investmentRepository,
                             UserBalanceRepository userBalanceRepository,
                             SettingsService settingsService,
                             LedgerPostingService ledgerPostingService,
                             OutboxService outboxService,
                             AuditService auditService, Clock clock) {
        this.withdrawalRepository = withdrawalRepository;
        this.quoteRepository = quoteRepository;
        this.userRepository = userRepository;
        this.bankAccountRepository = bankAccountRepository;
        this.investmentRepository = investmentRepository;
        this.userBalanceRepository = userBalanceRepository;
        this.settingsService = settingsService;
        this.ledgerPostingService = ledgerPostingService;
        this.outboxService = outboxService;
        this.auditService = auditService;
        this.clock = clock;
    }

    public record QuoteResult(WithdrawalQuote quote, BankAccount bank) {}

    // ---- User: quote ----

    @Transactional
    public QuoteResult quote(UUID userId, BigDecimal amount, WithdrawalType type) {
        Instant now = clock.instant();
        BigDecimal gross = MoneyUtils.requirePositive(amount, "amount");

        if (type == WithdrawalType.PRINCIPAL && !settingsService.principalWithdrawalEnabled()) {
            throw new ApiException(ErrorCode.FEATURE_DISABLED, "Principal withdrawal is not enabled");
        }

        User user = requireApprovedUser(userId);
        BankAccount bank = requireVerifiedBank(userId, now);
        requireNoOpenWithdrawal(userId);
        Investment investment = requireActiveInvestment(userId);

        UserBalance balance = userBalanceRepository.findById(userId)
                .orElse(new UserBalance(userId, now));

        if (type == WithdrawalType.PROFIT) {
            BigDecimal withdrawable = balance.withdrawableProfit();
            BigDecimal min = settingsService.minProfitWithdrawal();
            if (gross.compareTo(min) < 0) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED,
                        "Amount below minimum withdrawal of " + min);
            }
            if (gross.compareTo(withdrawable) > 0) {
                throw new ApiException(ErrorCode.INSUFFICIENT_WITHDRAWABLE,
                        "Amount exceeds withdrawable profit of " + withdrawable);
            }
        } else {
            if (gross.compareTo(balance.getPrincipal()) > 0) {
                throw new ApiException(ErrorCode.INSUFFICIENT_WITHDRAWABLE,
                        "Amount exceeds remaining principal of " + balance.getPrincipal());
            }
        }

        FeeSetting feeSetting = settingsService.currentFeeSetting(type);
        var breakdown = FeeCalculator.compute(gross, feeSetting.getFeePct());

        WithdrawalQuote quote = quoteRepository.save(new WithdrawalQuote(userId, type, gross,
                feeSetting.getId(), breakdown.feePct(), breakdown.fee(), breakdown.net(),
                now.plus(QUOTE_TTL), now));

        return new QuoteResult(quote, bank);
    }

    // ---- User: request ----

    @Transactional
    public WithdrawalRequest request(UUID userId, UUID quoteId, String idempotencyKey) {
        Instant now = clock.instant();

        // Idempotent replay
        var existing = withdrawalRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return existing.get();
        }

        WithdrawalQuote quote = quoteRepository.findById(quoteId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Quote not found"));
        if (!quote.getUserId().equals(userId)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Not your quote");
        }
        if (quote.isUsed()) {
            throw new ApiException(ErrorCode.QUOTE_EXPIRED, "Quote already used");
        }
        if (quote.isExpired(now)) {
            throw new ApiException(ErrorCode.QUOTE_EXPIRED, "Quote expired");
        }

        User user = requireApprovedUser(userId);
        BankAccount bank = requireVerifiedBank(userId, now);
        Investment investment = requireActiveInvestment(userId);

        BigDecimal gross = quote.getGrossAmount();
        BigDecimal fee = quote.getFeeAmount();
        BigDecimal net = quote.getNetAmount();

        WithdrawalRequest wr = new WithdrawalRequest(userId, investment.getId(),
                quote.getWithdrawalType(), gross, quote.getFeeSettingId(),
                quote.getFeePct(), fee, net,
                bank.getId(), bank.getHolderName(), bank.getAccountNumberLast4(),
                bank.getIfsc(), bank.getVpa(), idempotencyKey, now);

        try {
            withdrawalRepository.saveAndFlush(wr);
        } catch (DataIntegrityViolationException e) {
            // Unique idempotency key raced or one-open-withdrawal violated
            var replay = withdrawalRepository.findByIdempotencyKey(idempotencyKey);
            if (replay.isPresent()) {
                return replay.get();
            }
            throw new ApiException(ErrorCode.OPEN_WITHDRAWAL_EXISTS,
                    "An open withdrawal already exists");
        }

        // Lock funds
        if (quote.getWithdrawalType() == WithdrawalType.PROFIT) {
            ledgerPostingService.post(new LedgerPostingService.Journal(userId, investment.getId(),
                    List.of(
                            new LedgerPostingService.Posting(Bucket.PROFIT, EntryType.WITHDRAWAL_HOLD,
                                    quote.getGrossAmount().negate()),
                            new LedgerPostingService.Posting(Bucket.HELD, EntryType.WITHDRAWAL_HOLD,
                                    quote.getGrossAmount())),
                    "withdrawal", wr.getId().toString(),
                    "withdrawal-hold:" + wr.getId(), "USER", userId));
        } else {
            // Principal withdrawal: hold the gross from the principal bucket only
            ledgerPostingService.post(new LedgerPostingService.Journal(userId, investment.getId(),
                    List.of(
                            new LedgerPostingService.Posting(Bucket.PRINCIPAL, EntryType.WITHDRAWAL_HOLD,
                                    gross.negate()),
                            new LedgerPostingService.Posting(Bucket.HELD, EntryType.WITHDRAWAL_HOLD,
                                    gross)),
                    "withdrawal", wr.getId().toString(),
                    "withdrawal-hold:" + wr.getId(), "USER", userId));
        }

        quote.markUsed(now);
        quoteRepository.save(quote);

        outboxService.notifyUser("WITHDRAWAL_REQUESTED", userId,
                "{\"withdrawalId\":\"" + wr.getId() + "\",\"amount\":" + quote.getGrossAmount() + "}");
        auditService.recordUser(userId, "WITHDRAWAL_REQUESTED", "withdrawal_request",
                wr.getId().toString(), null, null);
        return wr;
    }

    // ---- User: cancel ----

    @Transactional
    public void cancel(UUID userId, UUID withdrawalId) {
        Instant now = clock.instant();
        WithdrawalRequest wr = getForUser(withdrawalId, userId);
        if (wr.getStatus() != WithdrawalStatus.REQUESTED) {
            throw new ApiException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Only REQUESTED withdrawals can be cancelled");
        }
        releaseHold(wr, userId);
        wr.cancel(now);
        withdrawalRepository.save(wr);
        auditService.recordUser(userId, "WITHDRAWAL_CANCELLED", "withdrawal_request",
                withdrawalId.toString(), null, null);
    }

    // ---- Admin: list ----

    @Transactional(readOnly = true)
    public Page<WithdrawalRequest> listByStatus(WithdrawalStatus status, Pageable pageable) {
        return status != null ? withdrawalRepository.findByStatus(status, pageable)
                : withdrawalRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public List<WithdrawalRequest> listOwn(UUID userId) {
        return withdrawalRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public WithdrawalRequest getById(UUID id) {
        return withdrawalRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Withdrawal not found"));
    }

    // ---- Admin: approve / second-approve / reject / mark-paid / mark-failed ----

    @Transactional
    public void approve(UUID id, UUID adminId) {
        Instant now = clock.instant();
        WithdrawalRequest wr = getById(id);
        if (wr.getStatus() != WithdrawalStatus.REQUESTED
                && wr.getStatus() != WithdrawalStatus.PAYOUT_FAILED) {
            throw new ApiException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Only REQUESTED or PAYOUT_FAILED withdrawals can be approved");
        }
        wr.approve(adminId, now);
        withdrawalRepository.save(wr);
        auditService.recordAdmin(adminId, "WITHDRAWAL_APPROVED", "withdrawal_request",
                id.toString(), null, null);
    }

    @Transactional
    public void secondApprove(UUID id, UUID adminId) {
        Instant now = clock.instant();
        WithdrawalRequest wr = getById(id);
        if (wr.getStatus() != WithdrawalStatus.APPROVED) {
            throw new ApiException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Only APPROVED withdrawals can be second-approved");
        }
        if (wr.getApprovedBy() == null || wr.getApprovedBy().equals(adminId)) {
            throw new ApiException(ErrorCode.MAKER_CHECKER_VIOLATION,
                    "Second approver must differ from the first");
        }
        if (wr.getGrossAmount().compareTo(settingsService.secondApprovalThreshold()) < 0) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "Second approval not required for this amount");
        }
        wr.secondApprove(adminId, now);
        withdrawalRepository.save(wr);
        auditService.recordAdmin(adminId, "WITHDRAWAL_SECOND_APPROVED", "withdrawal_request",
                id.toString(), null, null);
    }

    @Transactional
    public void reject(UUID id, UUID adminId, String reason) {
        Instant now = clock.instant();
        WithdrawalRequest wr = getById(id);
        if (wr.getStatus() != WithdrawalStatus.REQUESTED
                && wr.getStatus() != WithdrawalStatus.PAYOUT_FAILED) {
            throw new ApiException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Only REQUESTED or PAYOUT_FAILED withdrawals can be rejected");
        }
        releaseHold(wr, wr.getUserId());
        wr.reject(reason, now);
        withdrawalRepository.save(wr);

        // For principal withdrawals, reverse the exit request on the investment
        outboxService.notifyUser("WITHDRAWAL_REJECTED", wr.getUserId(),
                AuditService.json("withdrawalId", id, "reason", reason));
        auditService.recordAdmin(adminId, "WITHDRAWAL_REJECTED", "withdrawal_request",
                id.toString(), null, AuditService.json("reason", reason));
    }

    @Transactional
    public void markPaid(UUID id, UUID adminId, String payoutUtr, UUID payoutProofDocumentId) {
        Instant now = clock.instant();
        WithdrawalRequest wr = getById(id);
        if (wr.getStatus() != WithdrawalStatus.APPROVED) {
            throw new ApiException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Only APPROVED withdrawals can be marked paid");
        }
        // Second approval check
        if (wr.getGrossAmount().compareTo(settingsService.secondApprovalThreshold()) >= 0
                && wr.getSecondApprovedBy() == null) {
            throw new ApiException(ErrorCode.MAKER_CHECKER_VIOLATION,
                    "Second approval required before payout");
        }

        // Ledger: HELD −net (PAYOUT), −fee (FEE)
        ledgerPostingService.post(new LedgerPostingService.Journal(wr.getUserId(), wr.getInvestmentId(),
                List.of(
                        new LedgerPostingService.Posting(Bucket.HELD, EntryType.WITHDRAWAL_PAYOUT,
                                wr.getNetAmount().negate()),
                        new LedgerPostingService.Posting(Bucket.HELD, EntryType.WITHDRAWAL_FEE,
                                wr.getFeeAmount().negate())),
                "withdrawal", wr.getId().toString(),
                "withdrawal-payout:" + wr.getId(), "ADMIN", adminId));

        wr.markPaid(adminId, payoutUtr, payoutProofDocumentId, now);
        withdrawalRepository.save(wr);

        // For principal withdrawals, debit the tranche(s), oldest first; close any fully withdrawn
        if (wr.getWithdrawalType() == WithdrawalType.PRINCIPAL) {
            debitTranchesOldestFirst(wr.getUserId(), wr.getGrossAmount(), now);
        }

        outboxService.notifyUser("WITHDRAWAL_PAID", wr.getUserId(),
                "{\"withdrawalId\":\"" + id + "\",\"netAmount\":" + wr.getNetAmount() + "}");
        auditService.recordAdmin(adminId, "WITHDRAWAL_PAID", "withdrawal_request",
                id.toString(), null, null);
    }

    @Transactional
    public void markFailed(UUID id, UUID adminId, String failureReason) {
        Instant now = clock.instant();
        WithdrawalRequest wr = getById(id);
        if (wr.getStatus() != WithdrawalStatus.APPROVED) {
            throw new ApiException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Only APPROVED withdrawals can be marked failed");
        }
        wr.markFailed(failureReason, now);
        withdrawalRepository.save(wr);
        outboxService.notifyUser("WITHDRAWAL_PAYOUT_FAILED", wr.getUserId(),
                "{\"withdrawalId\":\"" + id + "\"}");
        auditService.recordAdmin(adminId, "WITHDRAWAL_PAYOUT_FAILED", "withdrawal_request",
                id.toString(), null, AuditService.json("failureReason", failureReason));
    }

    // ---- Helpers ----

    private WithdrawalRequest getForUser(UUID withdrawalId, UUID userId) {
        WithdrawalRequest wr = getById(withdrawalId);
        if (!wr.getUserId().equals(userId)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Not your withdrawal");
        }
        return wr;
    }

    private User requireApprovedUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "User not found"));
        if (user.getStatus() != UserStatus.APPROVED) {
            throw new ApiException(ErrorCode.ILLEGAL_STATE_TRANSITION, "KYC not approved");
        }
        return user;
    }

    private BankAccount requireVerifiedBank(UUID userId, Instant now) {
        BankAccount bank = bankAccountRepository
                .findByUserIdAndStatus(userId, BankAccountStatus.VERIFIED)
                .orElseThrow(() -> new ApiException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                        "No verified bank account"));
        if (bank.getCooloffUntil() != null && bank.getCooloffUntil().isAfter(now)) {
            throw new ApiException(ErrorCode.COOLING_OFF_ACTIVE,
                    "Bank change cooling-off period active until " + bank.getCooloffUntil());
        }
        return bank;
    }

    private void requireNoOpenWithdrawal(UUID userId) {
        if (withdrawalRepository.hasOpenWithdrawal(userId)) {
            throw new ApiException(ErrorCode.OPEN_WITHDRAWAL_EXISTS,
                    "An open withdrawal already exists");
        }
    }

    private Investment requireActiveInvestment(UUID userId) {
        return investmentRepository.findActiveByUserIdOldestFirst(userId).stream()
                .findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                        "No active investment found"));
    }

    /** Applies a principal debit across the user's ACTIVE investments, oldest first. */
    private void debitTranchesOldestFirst(UUID userId, BigDecimal amount, Instant now) {
        BigDecimal remaining = amount;
        for (Investment tranche : investmentRepository.findActiveByUserIdOldestFirst(userId)) {
            if (remaining.signum() <= 0) {
                break;
            }
            BigDecimal debit = tranche.getRemainingPrincipal().min(remaining);
            if (debit.signum() > 0) {
                tranche.debitPrincipal(debit, now);
                if (tranche.getRemainingPrincipal().signum() == 0) {
                    tranche.close(now);
                }
                investmentRepository.save(tranche);
                remaining = remaining.subtract(debit);
            }
        }
        if (remaining.signum() > 0) {
            throw new ApiException(ErrorCode.INSUFFICIENT_WITHDRAWABLE,
                    "Remaining principal is less than the held amount");
        }
    }

    private void releaseHold(WithdrawalRequest wr, UUID actorId) {
        // Reverse the hold: the gross returns to the bucket it came from
        Bucket source = wr.getWithdrawalType() == WithdrawalType.PROFIT ? Bucket.PROFIT : Bucket.PRINCIPAL;
        ledgerPostingService.post(new LedgerPostingService.Journal(wr.getUserId(),
                wr.getInvestmentId(),
                List.of(
                        new LedgerPostingService.Posting(source,
                                EntryType.WITHDRAWAL_RELEASE, wr.getGrossAmount()),
                        new LedgerPostingService.Posting(Bucket.HELD,
                                EntryType.WITHDRAWAL_RELEASE, wr.getGrossAmount().negate())),
                "withdrawal", wr.getId().toString(),
                "withdrawal-release:" + wr.getId(), "ADMIN", actorId));
    }
}
