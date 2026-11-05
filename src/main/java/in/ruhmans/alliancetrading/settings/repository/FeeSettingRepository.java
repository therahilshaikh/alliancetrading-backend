package in.ruhmans.alliancetrading.settings.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import in.ruhmans.alliancetrading.settings.domain.FeeSetting;
import in.ruhmans.alliancetrading.settings.domain.WithdrawalType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface FeeSettingRepository extends JpaRepository<FeeSetting, UUID> {

    @Query("SELECT f FROM FeeSetting f WHERE f.withdrawalType = :type AND f.effectiveFrom <= :now "
            + "ORDER BY f.effectiveFrom DESC, f.createdAt DESC LIMIT 1")
    Optional<FeeSetting> findCurrent(WithdrawalType type, Instant now);

    @Query("SELECT f FROM FeeSetting f WHERE f.effectiveFrom > :now ORDER BY f.effectiveFrom ASC")
    List<FeeSetting> findScheduled(Instant now);
}
