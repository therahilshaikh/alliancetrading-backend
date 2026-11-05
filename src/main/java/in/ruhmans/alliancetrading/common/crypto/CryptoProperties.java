package in.ruhmans.alliancetrading.common.crypto;

import java.util.Base64;

import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CryptoProperties {

    private final SecretKeySpec masterKey;
    private final String keyId;
    private final byte[] hashPepper;

    public CryptoProperties(
            @Value("${app.crypto.master-key}") String masterKeyBase64,
            @Value("${app.crypto.key-id}") String keyId,
            @Value("${app.crypto.hash-pepper}") String hashPepperBase64) {
        byte[] keyBytes = Base64.getDecoder().decode(masterKeyBase64);
        if (keyBytes.length != 32) {
            throw new IllegalStateException("app.crypto.master-key must be a base64-encoded 32-byte key");
        }
        this.masterKey = new SecretKeySpec(keyBytes, "AES");
        this.keyId = keyId;
        this.hashPepper = Base64.getDecoder().decode(hashPepperBase64);
    }

    public SecretKeySpec masterKey() {
        return masterKey;
    }

    public String keyId() {
        return keyId;
    }

    public byte[] hashPepper() {
        return hashPepper;
    }
}
