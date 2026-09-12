package com.plexon.jobs.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeActivityListenerTest {
    @Test
    void anvilRepairRequiresResultSlotSecondInputAndResult() {
        assertTrue(NativeActivityListener.qualifiesAnvilResult(
                NativeActivityListener.ANVIL_RESULT_SLOT, true, true));
        assertFalse(NativeActivityListener.qualifiesAnvilResult(0, true, true));
        assertFalse(NativeActivityListener.qualifiesAnvilResult(
                NativeActivityListener.ANVIL_RESULT_SLOT, false, true));
        assertFalse(NativeActivityListener.qualifiesAnvilResult(
                NativeActivityListener.ANVIL_RESULT_SLOT, true, false));
    }
}
