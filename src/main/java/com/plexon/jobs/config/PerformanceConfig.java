package com.plexon.jobs.config;

/** Performance controls with deliberately small, bounded tuning surface. */
public record PerformanceConfig(
        boolean dynamicListeners,
        boolean dynamicCoreBlockSubscription,
        int feedbackFlushTicks
) {
    public PerformanceConfig {
        if (feedbackFlushTicks < 1 || feedbackFlushTicks > 20) {
            throw new IllegalArgumentException("feedbackFlushTicks must be between 1 and 20");
        }
    }
}
