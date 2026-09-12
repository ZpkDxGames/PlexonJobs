package com.plexon.jobs.runtime;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeActivityListenerTest {
    @Test
    void anvilRepairRequiresResultSlotSecondInputAndResult() {
        ItemStack secondInput = new ItemStack(Material.IRON_INGOT);
        ItemStack result = new ItemStack(Material.IRON_PICKAXE);

        assertTrue(NativeActivityListener.qualifiesAnvilResult(
                NativeActivityListener.ANVIL_RESULT_SLOT, secondInput, result));
        assertFalse(NativeActivityListener.qualifiesAnvilResult(0, secondInput, result));
        assertFalse(NativeActivityListener.qualifiesAnvilResult(
                NativeActivityListener.ANVIL_RESULT_SLOT, null, result));
        assertFalse(NativeActivityListener.qualifiesAnvilResult(
                NativeActivityListener.ANVIL_RESULT_SLOT, secondInput, null));
        assertFalse(NativeActivityListener.qualifiesAnvilResult(
                NativeActivityListener.ANVIL_RESULT_SLOT, new ItemStack(Material.AIR), result));
    }
}
