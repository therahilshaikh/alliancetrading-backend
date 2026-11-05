package in.ruhmans.alliancetrading.common.crypto;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

/**
 * AES-256-GCM field-level encryption for PII columns.
 * Output format: base64( 12-byte IV || ciphertext+tag ).
 */
@Component
public class FieldEncryptor {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecureRandom random = new SecureRandom();
    private final CryptoProperties props;

    public FieldEncryptor(CryptoProperties props) {
        this.props = props;
    }

    public String encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, props.masterKey(), new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[IV_BYTES + ct.length];
            System.arraycopy(iv, 0, out, 0, IV_BYTES);
            System.arraycopy(ct, 0, out, IV_BYTES, ct.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    public String decrypt(String encoded) {
        if (encoded == null) {
            return null;
        }
        try {
            byte[] in = Base64.getDecoder().decode(encoded);
            byte[] iv = new byte[IV_BYTES];
            System.arraycopy(in, 0, iv, 0, IV_BYTES);
            byte[] ct = new byte[in.length - IV_BYTES];
            System.arraycopy(in, IV_BYTES, ct, 0, ct.length);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, props.masterKey(), new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(ct), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Decryption failed", e);
        }
    }

    public String currentKeyId() {
        return props.keyId();
    }

    public SecretKeySpec masterKey() {
        return props.masterKey();
    }
}
