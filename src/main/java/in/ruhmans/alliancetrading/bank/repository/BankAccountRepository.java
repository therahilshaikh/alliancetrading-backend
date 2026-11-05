package in.ruhmans.alliancetrading.bank.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import in.ruhmans.alliancetrading.bank.domain.BankAccount;
import in.ruhmans.alliancetrading.bank.domain.BankAccountStatus;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface BankAccountRepository extends JpaRepository<BankAccount, UUID> {

    List<BankAccount> findByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<BankAccount> findByUserIdAndStatus(UUID userId, BankAccountStatus status);

    List<BankAccount> findByUserIdInAndStatusIn(Collection<UUID> userIds, Collection<BankAccountStatus> statuses);

    @Query("SELECT b FROM BankAccount b WHERE b.status = :status ORDER BY b.createdAt ASC")
    List<BankAccount> findByStatusOrderByCreatedAt(BankAccountStatus status);

    Page<BankAccount> findByStatus(BankAccountStatus status, Pageable pageable);

    long countByStatus(BankAccountStatus status);

    Optional<BankAccount> findFirstByStatusOrderByCreatedAtAsc(BankAccountStatus status);

    @Query("SELECT b FROM BankAccount b WHERE b.status = :status AND b.createdAt > :cursorCreated ORDER BY b.createdAt ASC")
    List<BankAccount> findByStatusAfterCursor(BankAccountStatus status, Instant cursorCreated);
}
