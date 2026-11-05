package in.ruhmans.alliancetrading.audit.repository;

import java.util.List;

import in.ruhmans.alliancetrading.audit.domain.AuditLog;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findByEntityTypeAndEntityIdOrderByIdDesc(String entityType, String entityId);
}
