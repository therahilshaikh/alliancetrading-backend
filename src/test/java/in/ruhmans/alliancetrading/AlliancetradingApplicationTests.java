package in.ruhmans.alliancetrading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import in.ruhmans.alliancetrading.common.error.ApiException;
import in.ruhmans.alliancetrading.common.money.MoneyUtils;
import in.ruhmans.alliancetrading.common.pagination.CursorCodec;

import org.junit.jupiter.api.Test;

class CommonUtilsTest {

    @Test
    void moneyScalesHalfUp() {
        assertThat(MoneyUtils.money(new BigDecimal("10.005"))).isEqualByComparingTo("10.01");
    }

    @Test
    void requirePositiveRejectsZeroAndNegative() {
        assertThatThrownBy(() -> MoneyUtils.requirePositive(BigDecimal.ZERO, "amount"))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> MoneyUtils.requirePositive(new BigDecimal("-1"), "amount"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void percentOfRoundsHalfUp() {
        assertThat(MoneyUtils.percentOf(new BigDecimal("100.00"), new BigDecimal("0.0200")))
                .isEqualByComparingTo("2.00");
        assertThat(MoneyUtils.percentOf(new BigDecimal("999.99"), new BigDecimal("0.0250")))
                .isEqualByComparingTo("25.00");
    }

    @Test
    void cursorRoundTrip() {
        Instant now = Instant.now();
        UUID id = UUID.randomUUID();
        var decoded = CursorCodec.decode(CursorCodec.encode(now, id));
        assertThat(decoded.createdAt()).isEqualTo(now);
        assertThat(decoded.id()).isEqualTo(id);
    }

    @Test
    void invalidCursorRejected() {
        assertThatThrownBy(() -> CursorCodec.decode("not-a-cursor")).isInstanceOf(ApiException.class);
    }
}
