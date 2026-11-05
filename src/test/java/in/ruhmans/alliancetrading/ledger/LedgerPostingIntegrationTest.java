package in.ruhmans.alliancetrading.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import in.ruhmans.alliancetrading.BaseIntegrationTest;
import in.ruhmans.alliancetrading.common.error.ApiException;
import in.ruhmans.alliancetrading.ledger.domain.Bucket;
import in.ruhmans.alliancetrading.ledger.domain.EntryType;
import in.ruhmans.alliancetrading.ledger.domain.LedgerEntry;
import in.ruhmans.alliancetrading.ledger.domain.UserBalance;
import in.ruhmans.alliancetrading.ledger.repository.LedgerEntryRepository;
import in.ruhmans.alliancetrading.ledger.repository.UserBalanceRepository;
import in.ruhmans.alliancetrading.ledger.service.LedgerPostingService;
import in.ruhmans.alliancetrading.user.domain.User;
import in.ruhmans.alliancetrading.user.repository.UserRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class LedgerPostingIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private LedgerPostingService postingService;

    @Autowired
    private UserBalanceRepository balanceRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private User createUser() {
        return userRepository.save(new User("91" + String.format("%08d", new java.util.Random().nextInt(100_000_000)),
                java.time.Instant.now()));
    }

    private LedgerPostingService.Journal journal(UUID userId, String key, BigDecimal amount) {
        return new LedgerPostingService.Journal(userId, null,
                List.of(new LedgerPostingService.Posting(Bucket.PRINCIPAL, EntryType.DEPOSIT, amount)),
                "test", "t1", key, "SYSTEM", null);
    }

    @Test
    void postsDepositAndUpdatesCache() {
        User user = createUser();
        postingService.post(journal(user.getId(), "dep1", new BigDecimal("50000")));

        UserBalance b = balanceRepository.findById(user.getId()).orElseThrow();
        assertThat(b.getPrincipal()).isEqualByComparingTo("50000.00");
        assertThat(ledgerEntryRepository.sumByUserAndBucket(user.getId(), Bucket.PRINCIPAL))
                .isEqualByComparingTo("50000");
    }

    @Test
    void replayReturnsSameJournalId() {
        User user = createUser();
        UUID j1 = postingService.post(journal(user.getId(), "same-key", new BigDecimal("100")));
        UUID j2 = postingService.post(journal(user.getId(), "same-key", new BigDecimal("100")));
        assertThat(j2).isEqualTo(j1);

        UserBalance b = balanceRepository.findById(user.getId()).orElseThrow();
        assertThat(b.getPrincipal()).isEqualByComparingTo("100.00");
    }

    @Test
    void negativePrincipalRejected() {
        User user = createUser();
        assertThatThrownBy(() -> postingService.post(new LedgerPostingService.Journal(
                user.getId(), null,
                List.of(new LedgerPostingService.Posting(Bucket.PRINCIPAL, EntryType.DEPOSIT,
                        new BigDecimal("-10"))),
                "test", "t", "neg1", "SYSTEM", null)))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).code().name())
                        .isEqualTo("ILLEGAL_STATE_TRANSITION"));
    }

    @Test
    void ledgerAppendOnlyTriggerBlocksUpdateAndDelete() {
        User user = createUser();
        postingService.post(journal(user.getId(), "app-only", new BigDecimal("50")));
        LedgerEntry entry = ledgerEntryRepository.findByIdempotencyKey("app-only:PRINCIPAL:DEPOSIT").orElseThrow();

        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE ledger_entries SET amount = 999 WHERE id = ?", entry.getId()))
                .hasRootCauseInstanceOf(org.postgresql.util.PSQLException.class)
                .rootCause().hasMessageContaining("append-only");

        assertThatThrownBy(() -> jdbcTemplate.update(
                "DELETE FROM ledger_entries WHERE id = ?", entry.getId()))
                .hasRootCauseInstanceOf(org.postgresql.util.PSQLException.class)
                .rootCause().hasMessageContaining("append-only");
    }

    @Test
    void concurrentPostsMaintainCorrectBalance() throws InterruptedException {
        User user = createUser();
        int threads = 8;
        int postsPerThread = 5;
        BigDecimal amount = new BigDecimal("100");

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads * postsPerThread);
        AtomicInteger failures = new AtomicInteger();

        for (int t = 0; t < threads; t++) {
            final int threadNum = t;
            pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < postsPerThread; i++) {
                        postingService.post(journal(user.getId(),
                                "cc-" + threadNum + "-" + i, amount));
                        done.countDown();
                    }
                } catch (Exception e) {
                    failures.incrementAndGet();
                    done.countDown();
                }
            });
        }

        start.countDown();
        done.await(30, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(failures.get()).isZero();
        BigDecimal expected = amount.multiply(BigDecimal.valueOf((long) threads * postsPerThread));
        UserBalance b = balanceRepository.findById(user.getId()).orElseThrow();
        assertThat(b.getPrincipal()).isEqualByComparingTo(expected);
        assertThat(ledgerEntryRepository.sumByUserAndBucket(user.getId(), Bucket.PRINCIPAL))
                .isEqualByComparingTo(expected);
    }

    @Test
    void concurrentReplayOnlyAppliesOnce() throws InterruptedException {
        User user = createUser();
        String key = "replay-key";
        int threads = 4;

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        java.util.List<UUID> journalIds = java.util.Collections.synchronizedList(new java.util.ArrayList<>());

        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                try {
                    start.await();
                    journalIds.add(postingService.post(journal(user.getId(), key, new BigDecimal("500"))));
                } catch (Exception ignored) {
                    // DataIntegrityViolationException possible on race; journal id still returned
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        done.await(30, TimeUnit.SECONDS);
        pool.shutdown();

        UserBalance b = balanceRepository.findById(user.getId()).orElseThrow();
        assertThat(b.getPrincipal()).isEqualByComparingTo("500.00");
    }
}
