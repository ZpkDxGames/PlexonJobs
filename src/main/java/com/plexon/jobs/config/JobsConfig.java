package com.plexon.jobs.config;

import com.plexon.jobs.runtime.RuntimeMode;

import java.time.ZoneId;
import java.util.Locale;
import java.util.Set;

public record JobsConfig(
        RuntimeMode mode,
        String coreApiRange,
        int defaultMaxJobs,
        boolean keepLevelOnLeave,
        PayoutMode payoutMode,
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
) {
    public enum PayoutMode { COALESCED, IMMEDIATE }

    public static PayoutMode payoutMode(String raw) {
        try { return PayoutMode.valueOf(raw.toUpperCase(Locale.ROOT)); }
        catch (Exception ignored) { return PayoutMode.COALESCED; }
    }
}
