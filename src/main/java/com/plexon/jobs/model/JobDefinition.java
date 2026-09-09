package com.plexon.jobs.model;

import org.bukkit.Material;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public final class JobDefinition {
    private final String id;
    private final String displayName;
    private final Material icon;
    private final boolean enabled;
    private final int maxLevel;
    private final Map<Material, ActivityReward> breakRewards;

    public JobDefinition(String id, String displayName, Material icon, boolean enabled, int maxLevel,
                         Map<Material, ActivityReward> breakRewards) {
        this.id = Objects.requireNonNull(id);
        this.displayName = Objects.requireNonNull(displayName);
        this.icon = Objects.requireNonNull(icon);
        this.enabled = enabled;
        this.maxLevel = Math.max(1, maxLevel);
        EnumMap<Material, ActivityReward> copy = new EnumMap<>(Material.class);
        if (breakRewards != null) copy.putAll(breakRewards);
        this.breakRewards = Collections.unmodifiableMap(copy);
    }

    public String id() { return id; }
    public String displayName() { return displayName; }
    public Material icon() { return icon; }
    public boolean enabled() { return enabled; }
    public int maxLevel() { return maxLevel; }
    public Map<Material, ActivityReward> breakRewards() { return breakRewards; }
    public ActivityReward breakReward(Material material) { return breakRewards.getOrDefault(material, ActivityReward.ZERO); }
    public boolean handlesBreaks() { return enabled && !breakRewards.isEmpty(); }
}
