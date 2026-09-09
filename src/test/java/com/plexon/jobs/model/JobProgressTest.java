package com.plexon.jobs.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JobProgressTest {
    @Test void levelUpDeltaCanCrossMultipleLevels() {
        XpCurve curve = new XpCurve(10, 100, 1.0);
        JobProgress progress = new JobProgress(0, 1, true);
        JobProgress.ProgressDelta delta = progress.addXp(650, curve);
        assertTrue(delta.leveledUp());
        assertEquals(4, delta.newLevel());
        assertEquals(650, delta.totalXp());
    }
}
