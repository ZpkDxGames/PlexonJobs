package com.plexon.jobs.runtime;

/** Pure in-memory coalescing window used by the player reward BossBar. */
final class RewardFeedbackWindow {
    private String jobId = "";
    private long accumulatedXp;
    private long accumulatedMoney;
    private long expiresAtNanos;

    Update add(String nextJobId, long xp, long moneyMinor, long nowNanos, long durationNanos) {
        if (!nextJobId.equals(jobId) || expired(nowNanos)) {
            jobId = nextJobId;
            accumulatedXp = 0;
            accumulatedMoney = 0;
        }
        accumulatedXp = saturatingAdd(accumulatedXp, Math.max(0, xp));
        accumulatedMoney = saturatingAdd(accumulatedMoney, Math.max(0, moneyMinor));
        expiresAtNanos = saturatingAdd(nowNanos, Math.max(0, durationNanos));
        return new Update(jobId, accumulatedXp, accumulatedMoney, expiresAtNanos);
    }

    boolean expired(long nowNanos) {
        return nowNanos >= expiresAtNanos;
    }

    long expiresAtNanos() { return expiresAtNanos; }

    private static long saturatingAdd(long left, long right) {
        if (right <= 0) return left;
        if (left > Long.MAX_VALUE - right) return Long.MAX_VALUE;
        return left + right;
    }

    record Update(String jobId, long xp, long moneyMinor, long expiresAtNanos) {}
}
