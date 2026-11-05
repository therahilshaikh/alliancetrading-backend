package in.ruhmans.alliancetrading.common.pagination;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import in.ruhmans.alliancetrading.common.error.ApiException;
import in.ruhmans.alliancetrading.common.error.ErrorCode;

/**
 * Encodes/decodes opaque cursors of the form base64url("timestamp|uuid")
 * for keyset pagination over (created_at, id) descending lists.
 */
public final class CursorCodec {

    private CursorCodec() {}

    public record KeysetCursor(Instant createdAt, UUID id) {}

    public static String encode(Instant createdAt, UUID id) {
        String raw = createdAt.toString() + "|" + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static KeysetCursor decode(String cursor) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            int sep = raw.indexOf('|');
            return new KeysetCursor(Instant.parse(raw.substring(0, sep)), UUID.fromString(raw.substring(sep + 1)));
        } catch (Exception e) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Invalid cursor");
        }
    }
}
