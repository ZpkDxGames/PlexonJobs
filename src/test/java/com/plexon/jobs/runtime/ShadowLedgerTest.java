package com.plexon.jobs.runtime;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ShadowLedgerTest {
    @Test void coalescesManyEventsWithoutPerEventPersistenceShape() {
        ShadowLedger ledger = new ShadowLedger();
        UUID id = UUID.randomUUID();
        for (int i = 0; i < 10_000; i++) ledger.add(id, "miner", 20, 1);
        var batch = ledger.drain();
        assertEquals(1, batch.size());
        var total = batch.values().iterator().next();
        assertEquals(200_000, total.moneyMinor());
        assertEquals(10_000, total.xp());
        assertEquals(10_000, total.events());
        assertEquals(0, ledger.size());
    }
}
