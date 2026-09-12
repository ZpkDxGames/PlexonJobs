package com.plexon.jobs.model;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class PlayerJobsProfile {
    public enum State { LOADING, READY, FAILED }

    private final UUID playerId;
    private final Map<String, JobProgress> jobs = new LinkedHashMap<>();
    private State state;
    private long revision;

    public PlayerJobsProfile(UUID playerId, State state) {
        this.playerId = Objects.requireNonNull(playerId);
        this.state = Objects.requireNonNull(state);
    }

    public UUID playerId() { return playerId; }
    public State state() { return state; }
    public void state(State value) { state = Objects.requireNonNull(value); }
    public long revision() { return revision; }
    public void touch() { revision++; }

    public Map<String, JobProgress> jobs() { return Collections.unmodifiableMap(jobs); }
    public JobProgress progress(String jobId) { return jobs.computeIfAbsent(jobId, ignored -> new JobProgress(0, 1, false)); }
    public void put(String jobId, JobProgress progress) { jobs.put(jobId, progress); }

    /** Removes persisted job ids that are no longer part of the accepted runtime definition set. */
    public boolean retainJobs(Set<String> acceptedJobIds) {
        Objects.requireNonNull(acceptedJobIds, "acceptedJobIds");
        boolean changed = jobs.keySet().removeIf(id -> !acceptedJobIds.contains(id));
        if (changed) revision++;
        return changed;
    }

    public long activeCount() { return jobs.values().stream().filter(JobProgress::joined).count(); }
    public Collection<String> activeJobIds() {
        return jobs.entrySet().stream().filter(e -> e.getValue().joined()).map(Map.Entry::getKey).toList();
    }

    public Snapshot snapshot() {
        Map<String, ProgressSnapshot> copy = new LinkedHashMap<>();
        jobs.forEach((jobId, progress) -> copy.put(jobId,
                new ProgressSnapshot(progress.totalXp(), progress.level(), progress.joined())));
        return new Snapshot(playerId, revision, Collections.unmodifiableMap(copy));
    }

    public record Snapshot(UUID playerId, long revision, Map<String, ProgressSnapshot> jobs) {}
    public record ProgressSnapshot(long totalXp, int level, boolean joined) {}
}
