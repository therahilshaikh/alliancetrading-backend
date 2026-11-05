package in.ruhmans.alliancetrading.withdrawal.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import in.ruhmans.alliancetrading.withdrawal.domain.WithdrawalRequest;
import in.ruhmans.alliancetrading.withdrawal.domain.WithdrawalStatus;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface WithdrawalRequestRepository extends JpaRepository<WithdrawalRequest, UUID> {

    Optional<WithdrawalRequest> findByIdempotencyKey(String idempotencyKey);

    List<WithdrawalRequest> findByUserIdOrderByCreatedAtDesc(UUID userId);

    @Query("SELECT w FROM WithdrawalRequest w WHERE w.status = :status ORDER BY w.createdAt ASC")
    List<WithdrawalRequest> findByStatusOrderByCreatedAt(WithdrawalStatus status);

    Page<WithdrawalRequest> findByStatus(WithdrawalStatus status, Pageable pageable);

    long countByStatusIn(Collection<WithdrawalStatus> statuses);

    Optional<WithdrawalRequest> findFirstByStatusInOrderByCreatedAtAsc(Collection<WithdrawalStatus> statuses);

    @Query("SELECT CASE WHEN COUNT(w) > 0 THEN true ELSE false END FROM WithdrawalRequest w "
            + "WHERE w.userId = :userId AND w.status IN ('REQUESTED','APPROVED','PAYOUT_FAILED')")
    boolean hasOpenWithdrawal(UUID userId);
}
