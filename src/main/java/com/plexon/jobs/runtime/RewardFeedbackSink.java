package com.plexon.jobs.runtime;

/** Direct internal feedback path; it deliberately does not depend on PlexonJobs' public Bukkit events. */
public interface RewardFeedbackSink {
    RewardFeedbackSink NOOP = new RewardFeedbackSink() { };

    default void onReward(RewardFeedback reward) { }
    default void onLevelUp(LevelUpFeedback levelUp) { }

    record RewardFeedback(java.util.UUID playerId, String jobId, long jobXp, long moneyMinor,
                          long totalXp, int level) { }
    record LevelUpFeedback(java.util.UUID playerId, String jobId, int oldLevel, int newLevel,
                           long totalXp) { }
}
