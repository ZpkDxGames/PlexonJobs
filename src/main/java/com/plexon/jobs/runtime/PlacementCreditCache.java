package com.plexon.jobs.runtime;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Bounded in-memory suppression for rapid same-position Builder place/break farming. */
public final class PlacementCreditCache {
    private final long ttlNanos;
    private final int maxPerPlayer;
    private final Map<UUID, LinkedHashMap<PositionKey, Long>> recent = new java.util.HashMap<>();

    public PlacementCreditCache(int repeatWindowSeconds, int maxPerPlayer) {
        this.ttlNanos = Math.max(1L, repeatWindowSeconds) * 1_000_000_000L;
        this.maxPerPlayer = Math.max(128, maxPerPlayer);
    }

    public synchronized boolean credit(UUID playerId, UUID worldId, int x, int y, int z, long nowNanos) {
        LinkedHashMap<PositionKey, Long> positions = recent.computeIfAbsent(playerId,
                ignored -> new LinkedHashMap<>(128, 0.75f, true));
        purgeExpired(positions, nowNanos);
        PositionKey key = new PositionKey(worldId, x, y, z);
        Long previous = positions.get(key);
        if (previous != null && nowNanos - previous < ttlNanos) return false;
        positions.put(key, nowNanos);
        while (positions.size() > maxPerPlayer) {
            Iterator<PositionKey> iterator = positions.keySet().iterator();
            if (!iterator.hasNext()) break;
            iterator.next();
            iterator.remove();
        }
        return true;
    }

    public synchronized void remove(UUID playerId) {
        recent.remove(playerId);
    }

    public synchronized int tracked(UUID playerId) {
        LinkedHashMap<PositionKey, Long> positions = recent.get(playerId);
        return positions == null ? 0 : positions.size();
    }

    private void purgeExpired(LinkedHashMap<PositionKey, Long> positions, long nowNanos) {
        Iterator<Map.Entry<PositionKey, Long>> iterator = positions.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<PositionKey, Long> entry = iterator.next();
            if (nowNanos - entry.getValue() >= ttlNanos) iterator.remove();
        }
    }

    private record PositionKey(UUID worldId, int x, int y, int z) {}
}
