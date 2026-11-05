package in.ruhmans.alliancetrading.ledger.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import in.ruhmans.alliancetrading.ledger.domain.UserBalance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

public interface UserBalanceRepository extends JpaRepository<UserBalance, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM UserBalance b WHERE b.userId = :userId")
    Optional<UserBalance> findByUserIdForUpdate(UUID userId);

    @Modifying
    @Query(value = "INSERT INTO user_balances (user_id, principal, profit, held, version, updated_at) "
            + "VALUES (:userId, 0, 0, 0, 0, now()) ON CONFLICT (user_id) DO NOTHING",
            nativeQuery = true)
    int insertIfAbsent(UUID userId);

    /** Platform totals as {principal, profit, held}; zeros when there are no balances yet. */
    @Query("SELECT COALESCE(SUM(b.principal), 0), COALESCE(SUM(b.profit), 0), COALESCE(SUM(b.held), 0) "
            + "FROM UserBalance b")
    List<Object[]> sumTotals();
}
