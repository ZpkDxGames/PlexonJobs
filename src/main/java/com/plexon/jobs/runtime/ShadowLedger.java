package com.plexon.jobs.runtime;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ShadowLedger {
    private final Map<Key, Totals> totals = new HashMap<>();

    public synchronized void add(UUID playerId, String jobId, long moneyMinor, long xp) {
        addAggregate(playerId, jobId, moneyMinor, xp, 1);
    }

    public synchronized void addAggregate(UUID playerId, String jobId, long moneyMinor, long xp, long events) {
        if (events <= 0) return;
        Key key = new Key(playerId, jobId);
        Totals current = totals.computeIfAbsent(key, ignored -> new Totals());
        current.moneyMinor = Math.addExact(current.moneyMinor, Math.max(0, moneyMinor));
        current.xp = Math.addExact(current.xp, Math.max(0, xp));
        current.events = Math.addExact(current.events, events);
    }

    public synchronized Map<Key, Snapshot> drain() {
        Map<Key, Snapshot> copy = new HashMap<>();
        totals.forEach((key, value) -> copy.put(key, new Snapshot(value.moneyMinor, value.xp, value.events)));
        totals.clear();
        return copy;
    }

    public synchronized int size() { return totals.size(); }

    public record Key(UUID playerId, String jobId) {}
    public record Snapshot(long moneyMinor, long xp, long events) {}
    private static final class Totals { long moneyMinor; long xp; long events; }
}
