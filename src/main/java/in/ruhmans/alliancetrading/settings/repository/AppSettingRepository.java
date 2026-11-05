package in.ruhmans.alliancetrading.settings.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import in.ruhmans.alliancetrading.settings.domain.AppSetting;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AppSettingRepository extends JpaRepository<AppSetting, UUID> {

    @Query("SELECT a FROM AppSetting a WHERE a.key = :key AND a.effectiveFrom <= :now "
            + "ORDER BY a.effectiveFrom DESC, a.createdAt DESC LIMIT 1")
    Optional<AppSetting> findCurrent(String key, Instant now);

    @Query("SELECT a FROM AppSetting a WHERE a.effectiveFrom > :now ORDER BY a.effectiveFrom ASC")
    List<AppSetting> findScheduled(Instant now);
}
