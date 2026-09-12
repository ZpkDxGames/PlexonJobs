package com.plexon.jobs.runtime;

import com.plexon.jobs.model.ActivityType;
import com.plexon.jobs.model.JobDefinition;
import com.plexon.jobs.model.XpCurve;
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
import java.util.Optional;
import java.util.Set;

public final class JobRegistry {
    private final Map<String, JobDefinition> byId;
    private final Map<Material, List<JobDefinition>> breakRoutes;
    private final Set<Material> breakMaterials;
    private final Map<ActivityType, Map<String, List<JobDefinition>>> activityRoutes;
    private final Map<String, XpCurve> curves;

    public JobRegistry(Collection<JobDefinition> definitions) {
        Map<String, JobDefinition> ids = new LinkedHashMap<>();
        Map<Material, List<JobDefinition>> routes = new EnumMap<>(Material.class);
        EnumSet<Material> materials = EnumSet.noneOf(Material.class);
        EnumMap<ActivityType, Map<String, List<JobDefinition>>> genericRoutes = new EnumMap<>(ActivityType.class);

        for (JobDefinition definition : definitions) {
            if (ids.putIfAbsent(definition.id(), definition) != null) {
                throw new IllegalArgumentException("Duplicate job id: " + definition.id());
            }
            if (definition.handlesBreaks()) {
                for (Material material : definition.breakRewards().keySet()) {
                    materials.add(material);
                    routes.computeIfAbsent(material, ignored -> new ArrayList<>()).add(definition);
                }
            }
            for (ActivityType type : ActivityType.values()) {
                if (type == ActivityType.BREAK || !definition.handles(type)) continue;
                Map<String, List<JobDefinition>> typeRoutes = genericRoutes.computeIfAbsent(type, ignored -> new LinkedHashMap<>());
                for (String key : definition.activityKeys(type)) {
                    typeRoutes.computeIfAbsent(normalizeKey(key), ignored -> new ArrayList<>()).add(definition);
                }
            }
        }

        this.byId = Collections.unmodifiableMap(ids);
        Map<Material, List<JobDefinition>> frozenBreaks = new EnumMap<>(Material.class);
        routes.forEach((k, v) -> frozenBreaks.put(k, List.copyOf(v)));
        this.breakRoutes = Collections.unmodifiableMap(frozenBreaks);
        this.breakMaterials = Collections.unmodifiableSet(materials);

        EnumMap<ActivityType, Map<String, List<JobDefinition>>> frozenActivities = new EnumMap<>(ActivityType.class);
        genericRoutes.forEach((type, keyed) -> {
            Map<String, List<JobDefinition>> frozen = new LinkedHashMap<>();
            keyed.forEach((key, value) -> frozen.put(key, List.copyOf(value)));
            frozenActivities.put(type, Collections.unmodifiableMap(frozen));
        });
        this.activityRoutes = Collections.unmodifiableMap(frozenActivities);

        Map<String, XpCurve> curveMap = new LinkedHashMap<>();
        ids.values().forEach(definition -> curveMap.put(definition.id(), new XpCurve(definition.maxLevel(), 250, 1.35)));
        this.curves = Collections.unmodifiableMap(curveMap);
    }

    public Optional<JobDefinition> find(String id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(byId.get(id.toLowerCase(Locale.ROOT)));
    }

    public Collection<JobDefinition> definitions() { return byId.values(); }
    public List<JobDefinition> breakJobs(Material material) { return breakRoutes.getOrDefault(material, List.of()); }
    public Set<Material> breakMaterials() { return breakMaterials; }

    public List<JobDefinition> activityJobs(ActivityType type, String key) {
        if (type == ActivityType.BREAK) {
            Material material = Material.matchMaterial(key == null ? "" : key);
            return material == null ? List.of() : breakJobs(material);
        }
        Map<String, List<JobDefinition>> routes = activityRoutes.get(type);
        if (routes == null || routes.isEmpty()) return List.of();
        String normalized = normalizeKey(key);
        List<JobDefinition> exact = routes.getOrDefault(normalized, List.of());
        List<JobDefinition> wildcard = routes.getOrDefault("*", List.of());
        if (exact.isEmpty()) return wildcard;
        if (wildcard.isEmpty()) return exact;
        LinkedHashSet<JobDefinition> combined = new LinkedHashSet<>(exact);
        combined.addAll(wildcard);
        return List.copyOf(combined);
    }

    public boolean handles(ActivityType type) {
        if (type == ActivityType.BREAK) return !breakMaterials.isEmpty();
        Map<String, List<JobDefinition>> routes = activityRoutes.get(type);
        return routes != null && !routes.isEmpty();
    }

    public XpCurve curve(String jobId) {
        XpCurve curve = curves.get(jobId);
        if (curve == null) throw new IllegalArgumentException("Unknown job id: " + jobId);
        return curve;
    }

    private static String normalizeKey(String key) {
        if (key == null || key.isBlank()) return "*";
        return key.trim().toUpperCase(Locale.ROOT);
    }
}
