package com.plexon.jobs.runtime;

/** Compact immutable state consulted by gameplay callbacks. */
public record PlayerExecutionState(
        long joinedJobMask,
        long activityMask,
        boolean profileReady,
        boolean dailyReady,
        boolean rewardReady
) {
    public static final PlayerExecutionState NOT_READY = new PlayerExecutionState(0L, 0L, false, false, false);
}
