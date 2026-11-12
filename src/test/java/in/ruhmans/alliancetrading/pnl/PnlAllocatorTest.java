package in.ruhmans.alliancetrading.pnl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import in.ruhmans.alliancetrading.pnl.service.PnlAllocator;

import org.junit.jupiter.api.Test;

class PnlAllocatorTest {

    private final UUID first = UUID.randomUUID();
    private final UUID second = UUID.randomUUID();
    private final UUID third = UUID.randomUUID();

    /** Ordered weights: id, principal, id, principal, ... (oldest first). */
    private static Map<UUID, BigDecimal> weights(Object... idAndPrincipal) {
        Map<UUID, BigDecimal> weights = new LinkedHashMap<>();
        for (int i = 0; i < idAndPrincipal.length; i += 2) {
            weights.put((UUID) idAndPrincipal[i], new BigDecimal((String) idAndPrincipal[i + 1]));
        }
        return weights;
    }

    private static BigDecimal sum(Map<UUID, BigDecimal> split) {
        return split.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Test
    void splitsByPrincipalAndSumsExactly() {
        Map<UUID, BigDecimal> split = PnlAllocator.split(new BigDecimal("1000.00"),
                weights(first, "50000", second, "20000"));

        assertThat(split.get(first)).isEqualByComparingTo("714.29");
        assertThat(split.get(second)).isEqualByComparingTo("285.71");
        assertThat(sum(split)).isEqualByComparingTo("1000.00");
    }

    @Test
    void leftoverPaiseGoToTheOldestOnATie() {
        Map<UUID, BigDecimal> split = PnlAllocator.split(new BigDecimal("100.00"),
                weights(first, "10000", second, "10000", third, "10000"));

        assertThat(split.get(first)).isEqualByComparingTo("33.34");
        assertThat(split.get(second)).isEqualByComparingTo("33.33");
        assertThat(split.get(third)).isEqualByComparingTo("33.33");
    }

    @Test
    void lossesSplitWithTheSameMagnitudes() {
        Map<UUID, BigDecimal> split = PnlAllocator.split(new BigDecimal("-1000.00"),
                weights(first, "50000", second, "20000"));

        assertThat(split.get(first)).isEqualByComparingTo("-714.29");
        assertThat(split.get(second)).isEqualByComparingTo("-285.71");
        assertThat(sum(split)).isEqualByComparingTo("-1000.00");
    }

    @Test
    void zeroWeightGetsNothing() {
        Map<UUID, BigDecimal> split = PnlAllocator.split(new BigDecimal("400.00"),
                weights(first, "0", second, "20000"));

        assertThat(split.get(first)).isEqualByComparingTo("0.00");
        assertThat(split.get(second)).isEqualByComparingTo("400.00");
    }

    @Test
    void singleInvestmentGetsEverything() {
        assertThat(PnlAllocator.split(new BigDecimal("0.01"), weights(first, "10000")).get(first))
                .isEqualByComparingTo("0.01");
    }

    @Test
    void noWeightsOrNoPrincipalIsRefused() {
        assertThatThrownBy(() -> PnlAllocator.split(BigDecimal.TEN, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PnlAllocator.split(BigDecimal.TEN, weights(first, "0")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
