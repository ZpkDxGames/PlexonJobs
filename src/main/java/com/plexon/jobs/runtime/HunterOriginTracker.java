package com.plexon.jobs.runtime;

import com.plexon.jobs.config.ActivityConfig;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Origin eligibility state for Hunter rewards. MEMORY is the performance-oriented default. */
public final class HunterOriginTracker {
    private final NamespacedKey persistentKey;
    private final Set<UUID> memoryEligible = new HashSet<>();

    public HunterOriginTracker(NamespacedKey persistentKey) {
        this.persistentKey = java.util.Objects.requireNonNull(persistentKey);
    }

    public void onSpawn(LivingEntity entity, String reason, ActivityConfig config) {
        String normalized = reason == null ? "" : reason.toUpperCase(Locale.ROOT);
        boolean allowed = config.hunterAllowedSpawnReasons().contains(normalized);
        UUID id = entity.getUniqueId();

        if (config.hunterOriginTracking() == ActivityConfig.HunterOriginTracking.MEMORY) {
            // Default mode deliberately does not touch entity PDC on spawn.
            memoryEligible.remove(id);
            if (allowed) memoryEligible.add(id);
            return;
        }

        memoryEligible.remove(id);
        entity.getPersistentDataContainer().remove(persistentKey);
        if (allowed) entity.getPersistentDataContainer().set(persistentKey, PersistentDataType.STRING, normalized);
    }

    public boolean consumeEligible(LivingEntity entity, ActivityConfig config) {
        UUID id = entity.getUniqueId();
        if (config.hunterOriginTracking() == ActivityConfig.HunterOriginTracking.MEMORY) {
            return memoryEligible.remove(id);
        }
        String reason = entity.getPersistentDataContainer().get(persistentKey, PersistentDataType.STRING);
        entity.getPersistentDataContainer().remove(persistentKey);
        return reason != null && config.hunterAllowedSpawnReasons().contains(reason.toUpperCase(Locale.ROOT));
    }

    public void remove(UUID entityId) { memoryEligible.remove(entityId); }
    public int tracked() { return memoryEligible.size(); }
    public void clear() { memoryEligible.clear(); }
}
