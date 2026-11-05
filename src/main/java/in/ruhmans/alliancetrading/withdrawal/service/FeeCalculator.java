package in.ruhmans.alliancetrading.withdrawal.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import in.ruhmans.alliancetrading.common.money.MoneyUtils;

/**
 * fee = gross × feePct, rounded HALF_UP to 2dp. net = gross − fee.
 * feePct is a 4dp fraction (0.0200 = 2%).
 */
public final class FeeCalculator {

    private FeeCalculator() {}

    public record FeeBreakdown(BigDecimal gross, BigDecimal feePct, BigDecimal fee, BigDecimal net) {}

    public static FeeBreakdown compute(BigDecimal gross, BigDecimal feePct) {
        BigDecimal g = MoneyUtils.money(gross);
        BigDecimal fee = g.multiply(feePct).setScale(MoneyUtils.MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal net = g.subtract(fee);
        return new FeeBreakdown(g, feePct, fee, net);
    }
}
