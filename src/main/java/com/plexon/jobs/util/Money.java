package com.plexon.jobs.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class Money {
    private Money() {}

    public static long toMinor(BigDecimal amount, int scale) {
        if (amount == null || amount.signum() <= 0) return 0;
        return amount.setScale(scale, RoundingMode.HALF_UP).movePointRight(scale).longValueExact();
    }

    public static BigDecimal fromMinor(long minor, int scale) {
        return BigDecimal.valueOf(minor, scale);
    }

    public static String format(long minor, int scale) {
        return fromMinor(Math.max(0, minor), scale).setScale(scale, RoundingMode.UNNECESSARY).toPlainString();
    }
}
