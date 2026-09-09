package com.plexon.jobs.model;

public final class JobProgress {
    private long totalXp;
    private int level;
    private boolean joined;

    public JobProgress(long totalXp, int level, boolean joined) {
        this.totalXp = Math.max(0, totalXp);
        this.level = Math.max(1, level);
        this.joined = joined;
    }

    public long totalXp() { return totalXp; }
    public int level() { return level; }
    public boolean joined() { return joined; }
    public void joined(boolean value) { joined = value; }

    public ProgressDelta addXp(long amount, XpCurve curve) {
        if (amount <= 0) return new ProgressDelta(level, level, totalXp);
        int old = level;
        totalXp = Math.addExact(totalXp, amount);
        level = curve.levelForTotalXp(totalXp);
        return new ProgressDelta(old, level, totalXp);
    }

    public void setTotalXp(long totalXp, XpCurve curve) {
        this.totalXp = Math.max(0, totalXp);
        this.level = curve.levelForTotalXp(this.totalXp);
    }

    public record ProgressDelta(int oldLevel, int newLevel, long totalXp) {
        public boolean leveledUp() { return newLevel > oldLevel; }
    }
}
