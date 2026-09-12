package com.plexon.jobs.runtime;

import com.plexon.jobs.model.ActivityReward;
import com.plexon.jobs.model.ActivityType;
import com.plexon.jobs.model.JobDefinition;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Immutable, allocation-free-at-dispatch routing compiled from accepted job definitions.
 * The runtime deliberately limits a compiled catalog to 64 jobs so membership and routes fit in a long mask.
 */
public final class CompiledJobRoutes {
    private final JobDefinition[] jobs;
    private final Map<String, Integer> bitByJobId;
    private final Map<Material, CompiledRoute> breakRoutes;
    private final Map<ActivityType, Map<String, CompiledRoute>> activityRoutes;
    private final Map<ActivityType, CompiledRoute> wildcardRoutes;
    private final long[] jobActivityMasks;
    private final long allJobMask;

    public CompiledJobRoutes(Collection<JobDefinition> definitions) {
        List<JobDefinition> ordered = List.copyOf(definitions);
        if (ordered.size() > 64) {
            throw new IllegalArgumentException("PlexonJobs supports at most 64 compiled job definitions; found " + ordered.size());
        }
        this.jobs = ordered.toArray(JobDefinition[]::new);
        Map<String, Integer> bits = new LinkedHashMap<>();
        for (int i = 0; i < jobs.length; i++) {
            String id = jobs[i].id();
            if (bits.putIfAbsent(id, i) != null) throw new IllegalArgumentException("Duplicate job id: " + id);
        }
        this.bitByJobId = Collections.unmodifiableMap(bits);
        this.allJobMask = jobs.length == 64 ? -1L : (jobs.length == 0 ? 0L : (1L << jobs.length) - 1L);
        this.jobActivityMasks = new long[jobs.length];

        EnumMap<Material, MutableRoute> breaks = new EnumMap<>(Material.class);
        EnumMap<ActivityType, LinkedHashSet<String>> keysByType = new EnumMap<>(ActivityType.class);
        for (int bit = 0; bit < jobs.length; bit++) {
            JobDefinition job = jobs[bit];
            if (!job.enabled()) continue;
            if (job.handlesBreaks()) {
                jobActivityMasks[bit] |= activityBit(ActivityType.BREAK);
                for (Map.Entry<Material, ActivityReward> entry : job.breakRewards().entrySet()) {
                    MutableRoute route = breaks.computeIfAbsent(entry.getKey(), ignored -> new MutableRoute(jobs.length));
                    route.put(bit, entry.getValue());
                }
            }
            for (ActivityType type : ActivityType.values()) {
                if (type == ActivityType.BREAK || type == ActivityType.DAMAGE || !job.handles(type)) continue;
                jobActivityMasks[bit] |= activityBit(type);
                keysByType.computeIfAbsent(type, ignored -> new LinkedHashSet<>()).addAll(job.activityKeys(type));
            }
        }

        EnumMap<Material, CompiledRoute> frozenBreaks = new EnumMap<>(Material.class);
        breaks.forEach((material, route) -> frozenBreaks.put(material, route.freeze()));
        this.breakRoutes = Collections.unmodifiableMap(frozenBreaks);

        EnumMap<ActivityType, Map<String, CompiledRoute>> compiledActivities = new EnumMap<>(ActivityType.class);
        EnumMap<ActivityType, CompiledRoute> compiledWildcards = new EnumMap<>(ActivityType.class);
        for (Map.Entry<ActivityType, LinkedHashSet<String>> typeEntry : keysByType.entrySet()) {
            ActivityType type = typeEntry.getKey();
            LinkedHashMap<String, CompiledRoute> keyed = new LinkedHashMap<>();
            boolean hasWildcard = typeEntry.getValue().contains("*");
            if (hasWildcard) {
                MutableRoute wildcard = compileGeneric(type, "*");
                CompiledRoute frozen = wildcard.freeze();
                if (frozen.jobMask() != 0L) compiledWildcards.put(type, frozen);
            }
            for (String key : typeEntry.getValue()) {
                if ("*".equals(key)) continue;
                CompiledRoute route = compileGeneric(type, key).freeze();
                if (route.jobMask() != 0L) keyed.put(normalizeKey(key), route);
            }
            compiledActivities.put(type, Collections.unmodifiableMap(keyed));
        }
        this.activityRoutes = Collections.unmodifiableMap(compiledActivities);
        this.wildcardRoutes = Collections.unmodifiableMap(compiledWildcards);
    }

    private MutableRoute compileGeneric(ActivityType type, String key) {
        MutableRoute route = new MutableRoute(jobs.length);
        for (int bit = 0; bit < jobs.length; bit++) {
            JobDefinition job = jobs[bit];
            if (!job.enabled() || !job.handles(type)) continue;
            ActivityReward reward = job.reward(type, key);
            if (reward != null && !reward.empty()) route.put(bit, reward);
        }
        return route;
    }

    public CompiledRoute breakRoute(Material material) {
        return breakRoutes.getOrDefault(material, CompiledRoute.EMPTY);
    }

    public CompiledRoute route(ActivityType type, String key) {
        if (type == ActivityType.BREAK) return CompiledRoute.EMPTY;
        Map<String, CompiledRoute> routes = activityRoutes.get(type);
        if (routes != null && !routes.isEmpty()) {
            CompiledRoute exact = routes.get(normalizeKey(key));
            if (exact != null) return exact;
        }
        return wildcardRoutes.getOrDefault(type, CompiledRoute.EMPTY);
    }

    public long jobMask(String jobId) {
        Integer bit = bitByJobId.get(jobId);
        return bit == null ? 0L : 1L << bit;
    }

    public long jobMask(Collection<String> jobIds) {
        long mask = 0L;
        for (String id : jobIds) mask |= jobMask(id);
        return mask;
    }

    public long allJobMask() { return allJobMask; }

    public JobDefinition job(int bit) {
        if (bit < 0 || bit >= jobs.length) throw new IndexOutOfBoundsException("job bit " + bit);
        return jobs[bit];
    }

    public int jobCount() { return jobs.length; }

    public Set<Material> allBreakMaterials() {
        if (breakRoutes.isEmpty()) return Set.of();
        return Collections.unmodifiableSet(EnumSet.copyOf(breakRoutes.keySet()));
    }

    /** Low-frequency transition helper; allocations here never occur in block callbacks. */
    public Set<Material> breakMaterialsForJobs(long joinedJobMask) {
        if (joinedJobMask == 0L || breakRoutes.isEmpty()) return Set.of();
        EnumSet<Material> result = EnumSet.noneOf(Material.class);
        breakRoutes.forEach((material, route) -> {
            if ((route.jobMask() & joinedJobMask) != 0L) result.add(material);
        });
        return result.isEmpty() ? Set.of() : Collections.unmodifiableSet(result);
    }

    public long activityMaskForJobs(long joinedJobMask) {
        long result = 0L;
        long remaining = joinedJobMask & allJobMask;
        while (remaining != 0L) {
            int bit = Long.numberOfTrailingZeros(remaining);
            result |= jobActivityMasks[bit];
            remaining &= remaining - 1L;
        }
        return result;
    }

    public boolean hasActivity(long activityMask, ActivityType type) {
        return (activityMask & activityBit(type)) != 0L;
    }

    public static long activityBit(ActivityType type) {
        return 1L << type.ordinal();
    }

    private static String normalizeKey(String key) {
        if (key == null || key.isBlank()) return "*";
        return key.trim().toUpperCase(Locale.ROOT);
    }

    public static final class CompiledRoute {
        private static final CompiledRoute EMPTY = new CompiledRoute(0L, new ActivityReward[0]);
        private final long jobMask;
        private final ActivityReward[] rewards;

        private CompiledRoute(long jobMask, ActivityReward[] rewards) {
            this.jobMask = jobMask;
            this.rewards = rewards;
        }

        public long jobMask() { return jobMask; }

        public ActivityReward reward(int bit) {
            return bit < 0 || bit >= rewards.length || rewards[bit] == null ? ActivityReward.ZERO : rewards[bit];
        }
    }

    private static final class MutableRoute {
        private long mask;
        private final ActivityReward[] rewards;

        private MutableRoute(int size) { this.rewards = new ActivityReward[size]; }

        private void put(int bit, ActivityReward reward) {
            if (reward == null || reward.empty()) return;
            mask |= 1L << bit;
            rewards[bit] = reward;
        }

        private CompiledRoute freeze() { return new CompiledRoute(mask, rewards.clone()); }
    }
}
