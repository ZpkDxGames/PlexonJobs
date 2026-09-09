package com.plexon.jobs.runtime;

import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DailyLimitServiceTest {
    @Test void clampsAtExactRemainingBoundary() {
        DailyLimitService limits = new DailyLimitService(ZoneId.of("UTC"), 100, 1000);
        UUID id = UUID.randomUUID();
        limits.commit(id, "miner", 70, 100);
        var clamped = limits.clamp(id, "miner", 100, 50);
        assertEquals(30, clamped.moneyMinor());
        assertEquals(50, clamped.xp());
        assertTrue(clamped.capped());
    }
}
