package com.plexon.jobs.runtime;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class DailyLimitService {
    private final ZoneId zoneId;
    private final long defaultMoneyCap;
    private final long defaultXpCap;
    private LocalDate day;
    private final Map<Key, Counter> counters = new HashMap<>();

    public DailyLimitService(ZoneId zoneId, long defaultMoneyCap, long defaultXpCap) {
        this.zoneId = Objects.requireNonNull(zoneId);
        this.defaultMoneyCap = Math.max(0, defaultMoneyCap);
        this.defaultXpCap = Math.max(0, defaultXpCap);
        this.day = LocalDate.now(zoneId);
    }

    public synchronized Clamped clamp(UUID playerId, String jobId, long moneyMinor, long xp) {
        resetIfNeeded();
        Key key = new Key(playerId, jobId);
        Counter current = counters.computeIfAbsent(key, ignored -> new Counter());
        long moneyRemaining = defaultMoneyCap <= 0 ? Long.MAX_VALUE : Math.max(0, defaultMoneyCap - current.money);
        long xpRemaining = defaultXpCap <= 0 ? Long.MAX_VALUE : Math.max(0, defaultXpCap - current.xp);
        long allowedMoney = Math.min(Math.max(0, moneyMinor), moneyRemaining);
        long allowedXp = Math.min(Math.max(0, xp), xpRemaining);
        return new Clamped(allowedMoney, allowedXp, allowedMoney != moneyMinor || allowedXp != xp);
    }

    public synchronized void commit(UUID playerId, String jobId, long moneyMinor, long xp) {
        resetIfNeeded();
        Counter current = counters.computeIfAbsent(new Key(playerId, jobId), ignored -> new Counter());
        current.money = Math.addExact(current.money, Math.max(0, moneyMinor));
        current.xp = Math.addExact(current.xp, Math.max(0, xp));
    }

    public synchronized CounterView view(UUID playerId, String jobId) {
        resetIfNeeded();
        Counter c = counters.get(new Key(playerId, jobId));
        return c == null ? new CounterView(0, 0) : new CounterView(c.money, c.xp);
    }

    public synchronized LocalDate day() { resetIfNeeded(); return day; }

    private void resetIfNeeded() {
        LocalDate now = LocalDate.now(zoneId);
        if (!now.equals(day)) {
            counters.clear();
            day = now;
        }
    }

    private record Key(UUID playerId, String jobId) {}
    private static final class Counter { long money; long xp; }
    public record CounterView(long moneyMinor, long xp) {}
    public record Clamped(long moneyMinor, long xp, boolean capped) {}
}
