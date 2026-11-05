package in.ruhmans.alliancetrading.withdrawal;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import in.ruhmans.alliancetrading.BaseIntegrationTest;
import in.ruhmans.alliancetrading.bank.domain.BankAccount;
import in.ruhmans.alliancetrading.bank.domain.BankAccountType;
import in.ruhmans.alliancetrading.bank.repository.BankAccountRepository;
import in.ruhmans.alliancetrading.investment.domain.Investment;
import in.ruhmans.alliancetrading.investment.repository.InvestmentRepository;
import in.ruhmans.alliancetrading.ledger.domain.Bucket;
import in.ruhmans.alliancetrading.ledger.domain.EntryType;
import in.ruhmans.alliancetrading.ledger.service.LedgerPostingService;
import in.ruhmans.alliancetrading.settings.domain.FeeSetting;
import in.ruhmans.alliancetrading.settings.domain.WithdrawalType;
import in.ruhmans.alliancetrading.settings.repository.FeeSettingRepository;
import in.ruhmans.alliancetrading.user.domain.User;
import in.ruhmans.alliancetrading.user.domain.UserStatus;
import in.ruhmans.alliancetrading.user.repository.UserRepository;
import in.ruhmans.alliancetrading.withdrawal.domain.WithdrawalQuote;
import in.ruhmans.alliancetrading.withdrawal.repository.WithdrawalQuoteRepository;
import in.ruhmans.alliancetrading.withdrawal.repository.WithdrawalRequestRepository;
import in.ruhmans.alliancetrading.withdrawal.service.WithdrawalService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class WithdrawalConcurrencyIntegrationTest extends BaseIntegrationTest {

    @Autowired WithdrawalService withdrawalService;
    @Autowired WithdrawalRequestRepository withdrawalRepository;
    @Autowired WithdrawalQuoteRepository quoteRepository;
    @Autowired UserRepository userRepository;
    @Autowired BankAccountRepository bankAccountRepository;
    @Autowired InvestmentRepository investmentRepository;
    @Autowired FeeSettingRepository feeSettingRepository;
    @Autowired LedgerPostingService ledgerPostingService;
    @Autowired Clock clock;

    @Test
    void twoConcurrentWithdrawalsForSameUserExactlyOneSucceeds() throws InterruptedException {
        Instant now = Instant.now();

        // Setup: approved user, verified bank, active investment, profit in ledger
        User user = userRepository.save(new User("91" + System.nanoTime() % 100_000_000, now));
        user.setStatus(UserStatus.APPROVED);
        userRepository.save(user);

        BankAccount bank = new BankAccount(user.getId(), "Test", "enc", "hash" + user.getId(),
                "9012", "SBIN0123456", BankAccountType.SAVINGS, null, null, now, now);
        bank.markVerified(UUID.randomUUID(), now);
        bankAccountRepository.save(bank);

        Investment inv = new Investment(user.getId(), new BigDecimal("50000"), UUID.randomUUID(),
                UUID.randomUUID(), now);
        inv.approve(UUID.randomUUID(), now);
        investmentRepository.save(inv);

        // Fee setting
        FeeSetting fee = new FeeSetting(WithdrawalType.PROFIT, new BigDecimal("0.0200"),
                now.minusSeconds(60), UUID.randomUUID(), now);
        feeSettingRepository.save(fee);

        // Post profit to ledger
        ledgerPostingService.post(new LedgerPostingService.Journal(user.getId(), inv.getId(),
                java.util.List.of(new LedgerPostingService.Posting(Bucket.PROFIT, EntryType.PNL,
                        new BigDecimal("10000"))),
                "test", "setup", "setup-profit", "SYSTEM", null));

        // Create a quote
        var quoteResult = withdrawalService.quote(user.getId(), new BigDecimal("5000"),
                WithdrawalType.PROFIT);
        UUID quoteId = quoteResult.quote().getId();

        // Two threads race to submit the same quote
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();

        for (int i = 0; i < 2; i++) {
            final int n = i;
            pool.submit(() -> {
                try {
                    start.await();
                    withdrawalService.request(user.getId(), quoteId, "concurrent-" + n);
                    successes.incrementAndGet();
                } catch (Exception e) {
                    failures.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        done.await(30, TimeUnit.SECONDS);
        pool.shutdown();

        // Exactly one should succeed
        assertThat(successes.get() + failures.get()).isEqualTo(2);
        assertThat(successes.get()).isEqualTo(1);
        assertThat(withdrawalRepository.hasOpenWithdrawal(user.getId())).isTrue();
    }
}
