package com.plexon.jobs.economy;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PendingPayoutLedgerTest {
    @Test void failureMergesInflightBackExactlyOnce() {
        PendingPayoutLedger ledger = new PendingPayoutLedger();
        UUID id = UUID.randomUUID();
        ledger.add(id, 100);
        var snapshot = ledger.begin(id);
        assertEquals(100, snapshot.moneyMinor());
        ledger.add(id, 25);
        ledger.fail(snapshot);
        assertEquals(125, ledger.pending(id));
        var retry = ledger.begin(id);
        assertEquals(125, retry.moneyMinor());
        ledger.success(retry);
        assertEquals(0, ledger.pending(id));
    }

    @Test void inFlightSnapshotPreventsDuplicateBegin() {
        PendingPayoutLedger ledger = new PendingPayoutLedger();
        UUID id = UUID.randomUUID();
        ledger.add(id, 50);
        var first = ledger.begin(id);
        assertFalse(first.empty());
        assertTrue(ledger.begin(id).empty());
    }

    @Test void hundredThousandAccrualsRetainOneCoalescedPlayerEntry() {
        PendingPayoutLedger ledger = new PendingPayoutLedger();
        UUID id = UUID.randomUUID();
        for (int i = 0; i < 100_000; i++) ledger.add(id, 20);
        assertEquals(1, ledger.players());
        assertEquals(2_000_000L, ledger.pending(id));
        assertEquals(1, ledger.readyPlayers(10).size());
    }

    @Test void staleCompletionCannotAcknowledgeNewSnapshot() {
        PendingPayoutLedger ledger = new PendingPayoutLedger();
        UUID id = UUID.randomUUID();
        ledger.add(id, 50);
        var first = ledger.begin(id);
        ledger.success(first);
        ledger.add(id, 75);
        ledger.begin(id);
        assertThrows(IllegalStateException.class, () -> ledger.success(first));
    }
}
