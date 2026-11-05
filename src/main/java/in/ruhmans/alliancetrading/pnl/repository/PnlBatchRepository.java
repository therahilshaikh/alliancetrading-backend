package in.ruhmans.alliancetrading.pnl.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import in.ruhmans.alliancetrading.pnl.domain.PnlBatch;
import in.ruhmans.alliancetrading.pnl.domain.PnlBatchStatus;
import in.ruhmans.alliancetrading.pnl.domain.PnlValueType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PnlBatchRepository extends JpaRepository<PnlBatch, UUID> {

    @Query("SELECT b FROM PnlBatch b WHERE b.status = :status ORDER BY b.createdAt ASC")
    List<PnlBatch> findByStatusOrderByCreatedAt(PnlBatchStatus status);

    List<PnlBatch> findAllByOrderByCreatedAtDesc();

    Page<PnlBatch> findByStatus(PnlBatchStatus status, Pageable pageable);

    Page<PnlBatch> findByValueType(PnlValueType valueType, Pageable pageable);

    Page<PnlBatch> findByStatusAndValueType(PnlBatchStatus status, PnlValueType valueType, Pageable pageable);

    Page<PnlBatch> findByAssignedCheckerIdAndStatus(UUID assignedCheckerId, PnlBatchStatus status, Pageable pageable);

    long countByStatus(PnlBatchStatus status);

    Optional<PnlBatch> findFirstByStatusOrderByCreatedAtAsc(PnlBatchStatus status);

    List<PnlBatch> findByAssignedCheckerIdAndStatusOrderByCreatedAtAsc(UUID assignedCheckerId,
                                                                        PnlBatchStatus status);

    long countByAssignedCheckerIdAndStatus(UUID assignedCheckerId, PnlBatchStatus status);
}
