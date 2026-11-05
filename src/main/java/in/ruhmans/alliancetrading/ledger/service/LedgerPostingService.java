package in.ruhmans.alliancetrading.ledger.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import in.ruhmans.alliancetrading.common.error.ApiException;
import in.ruhmans.alliancetrading.common.error.ErrorCode;
import in.ruhmans.alliancetrading.common.money.MoneyUtils;
import in.ruhmans.alliancetrading.ledger.domain.Bucket;
import in.ruhmans.alliancetrading.ledger.domain.EntryType;
import in.ruhmans.alliancetrading.ledger.domain.LedgerEntry;
import in.ruhmans.alliancetrading.ledger.domain.UserBalance;
import in.ruhmans.alliancetrading.ledger.repository.LedgerEntryRepository;
import in.ruhmans.alliancetrading.ledger.repository.UserBalanceRepository;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The ONLY writer of ledger rows and the balances cache.
 * Every money movement goes through {@link #post(Journal)}.
 */
@Service
public class LedgerPostingService {

    private final LedgerEntryRepository ledgerEntryRepository;
    private final UserBalanceRepository userBalanceRepository;
    private final Clock clock;

    public LedgerPostingService(LedgerEntryRepository ledgerEntryRepository,
                                UserBalanceRepository userBalanceRepository, Clock clock) {
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.userBalanceRepository = userBalanceRepository;
        this.clock = clock;
    }

    public record Posting(Bucket bucket, EntryType entryType, BigDecimal amount) {
        public Posting {
            amount = MoneyUtils.money(amount);
        }
    }

    public record Journal(
            UUID userId,
            UUID investmentId,
            List<Posting> postings,
            String sourceType,
            String sourceId,
            String idempotencyKey,
            String createdByType,
            UUID createdById) {}

    /**
     * Atomically writes all postings and updates the balance cache.
     * Replays (same idempotency key) are a no-op that returns the existing journal id.
     */
    @Transactional
    public UUID post(Journal journal) {
        if (journal.postings() == null || journal.postings().isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Journal must contain at least one posting");
        }

        // Idempotent replay: check the first posting's derived key
        Posting first = journal.postings().get(0);
        String firstKey = derivedKey(journal.idempotencyKey(), first);
        var existing = ledgerEntryRepository.findByIdempotencyKey(firstKey);
        if (existing.isPresent()) {
            return existing.get().getJournalId();
        }

        UUID journalId = UUID.randomUUID();
        Instant now = clock.instant();

        // Lock the balance row (create it first if absent)
        userBalanceRepository.insertIfAbsent(journal.userId());
        UserBalance balance = userBalanceRepository.findByUserIdForUpdate(journal.userId())
                .orElseThrow(() -> new ApiException(ErrorCode.INTERNAL_ERROR, "Balance row missing after insert"));

        // Apply postings to the cached balance, validating invariants
        for (Posting p : journal.postings()) {
            balance.apply(p.bucket(), p.amount(), now);
        }
        if (balance.getPrincipal().signum() < 0) {
            throw new ApiException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Posting would make principal negative");
        }
        if (balance.getHeld().signum() < 0) {
            throw new ApiException(ErrorCode.ILLEGAL_STATE_TRANSITION,
                    "Posting would make held negative");
        }

        // Write ledger rows
        for (Posting p : journal.postings()) {
            LedgerEntry entry = new LedgerEntry(journalId, journal.userId(), journal.investmentId(),
                    p.bucket(), p.entryType(), p.amount(), journal.sourceType(), journal.sourceId(),
                    derivedKey(journal.idempotencyKey(), p),
                    journal.createdByType(), journal.createdById(), now);
            try {
                ledgerEntryRepository.saveAndFlush(entry);
            } catch (DataIntegrityViolationException e) {
                // Concurrent replay with the same derived key — treat as idempotent success
                var prior = ledgerEntryRepository.findByIdempotencyKey(entry.getIdempotencyKey());
                if (prior.isPresent()) {
                    return prior.get().getJournalId();
                }
                throw e;
            }
        }

        userBalanceRepository.save(balance);
        return journalId;
    }

    private static String derivedKey(String base, Posting p) {
        String key = base + ":" + p.bucket() + ":" + p.entryType();
        if (key.length() > 100) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Idempotency key too long");
        }
        return key;
    }
}
