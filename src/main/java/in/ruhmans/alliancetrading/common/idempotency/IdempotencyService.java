package in.ruhmans.alliancetrading.common.idempotency;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import in.ruhmans.alliancetrading.common.error.ApiException;
import in.ruhmans.alliancetrading.common.error.ErrorCode;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Idempotency-Key handling for money-moving POSTs.
 *
 * Usage:
 *   IdemResult<T> result = idempotency.execute(scope, key, actorId, requestFingerprint, () -> doWork());
 *
 * - First call: inserts a (scope, key) row in the SAME transaction as the work,
 *   so a rollback also removes the claim; on success the response is stored.
 * - Replay with same fingerprint: returns the stored response.
 * - Replay with a different fingerprint: 409 IDEMPOTENCY_CONFLICT.
 */
@Service
public class IdempotencyService {

    private final IdempotencyRecordRepository repository;
    private final Clock clock;

    public IdempotencyService(IdempotencyRecordRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public record StoredResponse(int status, String body) {}

    /**
     * @param requestFingerprint a stable hash of the request payload (use {@link #fingerprint(String)})
     */
    public Optional<StoredResponse> begin(String scope, String key, UUID actorId, String requestFingerprint) {
        IdempotencyRecord fresh = new IdempotencyRecord(scope, key, actorId, requestFingerprint, clock.instant());
        try {
            repository.saveAndFlush(fresh);
            return Optional.empty();
        } catch (DataIntegrityViolationException e) {
            IdempotencyRecord existing = repository.findById(new IdempotencyRecordId(scope, key))
                    .orElseThrow(() -> new ApiException(ErrorCode.INTERNAL_ERROR, "Idempotency race"));
            if (!existing.getRequestHash().equals(requestFingerprint)) {
                throw new ApiException(ErrorCode.IDEMPOTENCY_CONFLICT,
                        "Idempotency-Key was already used with a different request");
            }
            if (existing.getResponseStatus() == null) {
                // Original request still in flight or died after claiming; treat as conflict to be safe.
                throw new ApiException(ErrorCode.IDEMPOTENCY_CONFLICT,
                        "A request with this Idempotency-Key is already in progress");
            }
            return Optional.of(new StoredResponse(existing.getResponseStatus(), existing.getResponseBody()));
        }
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void complete(String scope, String key, int status, String body) {
        IdempotencyRecord record = repository.findById(new IdempotencyRecordId(scope, key))
                .orElseThrow(() -> new ApiException(ErrorCode.INTERNAL_ERROR, "Idempotency record missing"));
        record.complete(status, body);
        repository.save(record);
    }

    public static String fingerprint(String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static String requireKey(String headerValue) {
        if (headerValue == null || headerValue.isBlank() || headerValue.length() > 100) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "A valid Idempotency-Key header is required for this operation");
        }
        return headerValue.trim();
    }
}
