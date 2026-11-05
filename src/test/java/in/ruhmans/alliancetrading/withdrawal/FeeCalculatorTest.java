package in.ruhmans.alliancetrading.withdrawal;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import in.ruhmans.alliancetrading.withdrawal.service.FeeCalculator;

import org.junit.jupiter.api.Test;

class FeeCalculatorTest {

    @Test
    void twoPercentOn10000() {
        var r = FeeCalculator.compute(new BigDecimal("10000.00"), new BigDecimal("0.0200"));
        assertThat(r.fee()).isEqualByComparingTo("200.00");
        assertThat(r.net()).isEqualByComparingTo("9800.00");
        assertThat(r.gross()).isEqualByComparingTo("10000.00");
    }

    @Test
    void roundsHalfUpAtHalfPaisa() {
        // 999.99 * 0.025 = 24.99975 → 25.00 (HALF_UP)
        var r = FeeCalculator.compute(new BigDecimal("999.99"), new BigDecimal("0.0250"));
        assertThat(r.fee()).isEqualByComparingTo("25.00");
        assertThat(r.net()).isEqualByComparingTo("974.99");
    }

    @Test
    void zeroFee() {
        var r = FeeCalculator.compute(new BigDecimal("5000"), new BigDecimal("0.0000"));
        assertThat(r.fee()).isEqualByComparingTo("0.00");
        assertThat(r.net()).isEqualByComparingTo("5000.00");
    }

    @Test
    void netEqualsGrossMinusFeeAlways() {
        var r = FeeCalculator.compute(new BigDecimal("12345.67"), new BigDecimal("0.0333"));
        assertThat(r.gross().subtract(r.fee())).isEqualByComparingTo(r.net());
    }

    @Test
    void highFeePct() {
        var r = FeeCalculator.compute(new BigDecimal("1000.00"), new BigDecimal("0.5000"));
        assertThat(r.fee()).isEqualByComparingTo("500.00");
        assertThat(r.net()).isEqualByComparingTo("500.00");
    }
}
