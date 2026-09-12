package com.plexon.jobs.config;

import java.util.Set;

public record ActivityConfig(
        Set<String> hunterAllowedSpawnReasons,
        int builderRepeatWindowSeconds,
        int builderMaxTrackedPositions,
        int brewerAttributionSeconds,
        int explorerSampleTicks
) {
    public ActivityConfig {
        hunterAllowedSpawnReasons = Set.copyOf(hunterAllowedSpawnReasons == null ? Set.of() : hunterAllowedSpawnReasons);
    }
}
