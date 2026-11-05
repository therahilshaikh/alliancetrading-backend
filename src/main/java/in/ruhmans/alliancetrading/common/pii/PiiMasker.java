package in.ruhmans.alliancetrading.common.pii;

/**
 * Display masks for identity and bank numbers. Inputs are the stored last-4 suffixes,
 * so masking never requires decrypting the full value.
 */
public final class PiiMasker {

    private PiiMasker() {}

    /** PAN is 10 characters: {@code XXXXXX234F}. */
    public static String pan(String last4) {
        return last4 == null ? null : "XXXXXX" + last4;
    }

    /** Aadhaar is 12 digits: {@code XXXX XXXX 1234}. */
    public static String aadhaar(String last4) {
        return last4 == null ? null : "XXXX XXXX " + last4;
    }

    /** Bank account numbers vary in length: {@code XXXX4821}. */
    public static String accountNumber(String last4) {
        return last4 == null ? null : "XXXX" + last4;
    }
}
