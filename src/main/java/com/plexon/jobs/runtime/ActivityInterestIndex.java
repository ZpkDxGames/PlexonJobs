package com.plexon.jobs.runtime;

import com.plexon.jobs.model.ActivityType;
import com.plexon.jobs.model.JobProgress;
import com.plexon.jobs.model.PlayerJobsProfile;
import org.bukkit.Bukkit;

import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Authoritative online participation index. Gameplay callbacks never rebuild membership from a profile.
 * Mutations occur on the primary thread when lifecycle/readiness/membership/reload state changes.
 */
public final class ActivityInterestIndex {
    private final ProfileManager profiles;
    private final DailyLimitPersistence dailyPersistence;
    private final Map<UUID, PlayerExecutionState> states = new java.util.HashMap<>();
    private final EnumMap<ActivityType, LinkedHashSet<UUID>> participants = new EnumMap<>(ActivityType.class);
    private CompiledJobRoutes routes;
    private RuntimeMode mode;
    private Runnable topologyListener = () -> { };
    private long globalJoinedJobMask;

    public ActivityInterestIndex(ProfileManager profiles, DailyLimitPersistence dailyPersistence,
                                 CompiledJobRoutes routes, RuntimeMode mode) {
        this.profiles = java.util.Objects.requireNonNull(profiles);
        this.dailyPersistence = java.util.Objects.requireNonNull(dailyPersistence);
        this.routes = java.util.Objects.requireNonNull(routes);
        this.mode = java.util.Objects.requireNonNull(mode);
        for (ActivityType type : ActivityType.values()) participants.put(type, new LinkedHashSet<>());
    }

    public void topologyListener(Runnable listener) {
        this.topologyListener = listener == null ? () -> { } : listener;
    }

    public PlayerExecutionState state(UUID playerId) {
        return states.getOrDefault(playerId, PlayerExecutionState.NOT_READY);
    }

    public long matchedJobs(UUID playerId, ActivityType type, long routeMask) {
        PlayerExecutionState state = state(playerId);
        if (!state.profileReady() || !routes.hasActivity(state.activityMask(), type)) return 0L;
        return state.joinedJobMask() & routeMask;
    }

    public boolean hasGlobalInterest(ActivityType type) {
        Set<UUID> set = participants.get(type);
        return set != null && !set.isEmpty();
    }

    public Collection<UUID> participants(ActivityType type) {
        Set<UUID> set = participants.get(type);
        return set == null || set.isEmpty() ? Set.of() : Set.copyOf(set);
    }

    public int participantCount(ActivityType type) {
        Set<UUID> set = participants.get(type);
        return set == null ? 0 : set.size();
    }

    public long globalJoinedJobMask() { return globalJoinedJobMask; }

    public void refresh(UUID playerId) {
        requirePrimaryThread();
        PlayerExecutionState previous = states.getOrDefault(playerId, PlayerExecutionState.NOT_READY);
        PlayerExecutionState next = compute(playerId);
        if (next.equals(previous)) return;
        states.put(playerId, next);
        updateParticipants(playerId, previous.activityMask(), next.activityMask());
        recomputeGlobalJoinedJobMask();
        if (previous.joinedJobMask() != next.joinedJobMask() || previous.activityMask() != next.activityMask()) {
            topologyListener.run();
        }
    }

    public void remove(UUID playerId) {
        requirePrimaryThread();
        PlayerExecutionState previous = states.remove(playerId);
        if (previous == null) return;
        updateParticipants(playerId, previous.activityMask(), 0L);
        recomputeGlobalJoinedJobMask();
        if (previous.joinedJobMask() != 0L || previous.activityMask() != 0L) topologyListener.run();
    }

    public void reconfigure(CompiledJobRoutes nextRoutes, RuntimeMode nextMode) {
        requirePrimaryThread();
        this.routes = java.util.Objects.requireNonNull(nextRoutes);
        this.mode = java.util.Objects.requireNonNull(nextMode);
        rebuildOnline();
    }

    public void rebuildOnline() {
        requirePrimaryThread();
        states.clear();
        participants.values().forEach(Set::clear);
        globalJoinedJobMask = 0L;
        for (var player : Bukkit.getOnlinePlayers()) {
            UUID playerId = player.getUniqueId();
            PlayerExecutionState next = compute(playerId);
            states.put(playerId, next);
            updateParticipants(playerId, 0L, next.activityMask());
            globalJoinedJobMask |= next.joinedJobMask();
        }
        topologyListener.run();
    }

    public CompiledJobRoutes routes() { return routes; }

    private PlayerExecutionState compute(UUID playerId) {
        PlayerJobsProfile profile = profiles.get(playerId);
        boolean profileReady = profile != null && profile.state() == PlayerJobsProfile.State.READY;
        boolean dailyReady = mode != RuntimeMode.PRIMARY || dailyPersistence.ready(playerId);
        long joinedMask = 0L;
        if (profileReady) {
            for (Map.Entry<String, JobProgress> entry : profile.jobs().entrySet()) {
                if (entry.getValue().joined()) joinedMask |= routes.jobMask(entry.getKey());
            }
        }
        long activityMask = routes.activityMaskForJobs(joinedMask);
        return new PlayerExecutionState(joinedMask, activityMask, profileReady, dailyReady,
                profileReady && dailyReady && mode != RuntimeMode.DISABLED);
    }

    private void updateParticipants(UUID playerId, long oldMask, long newMask) {
        long changed = oldMask ^ newMask;
        if (changed == 0L) return;
        for (ActivityType type : ActivityType.values()) {
            long bit = CompiledJobRoutes.activityBit(type);
            if ((changed & bit) == 0L) continue;
            if ((newMask & bit) != 0L) participants.get(type).add(playerId);
            else participants.get(type).remove(playerId);
        }
    }

    private void recomputeGlobalJoinedJobMask() {
        long mask = 0L;
        for (PlayerExecutionState state : states.values()) mask |= state.joinedJobMask();
        globalJoinedJobMask = mask;
    }

    private static void requirePrimaryThread() {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("ActivityInterestIndex mutation must run on the primary thread");
    }
}
