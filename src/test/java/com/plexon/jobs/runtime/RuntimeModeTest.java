package com.plexon.jobs.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RuntimeModeTest {
    @Test void invalidModeFailsClosedInsteadOfSilentlyChangingSemantics() {
        assertThrows(IllegalArgumentException.class, () -> RuntimeMode.parse("typo-primary"));
    }

    @Test void missingModeDefaultsToShadow() {
        assertEquals(RuntimeMode.SHADOW, RuntimeMode.parse(null));
    }
}
