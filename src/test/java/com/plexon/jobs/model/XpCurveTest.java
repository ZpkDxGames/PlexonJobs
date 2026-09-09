package com.plexon.jobs.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class XpCurveTest {
    @Test void derivesLevelsFromCanonicalTotalXp() {
        XpCurve curve = new XpCurve(10, 100, 1.0);
        assertEquals(1, curve.levelForTotalXp(0));
        assertEquals(2, curve.levelForTotalXp(100));
        assertEquals(3, curve.levelForTotalXp(300));
        assertEquals(200, curve.xpToNextLevel(100));
    }

    @Test void capsAtMaximumLevel() {
        XpCurve curve = new XpCurve(3, 100, 1.0);
        assertEquals(3, curve.levelForTotalXp(Long.MAX_VALUE));
        assertEquals(0, curve.xpToNextLevel(Long.MAX_VALUE));
    }
}
