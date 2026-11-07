package in.ruhmans.alliancetrading.withdrawal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import in.ruhmans.alliancetrading.audit.service.AuditService;
import in.ruhmans.alliancetrading.bank.domain.BankAccount;
import in.ruhmans.alliancetrading.bank.domain.BankAccountStatus;
import in.ruhmans.alliancetrading.bank.domain.BankAccountType;
import in.ruhmans.alliancetrading.bank.repository.BankAccountRepository;
import in.ruhmans.alliancetrading.common.error.ApiException;
import in.ruhmans.alliancetrading.investment.domain.Investment;
import in.ruhmans.alliancetrading.investment.domain.InvestmentStatus;
import in.ruhmans.alliancetrading.investment.repository.InvestmentRepository;
import in.ruhmans.alliancetrading.ledger.domain.Bucket;
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
import in.ruhmans.alliancetrading.withdrawal.service.WithdrawalService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class WithdrawalServiceTest {

    private WithdrawalRequestRepository withdrawalRepository;
    private WithdrawalQuoteRepository quoteRepository;
    private UserRepository userRepository;
    private BankAccountRepository bankAccountRepository;
    private InvestmentRepository investmentRepository;
    private UserBalanceRepository userBalanceRepository;
    private SettingsService settingsService;
    private LedgerPostingService ledgerPostingService;
    private WithdrawalService withdrawalService;
    private Clock clock;

    @BeforeEach
    void setUp() {
        withdrawalRepository = Mockito.mock(WithdrawalRequestRepository.class);
        quoteRepository = Mockito.mock(WithdrawalQuoteRepository.class);
        userRepository = Mockito.mock(UserRepository.class);
        bankAccountRepository = Mockito.mock(BankAccountRepository.class);
        investmentRepository = Mockito.mock(InvestmentRepository.class);
        userBalanceRepository = Mockito.mock(UserBalanceRepository.class);
        settingsService = Mockito.mock(SettingsService.class);
        ledgerPostingService = Mockito.mock(LedgerPostingService.class);
        clock = Clock.fixed(Instant.now(), ZoneId.of("Asia/Kolkata"));

        withdrawalService = new WithdrawalService(withdrawalRepository, quoteRepository,
                userRepository, bankAccountRepository, investmentRepository, userBalanceRepository,
                settingsService, ledgerPostingService, Mockito.mock(OutboxService.class),
                Mockito.mock(AuditService.class), clock);
    }

    private User approvedUser() {
        User user = new User("9876543210", clock.instant());
        user.setStatus(UserStatus.APPROVED);
        return user;
    }

    private BankAccount verifiedBank(UUID userId) {
        BankAccount bank = new BankAccount(userId, "Test User", "enc", "hash", "9012",
                "SBIN0123456", BankAccountType.SAVINGS, "user@upi", null, null, clock.instant());
        bank.markVerified(UUID.randomUUID(), clock.instant());
        return bank;
    }

    private Investment activeInvestment(UUID userId) {
        Investment inv = new Investment(userId, new BigDecimal("50000"), UUID.randomUUID(),
                UUID.randomUUID(), clock.instant());
        inv.approve(UUID.randomUUID(), clock.instant());
        return inv;
    }

    private void setupHappyPath(UUID userId) {
        User user = approvedUser();
        BankAccount bank = verifiedBank(userId);
        Investment inv = activeInvestment(userId);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(bankAccountRepository.findByUserIdAndStatus(userId, BankAccountStatus.VERIFIED))
                .thenReturn(Optional.of(bank));
        when(investmentRepository.findActiveByUserIdOldestFirst(userId)).thenReturn(List.of(inv));
        when(withdrawalRepository.hasOpenWithdrawal(userId)).thenReturn(false);
        when(settingsService.minProfitWithdrawal()).thenReturn(new BigDecimal("100"));
        when(settingsService.principalWithdrawalEnabled()).thenReturn(false);
        when(settingsService.secondApprovalThreshold()).thenReturn(new BigDecimal("50000"));

        FeeSetting feeSetting = new FeeSetting(WithdrawalType.PROFIT, new BigDecimal("0.0200"),
                clock.instant(), UUID.randomUUID(), clock.instant());
        when(settingsService.currentFeeSetting(WithdrawalType.PROFIT)).thenReturn(feeSetting);
    }

    @Test
    void quoteSucceeds() {
        UUID userId = UUID.randomUUID();
        setupHappyPath(userId);

        UserBalance balance = new UserBalance(userId, clock.instant());
        balance.apply(Bucket.PRINCIPAL, new BigDecimal("50000"), clock.instant());
        balance.apply(Bucket.PROFIT, new BigDecimal("10000"), clock.instant());
        when(userBalanceRepository.findById(userId)).thenReturn(Optional.of(balance));

        when(quoteRepository.save(any(WithdrawalQuote.class))).thenAnswer(i -> i.getArgument(0));

        var result = withdrawalService.quote(userId, new BigDecimal("5000"), WithdrawalType.PROFIT);
        assertThat(result.quote().getNetAmount()).isEqualByComparingTo("4900.00");
        assertThat(result.quote().getFeeAmount()).isEqualByComparingTo("100.00");
        assertThat(result.bank().getAccountNumberLast4()).isEqualTo("9012");
    }

    @Test
    void quoteRejectsBelowMinimum() {
        UUID userId = UUID.randomUUID();
        setupHappyPath(userId);

        UserBalance balance = new UserBalance(userId, clock.instant());
        balance.apply(Bucket.PROFIT, new BigDecimal("10000"), clock.instant());
        when(userBalanceRepository.findById(userId)).thenReturn(Optional.of(balance));

        assertThatThrownBy(() -> withdrawalService.quote(userId, new BigDecimal("50"),
                WithdrawalType.PROFIT))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("minimum withdrawal");
    }

    @Test
    void quoteRejectsInsufficientProfit() {
        UUID userId = UUID.randomUUID();
        setupHappyPath(userId);

        UserBalance balance = new UserBalance(userId, clock.instant());
        balance.apply(Bucket.PROFIT, new BigDecimal("1000"), clock.instant());
        when(userBalanceRepository.findById(userId)).thenReturn(Optional.of(balance));

        assertThatThrownBy(() -> withdrawalService.quote(userId, new BigDecimal("5000"),
                WithdrawalType.PROFIT))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).code().name())
                        .isEqualTo("INSUFFICIENT_WITHDRAWABLE"));
    }

    @Test
    void principalWithdrawalBlockedWhenDisabled() {
        UUID userId = UUID.randomUUID();
        setupHappyPath(userId);

        assertThatThrownBy(() -> withdrawalService.quote(userId, new BigDecimal("50000"),
                WithdrawalType.PRINCIPAL))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).code().name())
                        .isEqualTo("FEATURE_DISABLED"));
    }

    @Test
    void quoteRejectsWhenOpenWithdrawalExists() {
        UUID userId = UUID.randomUUID();
        setupHappyPath(userId);
        when(withdrawalRepository.hasOpenWithdrawal(userId)).thenReturn(true);

        assertThatThrownBy(() -> withdrawalService.quote(userId, new BigDecimal("5000"),
                WithdrawalType.PROFIT))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).code().name())
                        .isEqualTo("OPEN_WITHDRAWAL_EXISTS"));
    }

    @Test
    void requestWithExpiredQuoteFails() {
        UUID userId = UUID.randomUUID();
        WithdrawalQuote quote = new WithdrawalQuote(userId, WithdrawalType.PROFIT,
                new BigDecimal("5000"), UUID.randomUUID(), new BigDecimal("0.0200"),
                new BigDecimal("100"), new BigDecimal("4900"),
                clock.instant().minusSeconds(1), clock.instant());
        when(withdrawalRepository.findByIdempotencyKey("k")).thenReturn(Optional.empty());
        when(quoteRepository.findById(quote.getId())).thenReturn(Optional.of(quote));

        assertThatThrownBy(() -> withdrawalService.request(userId, quote.getId(), "k"))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).code().name()).isEqualTo("QUOTE_EXPIRED"));
    }

    @Test
    void requestIdempotentReplay() {
        UUID userId = UUID.randomUUID();
        WithdrawalRequest existing = new WithdrawalRequest(userId, UUID.randomUUID(),
                WithdrawalType.PROFIT, new BigDecimal("5000"), UUID.randomUUID(),
                new BigDecimal("0.0200"), new BigDecimal("100"), new BigDecimal("4900"),
                UUID.randomUUID(), "Test", "9012", "SBIN0123456", null, "k", clock.instant());
        when(withdrawalRepository.findByIdempotencyKey("k")).thenReturn(Optional.of(existing));

        WithdrawalRequest result = withdrawalService.request(userId, UUID.randomUUID(), "k");
        assertThat(result.getId()).isEqualTo(existing.getId());
    }

    @Test
    void secondApproveRequiresDifferentAdmin() {
        UUID admin1 = UUID.randomUUID();
        WithdrawalRequest wr = new WithdrawalRequest(UUID.randomUUID(), UUID.randomUUID(),
                WithdrawalType.PROFIT, new BigDecimal("60000"), UUID.randomUUID(),
                new BigDecimal("0.0200"), new BigDecimal("1200"), new BigDecimal("58800"),
                UUID.randomUUID(), "Test", "9012", "SBIN0123456", null, "k", clock.instant());
        wr.approve(admin1, clock.instant());

        when(withdrawalRepository.findById(wr.getId())).thenReturn(Optional.of(wr));
        when(settingsService.secondApprovalThreshold()).thenReturn(new BigDecimal("50000"));

        assertThatThrownBy(() -> withdrawalService.secondApprove(wr.getId(), admin1))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).code().name())
                        .isEqualTo("MAKER_CHECKER_VIOLATION"));
    }

    @Test
    void markPaidRequiresSecondApprovalForLargeAmounts() {
        UUID admin1 = UUID.randomUUID();
        WithdrawalRequest wr = new WithdrawalRequest(UUID.randomUUID(), UUID.randomUUID(),
                WithdrawalType.PROFIT, new BigDecimal("60000"), UUID.randomUUID(),
                new BigDecimal("0.0200"), new BigDecimal("1200"), new BigDecimal("58800"),
                UUID.randomUUID(), "Test", "9012", "SBIN0123456", null, "k", clock.instant());
        wr.approve(admin1, clock.instant());

        when(withdrawalRepository.findById(wr.getId())).thenReturn(Optional.of(wr));
        when(settingsService.secondApprovalThreshold()).thenReturn(new BigDecimal("50000"));

        assertThatThrownBy(() -> withdrawalService.markPaid(wr.getId(), UUID.randomUUID(),
                "UTR123456789CD", UUID.randomUUID()))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).code().name())
                        .isEqualTo("MAKER_CHECKER_VIOLATION"));
    }

    @Test
    void cancelOnlyFromRequested() {
        UUID userId = UUID.randomUUID();
        WithdrawalRequest wr = new WithdrawalRequest(userId, UUID.randomUUID(),
                WithdrawalType.PROFIT, new BigDecimal("5000"), UUID.randomUUID(),
                new BigDecimal("0.0200"), new BigDecimal("100"), new BigDecimal("4900"),
                UUID.randomUUID(), "Test", "9012", "SBIN0123456", null, "k", clock.instant());
        wr.approve(UUID.randomUUID(), clock.instant());

        when(withdrawalRepository.findById(wr.getId())).thenReturn(Optional.of(wr));

        assertThatThrownBy(() -> withdrawalService.cancel(userId, wr.getId()))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).code().name())
                        .isEqualTo("ILLEGAL_STATE_TRANSITION"));
    }

    @Test
    void principalQuoteRejectsAboveRemainingPrincipal() {
        UUID userId = UUID.randomUUID();
        setupHappyPath(userId);
        when(settingsService.principalWithdrawalEnabled()).thenReturn(true);

        UserBalance balance = new UserBalance(userId, clock.instant());
        balance.apply(Bucket.PRINCIPAL, new BigDecimal("30000"), clock.instant());
        when(userBalanceRepository.findById(userId)).thenReturn(Optional.of(balance));

        assertThatThrownBy(() -> withdrawalService.quote(userId, new BigDecimal("50000"),
                WithdrawalType.PRINCIPAL))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).code().name())
                        .isEqualTo("INSUFFICIENT_WITHDRAWABLE"));
    }

    @Test
    void partialPrincipalWithdrawalDebitsOldestTranchesFirst() {
        UUID userId = UUID.randomUUID();
        Investment older = new Investment(userId, new BigDecimal("30000"), UUID.randomUUID(),
                UUID.randomUUID(), clock.instant());
        older.approve(UUID.randomUUID(), clock.instant());
        older.debitPrincipal(new BigDecimal("10000"), clock.instant());
        Investment newer = new Investment(userId, new BigDecimal("50000"), UUID.randomUUID(),
                UUID.randomUUID(), clock.instant());
        newer.approve(UUID.randomUUID(), clock.instant());

        WithdrawalRequest wr = new WithdrawalRequest(userId, older.getId(), WithdrawalType.PRINCIPAL,
                new BigDecimal("40000"), UUID.randomUUID(), new BigDecimal("0.0500"),
                new BigDecimal("2000"), new BigDecimal("38000"),
                UUID.randomUUID(), "Test", "9012", "SBIN0123456", null, "k", clock.instant());
        wr.approve(UUID.randomUUID(), clock.instant());

        when(withdrawalRepository.findById(wr.getId())).thenReturn(Optional.of(wr));
        when(settingsService.secondApprovalThreshold()).thenReturn(new BigDecimal("50000"));
        when(investmentRepository.findActiveByUserIdOldestFirst(userId))
                .thenReturn(new java.util.ArrayList<>(List.of(older, newer)));

        withdrawalService.markPaid(wr.getId(), UUID.randomUUID(), "UTR123456789CD", null);

        assertThat(older.getRemainingPrincipal()).isEqualByComparingTo("0.00");
        assertThat(older.getStatus()).isEqualTo(InvestmentStatus.CLOSED);
        assertThat(newer.getRemainingPrincipal()).isEqualByComparingTo("30000.00");
        assertThat(newer.getStatus()).isEqualTo(InvestmentStatus.ACTIVE);
    }

    @Test
    void approveFromRequestedSucceeds() {
        WithdrawalRequest wr = new WithdrawalRequest(UUID.randomUUID(), UUID.randomUUID(),
                WithdrawalType.PROFIT, new BigDecimal("5000"), UUID.randomUUID(),
                new BigDecimal("0.0200"), new BigDecimal("100"), new BigDecimal("4900"),
                UUID.randomUUID(), "Test", "9012", "SBIN0123456", null, "k", clock.instant());
        UUID adminId = UUID.randomUUID();
        when(withdrawalRepository.findById(wr.getId())).thenReturn(Optional.of(wr));
        when(withdrawalRepository.save(any(WithdrawalRequest.class))).thenAnswer(i -> i.getArgument(0));

        withdrawalService.approve(wr.getId(), adminId);
        assertThat(wr.getStatus()).isEqualTo(WithdrawalStatus.APPROVED);
        assertThat(wr.getApprovedBy()).isEqualTo(adminId);
    }
}
