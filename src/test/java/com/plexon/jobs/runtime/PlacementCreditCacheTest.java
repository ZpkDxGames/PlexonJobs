package com.plexon.jobs.runtime;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PlacementCreditCacheTest {
    @Test void repeatedPositionIsSuppressedUntilTtlExpires() {
        PlacementCreditCache cache = new PlacementCreditCache(10, 128);
        UUID player = UUID.randomUUID();
        UUID world = UUID.randomUUID();
        long start = 1_000_000_000L;

        assertTrue(cache.credit(player, world, 1, 64, 1, start));
        assertFalse(cache.credit(player, world, 1, 64, 1, start + 5_000_000_000L));
        assertTrue(cache.credit(player, world, 1, 64, 1, start + 11_000_000_000L));
    }

    @Test void cacheRemainsBoundedPerPlayer() {
        PlacementCreditCache cache = new PlacementCreditCache(3600, 128);
        UUID player = UUID.randomUUID();
        UUID world = UUID.randomUUID();
        for (int i = 0; i < 300; i++) assertTrue(cache.credit(player, world, i, 64, 0, i + 1L));
        assertEquals(128, cache.tracked(player));
    }
}
