package in.ruhmans.alliancetrading.audit.service;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import in.ruhmans.alliancetrading.audit.domain.AuditLog;
import in.ruhmans.alliancetrading.audit.repository.AuditLogRepository;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class AuditService {

    private static final ObjectMapper JSON = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final AuditLogRepository repository;
    private final Clock clock;

    public AuditService(AuditLogRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void record(String actorType, UUID actorId, String action,
                       String entityType, String entityId,
                       String beforeData, String afterData) {
        String ip = null;
        String userAgent = null;
        var attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes sra) {
            HttpServletRequest req = sra.getRequest();
            ip = extractIp(req);
            userAgent = truncate(req.getHeader("User-Agent"), 500);
        }
        String requestId = MDC.get("requestId");

        repository.save(new AuditLog(actorType, actorId, action, entityType, entityId,
                beforeData, afterData, ip, userAgent, requestId, clock.instant()));
    }

    public void recordAdmin(UUID adminId, String action, String entityType,
                            String entityId, String beforeData, String afterData) {
        record("ADMIN", adminId, action, entityType, entityId, beforeData, afterData);
    }

    public void recordUser(UUID userId, String action, String entityType,
                           String entityId, String beforeData, String afterData) {
        record("USER", userId, action, entityType, entityId, beforeData, afterData);
    }

    public void recordSystem(String action, String entityType, String entityId,
                             String beforeData, String afterData) {
        record("SYSTEM", null, action, entityType, entityId, beforeData, afterData);
    }

    /**
     * Serialises audit detail safely (free-text values such as rejection reasons may
     * contain quotes). Pairs are key, value, key, value...; null values are kept.
     */
    public static String json(Object... keyValues) {
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("json() expects key/value pairs");
        }
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        try {
            return JSON.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise audit data", e);
        }
    }

    private String extractIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
