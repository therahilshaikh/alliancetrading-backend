package in.ruhmans.alliancetrading.audit.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import in.ruhmans.alliancetrading.audit.domain.AuditLog;
import in.ruhmans.alliancetrading.audit.repository.AuditLogRepository;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/audit-log")
public class AdminAuditController {

    private final AuditLogRepository auditLogRepository;

    public AdminAuditController(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public record AuditEntry(Long id, String actorType, UUID actorId, String action,
                             String entityType, String entityId, String beforeData,
                             String afterData, String ip, String requestId, Instant createdAt) {}

    @GetMapping
    public ResponseEntity<List<AuditEntry>> list(
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String entityId,
            @RequestParam(defaultValue = "50") int limit) {
        List<AuditLog> entries;
        if (entityType != null && entityId != null) {
            entries = auditLogRepository.findByEntityTypeAndEntityIdOrderByIdDesc(entityType, entityId);
        } else {
            entries = auditLogRepository.findAll().stream()
                    .sorted(java.util.Comparator.comparing(AuditLog::getId).reversed())
                    .limit(Math.min(limit, 200))
                    .toList();
        }
        return ResponseEntity.ok(entries.stream().map(this::toEntry).toList());
    }

    private AuditEntry toEntry(AuditLog a) {
        return new AuditEntry(a.getId(), a.getActorType(), a.getActorId(), a.getAction(),
                a.getEntityType(), a.getEntityId(), a.getBeforeData(), a.getAfterData(),
                a.getIp(), a.getRequestId(), a.getCreatedAt());
    }
}
