package com.plexon.jobs.runtime;

import org.bukkit.Location;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Attributes a completed brewing batch to the most recent player interaction with that stand. */
public final class BrewerAttributionTracker {
    private static final int MAX_STANDS = 8_192;
    private final long ttlMillis;
    private final LinkedHashMap<BlockKey, Attribution> stands = new LinkedHashMap<>(128, 0.75f, true);

    public BrewerAttributionTracker(int attributionSeconds) {
        this.ttlMillis = Math.max(5L, attributionSeconds) * 1_000L;
    }

    public synchronized void touch(Location location, UUID playerId, long nowMillis) {
        BlockKey key = key(location);
        if (key == null) return;
        stands.put(key, new Attribution(playerId, nowMillis));
        trim(nowMillis);
    }

    public synchronized Optional<UUID> consume(Location location, long nowMillis) {
        BlockKey key = key(location);
        if (key == null) return Optional.empty();
        Attribution attribution = stands.remove(key);
        if (attribution == null || nowMillis - attribution.atMillis() > ttlMillis) return Optional.empty();
        return Optional.of(attribution.playerId());
    }

    public synchronized int tracked() { return stands.size(); }

    private void trim(long nowMillis) {
        var iterator = stands.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<BlockKey, Attribution> entry = iterator.next();
            if (nowMillis - entry.getValue().atMillis() > ttlMillis || stands.size() > MAX_STANDS) iterator.remove();
            else if (stands.size() <= MAX_STANDS) break;
        }
    }

    private static BlockKey key(Location location) {
        if (location == null || location.getWorld() == null) return null;
        return new BlockKey(location.getWorld().getUID(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    private record BlockKey(UUID worldId, int x, int y, int z) {}
    private record Attribution(UUID playerId, long atMillis) {}
}
