package com.plexon.jobs.runtime;

import com.plexon.jobs.economy.PendingPayoutLedger;
import com.plexon.jobs.model.JobProgress;
import com.plexon.jobs.model.XpCurve;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SyntheticWorkloadTest {
    @Test void hundredThousandEligibleActionsKeepExactTotalsAndBoundedLedgerShape() {
        UUID playerId = UUID.randomUUID();
        PendingPayoutLedger ledger = new PendingPayoutLedger();
        JobProgress progress = new JobProgress(0, 1, true);
        XpCurve curve = new XpCurve(500, 100, 1.0);

        for (int i = 0; i < 100_000; i++) {
            progress.addXp(3, curve);
            ledger.add(playerId, 20);
        }

        assertEquals(300_000L, progress.totalXp());
        assertEquals(2_000_000L, ledger.pending(playerId));
        assertEquals(1, ledger.players(), "coalescing must not retain one queue item per event");
        var batch = ledger.begin(playerId);
        assertEquals(2_000_000L, batch.moneyMinor());
        assertTrue(ledger.begin(playerId).empty(), "only one payout may be in-flight per player");
    }
}
