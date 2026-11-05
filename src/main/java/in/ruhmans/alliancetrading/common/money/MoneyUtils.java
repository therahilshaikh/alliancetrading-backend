package in.ruhmans.alliancetrading.common.money;

import java.math.BigDecimal;
import java.math.RoundingMode;

import in.ruhmans.alliancetrading.common.error.ApiException;
import in.ruhmans.alliancetrading.common.error.ErrorCode;

/**
 * Money rules: BigDecimal only, always 2 decimal places (NUMERIC(15,2)).
 * Percentages are 4 decimal places (NUMERIC(6,4)), e.g. 0.0200 = 2%.
 */
public final class MoneyUtils {

    public static final int MONEY_SCALE = 2;
    public static final int PERCENT_SCALE = 4;
    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(MONEY_SCALE);

    private MoneyUtils() {}

    public static BigDecimal money(BigDecimal value) {
        if (value == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Amount is required");
        }
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    public static BigDecimal requirePositive(BigDecimal value, String field) {
        BigDecimal v = money(value);
        if (v.signum() <= 0) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, field + " must be positive");
        }
        return v;
    }

    /** fee = gross * pct, rounded HALF_UP to 2dp. pct is a 4dp fraction (0.0200 = 2%). */
    public static BigDecimal percentOf(BigDecimal gross, BigDecimal pct) {
        return gross.multiply(pct).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
