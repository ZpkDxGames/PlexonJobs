package com.plexon.jobs.config;

import java.util.Locale;
import java.util.Set;

public record ActivityConfig(
        Set<String> hunterAllowedSpawnReasons,
        HunterOriginTracking hunterOriginTracking,
        int builderRepeatWindowSeconds,
        int builderMaxTrackedPositions,
        int brewerAttributionSeconds,
        int explorerSampleTicks
) {
    public ActivityConfig {
        hunterAllowedSpawnReasons = Set.copyOf(hunterAllowedSpawnReasons == null ? Set.of() : hunterAllowedSpawnReasons);
        hunterOriginTracking = hunterOriginTracking == null ? HunterOriginTracking.MEMORY : hunterOriginTracking;
    }

    public enum HunterOriginTracking {
        MEMORY,
        PERSISTENT_PDC;

        public static HunterOriginTracking parse(String value) {
            try {
                return valueOf(value == null ? "MEMORY" : value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("activity.hunter.origin-tracking must be MEMORY or PERSISTENT_PDC", ex);
            }
        }
    }
}
