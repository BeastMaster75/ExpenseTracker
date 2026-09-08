package com.expensetracker.common.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class MoneyUtils {

    public static final int SCALE = 2;

    private MoneyUtils() {
    }

    public static BigDecimal percentage(BigDecimal part, BigDecimal whole) {

        if (part == null || whole == null || whole.signum() == 0) {
            return BigDecimal.ZERO.setScale(SCALE);
        }

        return part
                .multiply(BigDecimal.valueOf(100))
                .divide(whole, SCALE, RoundingMode.HALF_UP);
    }

    public static boolean isNegative(BigDecimal value) {
        return value != null && value.signum() < 0;
    }

    public static BigDecimal orZero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
