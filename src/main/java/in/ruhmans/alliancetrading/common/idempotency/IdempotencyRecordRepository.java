package in.ruhmans.alliancetrading.common.idempotency;

import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, IdempotencyRecordId> {
}
