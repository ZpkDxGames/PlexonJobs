package com.plexon.jobs.model;

import org.bukkit.Material;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class JobDefinition {
    private final String id;
    private final String displayName;
    private final Material icon;
    private final boolean enabled;
    private final int maxLevel;
    private final Map<Material, ActivityReward> breakRewards;
    private final Map<ActivityType, Map<String, ActivityReward>> activityRewards;

    public JobDefinition(String id, String displayName, Material icon, boolean enabled, int maxLevel,
                         Map<Material, ActivityReward> breakRewards) {
        this(id, displayName, icon, enabled, maxLevel, breakRewards, Map.of());
    }

    public JobDefinition(String id, String displayName, Material icon, boolean enabled, int maxLevel,
                         Map<Material, ActivityReward> breakRewards,
                         Map<ActivityType, Map<String, ActivityReward>> activityRewards) {
        this.id = Objects.requireNonNull(id);
        this.displayName = Objects.requireNonNull(displayName);
        this.icon = Objects.requireNonNull(icon);
        this.enabled = enabled;
        this.maxLevel = Math.max(1, maxLevel);

        EnumMap<Material, ActivityReward> breaks = new EnumMap<>(Material.class);
        if (breakRewards != null) breaks.putAll(breakRewards);
        this.breakRewards = Collections.unmodifiableMap(breaks);

        EnumMap<ActivityType, Map<String, ActivityReward>> activities = new EnumMap<>(ActivityType.class);
        if (activityRewards != null) {
            activityRewards.forEach((type, rewards) -> {
                if (type == null || type == ActivityType.BREAK || rewards == null || rewards.isEmpty()) return;
                Map<String, ActivityReward> normalized = new LinkedHashMap<>();
                rewards.forEach((key, reward) -> normalized.put(normalizeKey(key), Objects.requireNonNull(reward)));
                activities.put(type, Collections.unmodifiableMap(normalized));
            });
        }
        this.activityRewards = Collections.unmodifiableMap(activities);
    }

    public String id() { return id; }
    public String displayName() { return displayName; }
    public Material icon() { return icon; }
    public boolean enabled() { return enabled; }
    public int maxLevel() { return maxLevel; }
    public Map<Material, ActivityReward> breakRewards() { return breakRewards; }
    public ActivityReward breakReward(Material material) { return breakRewards.getOrDefault(material, ActivityReward.ZERO); }
    public boolean handlesBreaks() { return enabled && !breakRewards.isEmpty(); }

    public Map<ActivityType, Map<String, ActivityReward>> activityRewards() { return activityRewards; }

    public Set<String> activityKeys(ActivityType type) {
        if (type == ActivityType.BREAK) {
            return breakRewards.keySet().stream().map(Material::name).collect(java.util.stream.Collectors.toUnmodifiableSet());
        }
        Map<String, ActivityReward> rewards = activityRewards.get(type);
        return rewards == null ? Set.of() : rewards.keySet();
    }

    public boolean handles(ActivityType type) {
        if (!enabled) return false;
        if (type == ActivityType.BREAK) return !breakRewards.isEmpty();
        Map<String, ActivityReward> rewards = activityRewards.get(type);
        return rewards != null && !rewards.isEmpty();
    }

    public ActivityReward reward(ActivityType type, String key) {
        Objects.requireNonNull(type, "type");
        if (type == ActivityType.BREAK) {
            Material material = Material.matchMaterial(key == null ? "" : key);
            return material == null ? ActivityReward.ZERO : breakReward(material);
        }
        Map<String, ActivityReward> rewards = activityRewards.get(type);
        if (rewards == null || rewards.isEmpty()) return ActivityReward.ZERO;
        String normalized = normalizeKey(key);
        ActivityReward exact = rewards.get(normalized);
        return exact != null ? exact : rewards.getOrDefault("*", ActivityReward.ZERO);
    }

    private static String normalizeKey(String value) {
        if (value == null || value.isBlank()) return "*";
        return value.trim().toUpperCase(Locale.ROOT);
    }
}
