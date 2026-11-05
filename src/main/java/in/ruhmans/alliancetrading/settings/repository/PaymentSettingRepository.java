package in.ruhmans.alliancetrading.settings.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import in.ruhmans.alliancetrading.settings.domain.PaymentSetting;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PaymentSettingRepository extends JpaRepository<PaymentSetting, UUID> {

    @Query("SELECT p FROM PaymentSetting p WHERE p.effectiveFrom <= :now "
            + "ORDER BY p.effectiveFrom DESC, p.createdAt DESC LIMIT 1")
    Optional<PaymentSetting> findCurrent(Instant now);

    @Query("SELECT p FROM PaymentSetting p WHERE p.effectiveFrom > :now ORDER BY p.effectiveFrom ASC")
    List<PaymentSetting> findScheduled(Instant now);

    /** A scheduled default change that points at this account. */
    boolean existsByPaymentAccountIdAndEffectiveFromAfter(UUID paymentAccountId, Instant now);
}
