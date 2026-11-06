package in.ruhmans.alliancetrading.pnl.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import in.ruhmans.alliancetrading.pnl.domain.PnlAllocation;
import in.ruhmans.alliancetrading.pnl.domain.PnlBatchType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PnlAllocationRepository extends JpaRepository<PnlAllocation, UUID> {

    interface InvestmentTotal {
        UUID getInvestmentId();
        BigDecimal getTotal();
    }

    List<PnlAllocation> findByBatchId(UUID batchId);

    @Query("SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END FROM PnlAllocation a "
            + "WHERE a.investmentId = :investmentId AND a.sessionDate = :sessionDate "
            + "AND a.batchType = 'REGULAR'")
    boolean existsRegularForInvestmentOnDate(UUID investmentId, LocalDate sessionDate);

    List<PnlAllocation> findByInvestmentIdAndSessionDateAndBatchType(
            UUID investmentId, LocalDate sessionDate, PnlBatchType batchType);

    @Query("SELECT a.investmentId AS investmentId, SUM(a.amount) AS total FROM PnlAllocation a "
            + "WHERE a.investmentId IN :investmentIds GROUP BY a.investmentId")
    List<InvestmentTotal> sumByInvestmentIds(Collection<UUID> investmentIds);
}
