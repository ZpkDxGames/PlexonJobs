package com.plexon.jobs.model;

import java.util.Arrays;

public final class XpCurve {
    private final int maxLevel;
    private final long[] cumulative;

    public XpCurve(int maxLevel, long baseXp, double exponent) {
        if (maxLevel < 1) throw new IllegalArgumentException("maxLevel");
        if (baseXp < 1) throw new IllegalArgumentException("baseXp");
        if (!Double.isFinite(exponent) || exponent <= 0) throw new IllegalArgumentException("exponent");
        this.maxLevel = maxLevel;
        this.cumulative = new long[maxLevel + 1];
        cumulative[1] = 0;
        long total = 0;
        for (int level = 2; level <= maxLevel; level++) {
            double raw = baseXp * Math.pow(level - 1, exponent);
            long needed = Math.max(1L, Math.round(raw));
            total = Math.addExact(total, needed);
            cumulative[level] = total;
        }
    }

    public int maxLevel() { return maxLevel; }
    public long totalXpForLevel(int level) {
        int bounded = Math.max(1, Math.min(level, maxLevel));
        return cumulative[bounded];
    }

    public int levelForTotalXp(long xp) {
        if (xp <= 0) return 1;
        int idx = Arrays.binarySearch(cumulative, xp);
        if (idx >= 0) return Math.max(1, idx);
        int insertion = -idx - 1;
        return Math.max(1, Math.min(maxLevel, insertion - 1));
    }

    public long xpToNextLevel(long xp) {
        int level = levelForTotalXp(xp);
        if (level >= maxLevel) return 0;
        return Math.max(0, cumulative[level + 1] - xp);
    }
}
