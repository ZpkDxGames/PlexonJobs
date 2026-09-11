package com.plexon.jobs.config;

import com.plexon.jobs.runtime.RuntimeMode;

import java.time.ZoneId;
import java.util.Set;

public record JobsConfig(
        RuntimeMode mode,
        String coreApiRange,
        int defaultMaxJobs,
        boolean keepLevelOnLeave,
        int payoutFlushTicks,
        int maxCommitsPerTick,
        int retryLimit,
        int moneyScale,
        long defaultMoneyCapMinor,
        long defaultXpCap,
        int saveIntervalTicks,
        ZoneId resetZone,
        Set<String> allowedGameModes,
        Set<String> disabledWorlds
) {}
