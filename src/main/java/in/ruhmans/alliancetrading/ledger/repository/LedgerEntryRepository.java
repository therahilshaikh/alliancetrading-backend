package in.ruhmans.alliancetrading.ledger.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import in.ruhmans.alliancetrading.ledger.domain.Bucket;
import in.ruhmans.alliancetrading.ledger.domain.LedgerEntry;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    Optional<LedgerEntry> findByIdempotencyKey(String idempotencyKey);

    @Query("SELECT e FROM LedgerEntry e WHERE e.userId = :userId AND e.id < :cursorId ORDER BY e.id DESC LIMIT :limit")
    List<LedgerEntry> findByUserIdBeforeCursor(UUID userId, Long cursorId, int limit);

    @Query("SELECT e FROM LedgerEntry e WHERE e.userId = :userId ORDER BY e.id DESC LIMIT :limit")
    List<LedgerEntry> findByUserIdLatest(UUID userId, int limit);

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM LedgerEntry e WHERE e.userId = :userId AND e.bucket = :bucket")
    BigDecimal sumByUserAndBucket(UUID userId, Bucket bucket);
}
