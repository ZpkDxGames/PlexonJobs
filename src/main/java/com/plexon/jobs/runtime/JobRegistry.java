package com.plexon.jobs.runtime;

import com.plexon.jobs.model.JobDefinition;
import com.plexon.jobs.model.XpCurve;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class JobRegistry {
    private final Map<String, JobDefinition> byId;
    private final Map<Material, List<JobDefinition>> breakRoutes;
    private final Set<Material> breakMaterials;
    private final Map<String, XpCurve> curves;

    public JobRegistry(Collection<JobDefinition> definitions) {
        Map<String, JobDefinition> ids = new LinkedHashMap<>();
        Map<Material, List<JobDefinition>> routes = new java.util.EnumMap<>(Material.class);
        EnumSet<Material> materials = EnumSet.noneOf(Material.class);
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
        }
        this.byId = Collections.unmodifiableMap(ids);
        Map<Material, List<JobDefinition>> frozen = new java.util.EnumMap<>(Material.class);
        routes.forEach((k, v) -> frozen.put(k, List.copyOf(v)));
        this.breakRoutes = Collections.unmodifiableMap(frozen);
        this.breakMaterials = Collections.unmodifiableSet(materials);
        Map<String, XpCurve> curveMap = new LinkedHashMap<>();
        ids.values().forEach(definition -> curveMap.put(definition.id(), new XpCurve(definition.maxLevel(), 250, 1.35)));
        this.curves = Collections.unmodifiableMap(curveMap);
    }

    public Optional<JobDefinition> find(String id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(byId.get(id.toLowerCase(java.util.Locale.ROOT)));
    }

    public Collection<JobDefinition> definitions() { return byId.values(); }
    public List<JobDefinition> breakJobs(Material material) { return breakRoutes.getOrDefault(material, List.of()); }
    public Set<Material> breakMaterials() { return breakMaterials; }
    public XpCurve curve(String jobId) {
        XpCurve curve = curves.get(jobId);
        if (curve == null) throw new IllegalArgumentException("Unknown job id: " + jobId);
        return curve;
    }
}
