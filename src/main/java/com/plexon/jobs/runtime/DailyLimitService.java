package com.plexon.jobs.runtime;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Main-thread daily-cap state. Database orchestration lives in DailyLimitPersistence so the work
 * event path remains memory-only.
 */
public final class DailyLimitService {
    private final ZoneId zoneId;
    private final long defaultMoneyCap;
    private final long defaultXpCap;
    private LocalDate day;
    private final Map<Key, Counter> counters = new HashMap<>();
    private final Map<UUID, Counter> playerTotals = new HashMap<>();
    private final Set<UUID> dirty = new java.util.HashSet<>();
    private final Map<UUID, Long> revisions = new HashMap<>();

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
        long money = Math.max(0, moneyMinor);
        long experience = Math.max(0, xp);
        Counter current = counters.computeIfAbsent(new Key(playerId, jobId), ignored -> new Counter());
        current.money = Math.addExact(current.money, money);
        current.xp = Math.addExact(current.xp, experience);
        Counter total = playerTotals.computeIfAbsent(playerId, ignored -> new Counter());
        total.money = Math.addExact(total.money, money);
        total.xp = Math.addExact(total.xp, experience);
        revisions.merge(playerId, 1L, Math::addExact);
        dirty.add(playerId);
    }

    public synchronized CounterView view(UUID playerId, String jobId) {
        resetIfNeeded();
        Counter c = counters.get(new Key(playerId, jobId));
        return c == null ? new CounterView(0, 0) : new CounterView(c.money, c.xp);
    }

    public synchronized CounterView total(UUID playerId) {
        resetIfNeeded();
        Counter c = playerTotals.get(playerId);
        return c == null ? new CounterView(0, 0) : new CounterView(c.money, c.xp);
    }

    public synchronized LocalDate day() {
        resetIfNeeded();
        return day;
    }

    public synchronized long dayId() {
        resetIfNeeded();
        return day.toEpochDay();
    }

    /** Restores an authoritative snapshot only if it still belongs to the current configured day. */
    public synchronized boolean restore(UUID playerId, long dayId, Map<String, CounterView> restored) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(restored, "restored");
        resetIfNeeded();
        if (day.toEpochDay() != dayId) return false;

        counters.keySet().removeIf(key -> key.playerId().equals(playerId));
        Counter total = new Counter();
        for (Map.Entry<String, CounterView> entry : restored.entrySet()) {
            CounterView value = Objects.requireNonNull(entry.getValue(), "counter");
            Counter counter = new Counter();
            counter.money = Math.max(0, value.moneyMinor());
            counter.xp = Math.max(0, value.xp());
            counters.put(new Key(playerId, Objects.requireNonNull(entry.getKey(), "jobId")), counter);
            total.money = Math.addExact(total.money, counter.money);
            total.xp = Math.addExact(total.xp, counter.xp);
        }
        if (total.money == 0 && total.xp == 0) playerTotals.remove(playerId);
        else playerTotals.put(playerId, total);
        dirty.remove(playerId);
        revisions.put(playerId, 0L);
        return true;
    }

    public synchronized Snapshot snapshot(UUID playerId) {
        resetIfNeeded();
        Map<String, CounterView> rows = new LinkedHashMap<>();
        counters.forEach((key, value) -> {
            if (key.playerId().equals(playerId)) rows.put(key.jobId(), new CounterView(value.money, value.xp));
        });
        return new Snapshot(day.toEpochDay(), revisions.getOrDefault(playerId, 0L), Map.copyOf(rows));
    }

    public synchronized Set<UUID> dirtyPlayers() {
        resetIfNeeded();
        return Set.copyOf(dirty);
    }

    public synchronized boolean isDirty(UUID playerId) {
        resetIfNeeded();
        return dirty.contains(playerId);
    }

    public synchronized void markPersisted(UUID playerId, long revision) {
        resetIfNeeded();
        if (revisions.getOrDefault(playerId, 0L) == revision) dirty.remove(playerId);
    }

    public synchronized void remove(UUID playerId) {
        resetIfNeeded();
        if (dirty.contains(playerId)) throw new IllegalStateException("Cannot remove dirty daily state for " + playerId);
        counters.keySet().removeIf(key -> key.playerId().equals(playerId));
        playerTotals.remove(playerId);
        revisions.remove(playerId);
    }

    private void resetIfNeeded() {
        LocalDate now = LocalDate.now(zoneId);
        if (!now.equals(day)) {
            counters.clear();
            playerTotals.clear();
            dirty.clear();
            revisions.clear();
            day = now;
        }
    }

    private record Key(UUID playerId, String jobId) {}
    private static final class Counter { long money; long xp; }
    public record CounterView(long moneyMinor, long xp) {}
    public record Clamped(long moneyMinor, long xp, boolean capped) {}
    public record Snapshot(long dayId, long revision, Map<String, CounterView> rows) {}
}
