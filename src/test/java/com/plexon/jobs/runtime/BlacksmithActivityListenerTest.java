package com.plexon.jobs.runtime;

import com.plexon.jobs.runtime.listener.BlacksmithActivityListener;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlacksmithActivityListenerTest {
    @Test
    void anvilRepairRequiresResultSlotSecondInputAndResult() {
        assertTrue(BlacksmithActivityListener.qualifiesAnvilResult(
                BlacksmithActivityListener.ANVIL_RESULT_SLOT, true, true));
        assertFalse(BlacksmithActivityListener.qualifiesAnvilResult(0, true, true));
        assertFalse(BlacksmithActivityListener.qualifiesAnvilResult(
                BlacksmithActivityListener.ANVIL_RESULT_SLOT, false, true));
        assertFalse(BlacksmithActivityListener.qualifiesAnvilResult(
                BlacksmithActivityListener.ANVIL_RESULT_SLOT, true, false));
    }
}
