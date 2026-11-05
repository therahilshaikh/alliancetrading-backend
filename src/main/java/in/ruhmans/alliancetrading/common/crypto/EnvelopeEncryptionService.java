package in.ruhmans.alliancetrading.common.crypto;

import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

/**
 * Envelope encryption for stored files: each file gets a random 256-bit
 * data key (AES-256-GCM). The data key is wrapped (AES-GCM) with the
 * master key and stored alongside the document as base64(iv || wrappedKey).
 */
@Component
public class EnvelopeEncryptionService {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int DATA_KEY_BYTES = 32;

    private final SecureRandom random = new SecureRandom();
    private final CryptoProperties props;

    public EnvelopeEncryptionService(CryptoProperties props) {
        this.props = props;
    }

    public record EncryptedPayload(byte[] ciphertext, String wrappedDataKey, String keyId) {}

    public EncryptedPayload encrypt(byte[] plaintext) {
        try {
            byte[] dataKey = new byte[DATA_KEY_BYTES];
            random.nextBytes(dataKey);
            SecretKeySpec dataKeySpec = new SecretKeySpec(dataKey, "AES");

            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, dataKeySpec, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plaintext);
            byte[] out = new byte[IV_BYTES + ct.length];
            System.arraycopy(iv, 0, out, 0, IV_BYTES);
            System.arraycopy(ct, 0, out, IV_BYTES, ct.length);

            return new EncryptedPayload(out, wrap(dataKey), props.keyId());
        } catch (Exception e) {
            throw new IllegalStateException("File encryption failed", e);
        }
    }

    public byte[] decrypt(byte[] ciphertext, String wrappedDataKey) {
        try {
            byte[] dataKey = unwrap(wrappedDataKey);
            byte[] iv = new byte[IV_BYTES];
            System.arraycopy(ciphertext, 0, iv, 0, IV_BYTES);
            byte[] ct = new byte[ciphertext.length - IV_BYTES];
            System.arraycopy(ciphertext, IV_BYTES, ct, 0, ct.length);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(dataKey, "AES"), new GCMParameterSpec(TAG_BITS, iv));
            return cipher.doFinal(ct);
        } catch (Exception e) {
            throw new IllegalStateException("File decryption failed", e);
        }
    }

    private String wrap(byte[] dataKey) throws Exception {
        byte[] iv = new byte[IV_BYTES];
        random.nextBytes(iv);
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, props.masterKey(), new GCMParameterSpec(TAG_BITS, iv));
        byte[] wrapped = cipher.doFinal(dataKey);
        byte[] out = new byte[IV_BYTES + wrapped.length];
        System.arraycopy(iv, 0, out, 0, IV_BYTES);
        System.arraycopy(wrapped, 0, out, IV_BYTES, wrapped.length);
        return Base64.getEncoder().encodeToString(out);
    }

    private byte[] unwrap(String wrappedDataKey) throws Exception {
        byte[] in = Base64.getDecoder().decode(wrappedDataKey);
        byte[] iv = new byte[IV_BYTES];
        System.arraycopy(in, 0, iv, 0, IV_BYTES);
        byte[] wrapped = new byte[in.length - IV_BYTES];
        System.arraycopy(in, IV_BYTES, wrapped, 0, wrapped.length);
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, props.masterKey(), new GCMParameterSpec(TAG_BITS, iv));
        return cipher.doFinal(wrapped);
    }
}
