package com.plexon.jobs.runtime.listener;

import com.plexon.jobs.PlexonJobs;
import com.plexon.jobs.model.ActivityType;
import io.papermc.paper.event.inventory.ItemCraftedEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;

public final class CrafterActivityListener implements Listener {
    private final PlexonJobs plugin;
    public CrafterActivityListener(PlexonJobs plugin) { this.plugin = Objects.requireNonNull(plugin); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCraft(ItemCraftedEvent event) {
        ItemStack crafted = event.getCraftedItem();
        if (crafted == null || crafted.getType().isAir()) return;
        plugin.grants().handle(event.getPlayer(), ActivityType.CRAFT, crafted.getType().name(), Math.max(1, crafted.getAmount()),
                "paper:craft:" + crafted.getType().name());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onFurnaceExtract(FurnaceExtractEvent event) {
        plugin.grants().handle(event.getPlayer(), ActivityType.SMELT, event.getItemType().name(), Math.max(1, event.getItemAmount()),
                "paper:smelt:" + event.getBlock().getWorld().getUID() + ":" + event.getBlock().getX() + ":" + event.getBlock().getY() + ":" + event.getBlock().getZ());
    }
}
