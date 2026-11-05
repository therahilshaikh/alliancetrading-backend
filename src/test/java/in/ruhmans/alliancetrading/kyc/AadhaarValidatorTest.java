package in.ruhmans.alliancetrading.kyc;

import static org.assertj.core.api.Assertions.assertThat;

import in.ruhmans.alliancetrading.kyc.service.AadhaarValidator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AadhaarValidatorTest {

    // Verhoeff tables, used only to build valid test numbers independently of the validator
    private static final int[][] D = {
            {0, 1, 2, 3, 4, 5, 6, 7, 8, 9}, {1, 2, 3, 4, 0, 6, 7, 8, 9, 5},
            {2, 3, 4, 0, 1, 7, 8, 9, 5, 6}, {3, 4, 0, 1, 2, 8, 9, 5, 6, 7},
            {4, 0, 1, 2, 3, 9, 5, 6, 7, 8}, {5, 9, 8, 7, 6, 0, 4, 3, 2, 1},
            {6, 5, 9, 8, 7, 1, 0, 4, 3, 2}, {7, 6, 5, 9, 8, 2, 1, 0, 4, 3},
            {8, 7, 6, 5, 9, 3, 2, 1, 0, 4}, {9, 8, 7, 6, 5, 4, 3, 2, 1, 0}};
    private static final int[][] P = {
            {0, 1, 2, 3, 4, 5, 6, 7, 8, 9}, {1, 5, 7, 6, 2, 8, 3, 0, 9, 4},
            {5, 8, 0, 3, 7, 9, 6, 1, 4, 2}, {8, 9, 1, 6, 0, 4, 3, 5, 2, 7},
            {9, 4, 5, 3, 1, 2, 6, 8, 7, 0}, {4, 2, 8, 6, 5, 7, 3, 9, 0, 1},
            {2, 7, 9, 3, 8, 0, 6, 4, 1, 5}, {7, 0, 4, 6, 9, 1, 3, 2, 5, 8}};
    private static final int[] INV = {0, 4, 3, 2, 1, 5, 6, 7, 8, 9};

    /** Appends the Verhoeff check digit to an 11-digit prefix. */
    static String withCheckDigit(String first11) {
        int c = 0;
        for (int i = 0; i < first11.length(); i++) {
            int digit = first11.charAt(first11.length() - 1 - i) - '0';
            c = D[c][P[(i + 1) % 8][digit]];
        }
        return first11 + INV[c];
    }

    @Test
    void acceptsNumbersWithValidCheckDigit() {
        assertThat(AadhaarValidator.isValid(withCheckDigit("23412341234"))).isTrue();
        assertThat(AadhaarValidator.isValid(withCheckDigit("98765432109"))).isTrue();
    }

    @Test
    void rejectsWrongCheckDigit() {
        String valid = withCheckDigit("23412341234");
        String wrong = valid.substring(0, 11) + ((valid.charAt(11) - '0' + 1) % 10);
        assertThat(AadhaarValidator.isValid(wrong)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "1234", "12345678901a", "0234123412346", "123412341234", "023412341234"})
    void rejectsMalformedOrReservedPrefix(String value) {
        assertThat(AadhaarValidator.isValid(value)).isFalse();
    }

    @Test
    void rejectsNull() {
        assertThat(AadhaarValidator.isValid(null)).isFalse();
    }
}
