package com.plexon.jobs.runtime;

import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.util.Map;
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

    @Test void playerTotalIsMaintainedWithoutJobScan() {
        DailyLimitService limits = new DailyLimitService(ZoneId.of("UTC"), 0, 0);
        UUID id = UUID.randomUUID();
        limits.commit(id, "miner", 25, 3);
        limits.commit(id, "woodcutter", 40, 7);
        assertEquals(65, limits.total(id).moneyMinor());
        assertEquals(10, limits.total(id).xp());
        assertEquals(25, limits.view(id, "miner").moneyMinor());
    }

    @Test void restoredSnapshotContinuesFromPersistedCap() {
        DailyLimitService limits = new DailyLimitService(ZoneId.of("UTC"), 100, 1000);
        UUID id = UUID.randomUUID();
        assertTrue(limits.restore(id, limits.dayId(), Map.of(
                "miner", new DailyLimitService.CounterView(80, 700))));

        var clamped = limits.clamp(id, "miner", 50, 500);
        assertEquals(20, clamped.moneyMinor());
        assertEquals(300, clamped.xp());
        assertTrue(clamped.capped());
        assertEquals(80, limits.total(id).moneyMinor());
        assertFalse(limits.isDirty(id), "Hydration itself must not schedule a redundant write");
    }

    @Test void staleSaveCannotClearNewerDirtyRevision() {
        DailyLimitService limits = new DailyLimitService(ZoneId.of("UTC"), 0, 0);
        UUID id = UUID.randomUUID();
        limits.restore(id, limits.dayId(), Map.of());
        limits.commit(id, "miner", 10, 1);
        DailyLimitService.Snapshot older = limits.snapshot(id);
        limits.commit(id, "miner", 20, 2);

        limits.markPersisted(id, older.revision());
        assertTrue(limits.isDirty(id), "An older async completion must not clear newer mutations");
        DailyLimitService.Snapshot latest = limits.snapshot(id);
        limits.markPersisted(id, latest.revision());
        assertFalse(limits.isDirty(id));
        assertEquals(30, limits.view(id, "miner").moneyMinor());
    }
}
