package in.ruhmans.alliancetrading.common.crypto;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

/**
 * HMAC-SHA256 with a server-side pepper. Used for deterministic
 * uniqueness lookups over encrypted PII (e.g. PAN) and OTP storage.
 */
@Component
public class FieldHasher {

    private final CryptoProperties props;

    public FieldHasher(CryptoProperties props) {
        this.props = props;
    }

    public String hash(String value) {
        if (value == null) {
            return null;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(props.hashPepper(), "HmacSHA256"));
            byte[] out = mac.doFinal(normalize(value).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("Hashing failed", e);
        }
    }

    private String normalize(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }
}
