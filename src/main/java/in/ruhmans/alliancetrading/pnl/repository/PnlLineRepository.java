package in.ruhmans.alliancetrading.pnl.repository;

import java.util.List;
import java.util.UUID;

import in.ruhmans.alliancetrading.pnl.domain.PnlLine;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PnlLineRepository extends JpaRepository<PnlLine, UUID> {

    List<PnlLine> findByBatchId(UUID batchId);
}
