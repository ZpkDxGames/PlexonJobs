package com.plexon.jobs.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class MoneyTest {
    @Test void usesFixedPointAuthoritativeUnits() {
        assertEquals(10, Money.toMinor(new BigDecimal("0.10"), 2));
        assertEquals(new BigDecimal("0.10"), Money.fromMinor(10, 2));
        assertEquals("0.10", Money.format(10, 2));
    }
}
