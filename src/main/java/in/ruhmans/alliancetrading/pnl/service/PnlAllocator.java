package in.ruhmans.alliancetrading.pnl.service;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import in.ruhmans.alliancetrading.common.money.MoneyUtils;

/**
 * Splits a member's P&L across their investments pro-rata by the principal each one has earning, so
 * per-investment earnings stay meaningful while the member total is exactly what the batch says.
 *
 * <p>Largest-remainder method in paise: every share is rounded towards zero, then the leftover paise go to
 * the investments with the largest remainders (earliest first on a tie). The shares always sum to the amount.
 */
public final class PnlAllocator {

    private PnlAllocator() {}

    /**
     * @param amount  member amount, any sign, 2 dp
     * @param weights investment id to earning principal, oldest first; all non-negative, at least one positive
     * @return investment id to share, in the given order
     */
    public static Map<UUID, BigDecimal> split(BigDecimal amount, Map<UUID, BigDecimal> weights) {
        if (weights.isEmpty()) {
            throw new IllegalArgumentException("A member must hold at least one investment to receive P&L");
        }
        List<UUID> ids = new ArrayList<>(weights.keySet());
        List<BigInteger> paiseWeights = ids.stream().map(id -> toPaise(weights.get(id))).toList();
        BigInteger weightSum = paiseWeights.stream().reduce(BigInteger.ZERO, BigInteger::add);
        if (weightSum.signum() <= 0 || paiseWeights.stream().anyMatch(w -> w.signum() < 0)) {
            throw new IllegalArgumentException("Weights must be non-negative with a positive total");
        }

        BigInteger totalPaise = toPaise(amount);
        BigInteger absPaise = totalPaise.abs();
        BigInteger[] shares = new BigInteger[ids.size()];
        BigInteger[] remainders = new BigInteger[ids.size()];
        BigInteger allocated = BigInteger.ZERO;
        for (int i = 0; i < ids.size(); i++) {
            BigInteger[] qr = absPaise.multiply(paiseWeights.get(i)).divideAndRemainder(weightSum);
            shares[i] = qr[0];
            remainders[i] = qr[1];
            allocated = allocated.add(qr[0]);
        }

        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            order.add(i);
        }
        order.sort(Comparator.comparing((Integer i) -> remainders[i]).reversed().thenComparing(i -> i));
        int leftover = absPaise.subtract(allocated).intValueExact();
        for (int k = 0; k < leftover; k++) {
            int i = order.get(k);
            shares[i] = shares[i].add(BigInteger.ONE);
        }

        Map<UUID, BigDecimal> split = new LinkedHashMap<>();
        for (int i = 0; i < ids.size(); i++) {
            BigInteger signed = totalPaise.signum() < 0 ? shares[i].negate() : shares[i];
            split.put(ids.get(i), new BigDecimal(signed, MoneyUtils.MONEY_SCALE));
        }
        return split;
    }

    private static BigInteger toPaise(BigDecimal value) {
        return MoneyUtils.money(value).movePointRight(MoneyUtils.MONEY_SCALE).toBigIntegerExact();
    }
}
