package com.plexon.jobs.runtime.listener;

import com.plexon.jobs.PlexonJobs;
import com.plexon.jobs.model.ActivityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.SmithItemEvent;
import org.bukkit.event.player.PlayerItemMendEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;

public final class BlacksmithActivityListener implements Listener {
    public static final int ANVIL_SECOND_INPUT_SLOT = 1;
    public static final int ANVIL_RESULT_SLOT = 2;
    private final PlexonJobs plugin;

    public BlacksmithActivityListener(PlexonJobs plugin) { this.plugin = Objects.requireNonNull(plugin); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSmith(SmithItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        ItemStack result = event.getCurrentItem();
        if (result == null || result.getType().isAir()) return;
        plugin.grants().handle(player, ActivityType.REPAIR, result.getType().name(), 1,
                "paper:smith:" + result.getType().name());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAnvilResult(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getView().getTopInventory().getType() != InventoryType.ANVIL) return;
        ItemStack secondInput = event.getView().getTopInventory().getItem(ANVIL_SECOND_INPUT_SLOT);
        ItemStack result = event.getCurrentItem();
        boolean hasSecondInput = secondInput != null && !secondInput.getType().isAir();
        boolean hasResult = result != null && !result.getType().isAir();
        if (!qualifiesAnvilResult(event.getRawSlot(), hasSecondInput, hasResult)) return;
        plugin.grants().handle(player, ActivityType.REPAIR, result.getType().name(), 1,
                "paper:anvil:" + result.getType().name());
    }

    public static boolean qualifiesAnvilResult(int rawSlot, boolean hasSecondInput, boolean hasResult) {
        return rawSlot == ANVIL_RESULT_SLOT && hasSecondInput && hasResult;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMend(PlayerItemMendEvent event) {
        if (event.getRepairAmount() <= 0) return;
        plugin.grants().handle(event.getPlayer(), ActivityType.REPAIR, event.getItem().getType().name(),
                Math.max(1, event.getRepairAmount()), "paper:mend");
    }
}
