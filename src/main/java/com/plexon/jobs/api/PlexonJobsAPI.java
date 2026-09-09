package com.plexon.jobs.api;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface PlexonJobsAPI {
    Optional<PlayerJobsView> profile(UUID playerId);
    Collection<String> activeJobs(UUID playerId);
    boolean isInJob(UUID playerId, String jobId);
    int level(UUID playerId, String jobId);
    long totalXp(UUID playerId, String jobId);
    Result joinJob(UUID playerId, String jobId);
    Result leaveJob(UUID playerId, String jobId);
    Result addJobXp(UUID playerId, String jobId, long amount, String source);
    Collection<JobView> jobDefinitions();
    long pendingPayout(UUID playerId);

    record JobView(String id, String displayName, String icon, boolean enabled, int maxLevel) {}
    record PlayerJobView(String id, boolean joined, int level, long totalXp, long xpToNextLevel) {}
    record PlayerJobsView(UUID playerId, Map<String, PlayerJobView> jobs) {}
    record Result(boolean success, String message) {
        public static Result ok(String message) { return new Result(true, message); }
        public static Result fail(String message) { return new Result(false, message); }
    }
}
