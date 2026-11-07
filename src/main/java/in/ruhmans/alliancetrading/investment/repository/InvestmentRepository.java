package in.ruhmans.alliancetrading.investment.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import in.ruhmans.alliancetrading.investment.domain.Investment;
import in.ruhmans.alliancetrading.investment.domain.InvestmentStatus;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface InvestmentRepository extends JpaRepository<Investment, UUID> {

    List<Investment> findByUserIdOrderByCreatedAtDesc(UUID userId);

    @Query("SELECT i FROM Investment i WHERE i.status = :status ORDER BY i.createdAt ASC")
    List<Investment> findByStatusOrderByCreatedAt(InvestmentStatus status);

    Page<Investment> findByStatus(InvestmentStatus status, Pageable pageable);

    long countByStatus(InvestmentStatus status);

    Optional<Investment> findFirstByStatusOrderByCreatedAtAsc(InvestmentStatus status);

    @Query("SELECT i FROM Investment i WHERE i.userId = :userId "
            + "AND i.status IN ('PENDING_VERIFICATION','ACTIVE') ORDER BY i.createdAt ASC")
    List<Investment> findLiveByUserId(UUID userId);

    @Query("SELECT i FROM Investment i WHERE i.userId = :userId AND i.status = 'ACTIVE' "
            + "ORDER BY i.createdAt ASC")
    List<Investment> findActiveByUserIdOldestFirst(UUID userId);

    boolean existsByUserIdAndStatus(UUID userId, InvestmentStatus status);

    @Query("SELECT i FROM Investment i WHERE i.id IN :ids")
    List<Investment> findAllByIds(List<UUID> ids);
}
