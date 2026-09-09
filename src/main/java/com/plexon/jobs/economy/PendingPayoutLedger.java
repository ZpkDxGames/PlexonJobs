package com.plexon.jobs.economy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class PendingPayoutLedger {
    private final Map<UUID, Entry> entries = new HashMap<>();

    public synchronized void add(UUID playerId, long moneyMinor) {
        if (moneyMinor <= 0) return;
        Entry entry = entries.computeIfAbsent(playerId, ignored -> new Entry());
        entry.pending = Math.addExact(entry.pending, moneyMinor);
        if (entry.oldestNanos == 0) entry.oldestNanos = System.nanoTime();
    }

    public synchronized Snapshot begin(UUID playerId) {
        Entry entry = entries.get(playerId);
        if (entry == null || entry.inFlight != 0 || entry.pending == 0) return new Snapshot(playerId, 0);
        entry.inFlight = entry.pending;
        entry.pending = 0;
        return new Snapshot(playerId, entry.inFlight);
    }

    public synchronized void success(Snapshot snapshot) {
        Entry entry = entries.get(snapshot.playerId());
        if (entry == null) return;
        if (entry.inFlight != snapshot.moneyMinor()) throw new IllegalStateException("stale payout snapshot");
        entry.inFlight = 0;
        if (entry.pending == 0) entries.remove(snapshot.playerId());
    }

    public synchronized void fail(Snapshot snapshot) {
        Entry entry = entries.computeIfAbsent(snapshot.playerId(), ignored -> new Entry());
        if (entry.inFlight != snapshot.moneyMinor()) throw new IllegalStateException("stale payout snapshot");
        entry.inFlight = 0;
        entry.pending = Math.addExact(entry.pending, snapshot.moneyMinor());
    }

    public synchronized long pending(UUID playerId) {
        Entry entry = entries.get(playerId);
        return entry == null ? 0 : Math.addExact(entry.pending, entry.inFlight);
    }

    public synchronized long totalPending() {
        long total = 0;
        for (Entry entry : entries.values()) total = Math.addExact(total, Math.addExact(entry.pending, entry.inFlight));
        return total;
    }

    public synchronized int players() { return entries.size(); }

    public synchronized List<UUID> readyPlayers(int limit) {
        return entries.entrySet().stream()
                .filter(e -> e.getValue().pending > 0 && e.getValue().inFlight == 0)
                .sorted(Comparator.comparingLong(e -> e.getValue().oldestNanos))
                .limit(Math.max(0, limit))
                .map(Map.Entry::getKey)
                .toList();
    }

    public synchronized long oldestAgeMillis() {
        long oldest = entries.values().stream().mapToLong(e -> e.oldestNanos).filter(v -> v > 0).min().orElse(0);
        return oldest == 0 ? 0 : Math.max(0, (System.nanoTime() - oldest) / 1_000_000L);
    }

    public record Snapshot(UUID playerId, long moneyMinor) {
        public boolean empty() { return moneyMinor <= 0; }
    }

    private static final class Entry {
        long pending;
        long inFlight;
        long oldestNanos;
    }
}
