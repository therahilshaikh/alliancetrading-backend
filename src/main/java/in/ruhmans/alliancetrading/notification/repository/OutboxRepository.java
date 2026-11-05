package in.ruhmans.alliancetrading.notification.repository;

import java.time.Instant;
import java.util.List;

import in.ruhmans.alliancetrading.notification.domain.OutboxMessage;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OutboxRepository extends JpaRepository<OutboxMessage, Long> {

    @Query("SELECT o FROM OutboxMessage o WHERE o.status = 'PENDING' AND o.nextAttemptAt <= :now "
            + "ORDER BY o.id ASC LIMIT :limit")
    List<OutboxMessage> findPendingBatch(Instant now, int limit);
}
