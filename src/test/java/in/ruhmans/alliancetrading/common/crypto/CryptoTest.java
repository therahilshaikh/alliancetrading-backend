package in.ruhmans.alliancetrading.common.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CryptoTest {

    private CryptoProperties props;

    @BeforeEach
    void setUp() {
        byte[] key = new byte[32];
        for (int i = 0; i < 32; i++) key[i] = (byte) i;
        props = new CryptoProperties(Base64.getEncoder().encodeToString(key), "k1",
                Base64.getEncoder().encodeToString("pepper".getBytes()));
    }

    @Test
    void fieldEncryptorRoundTrip() {
        FieldEncryptor enc = new FieldEncryptor(props);
        String ct = enc.encrypt("ABCDE1234F");
        assertThat(ct).isNotEqualTo("ABCDE1234F");
        assertThat(enc.decrypt(ct)).isEqualTo("ABCDE1234F");
    }

    @Test
    void fieldEncryptorProducesUniqueIv() {
        FieldEncryptor enc = new FieldEncryptor(props);
        assertThat(enc.encrypt("same")).isNotEqualTo(enc.encrypt("same"));
    }

    @Test
    void fieldHasherIsDeterministicAndNormalized() {
        FieldHasher hasher = new FieldHasher(props);
        assertThat(hasher.hash("abcde1234f")).isEqualTo(hasher.hash(" ABCDE1234F "));
    }

    @Test
    void envelopeRoundTrip() {
        EnvelopeEncryptionService svc = new EnvelopeEncryptionService(props);
        byte[] data = "hello world".getBytes();
        var enc = svc.encrypt(data);
        assertThat(enc.keyId()).isEqualTo("k1");
        assertThat(svc.decrypt(enc.ciphertext(), enc.wrappedDataKey())).isEqualTo(data);
    }
}
