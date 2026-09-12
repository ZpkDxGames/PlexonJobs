package com.plexon.jobs.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RewardFeedbackWindowTest {
    @Test void sameJobRewardsAccumulateInsideWindow() {
        RewardFeedbackWindow window = new RewardFeedbackWindow();
        var first = window.add("miner", 5, 10, 1_000, 500);
        var second = window.add("miner", 7, 20, 1_200, 500);

        assertEquals(5, first.xp());
        assertEquals(12, second.xp());
        assertEquals(30, second.moneyMinor());
        assertEquals(1_700, second.expiresAtNanos());
        assertFalse(window.expired(1_699));
        assertTrue(window.expired(1_700));
    }

    @Test void changingJobResetsAccumulatedDeltas() {
        RewardFeedbackWindow window = new RewardFeedbackWindow();
        window.add("miner", 100, 200, 1_000, 500);
        var next = window.add("farmer", 4, 8, 1_100, 500);

        assertEquals("farmer", next.jobId());
        assertEquals(4, next.xp());
        assertEquals(8, next.moneyMinor());
    }

    @Test void expiredWindowResetsEvenForSameJob() {
        RewardFeedbackWindow window = new RewardFeedbackWindow();
        window.add("miner", 100, 200, 1_000, 100);
        var next = window.add("miner", 3, 6, 1_100, 100);

        assertEquals(3, next.xp());
        assertEquals(6, next.moneyMinor());
    }
}
