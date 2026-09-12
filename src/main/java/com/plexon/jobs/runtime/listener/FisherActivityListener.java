package com.plexon.jobs.runtime.listener;

import com.plexon.jobs.PlexonJobs;
import com.plexon.jobs.model.ActivityType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;

public final class FisherActivityListener implements Listener {
    private final PlexonJobs plugin;
    public FisherActivityListener(PlexonJobs plugin) { this.plugin = Objects.requireNonNull(plugin); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        Entity caught = event.getCaught();
        if (caught == null) return;
        String key;
        long units = 1L;
        if (caught instanceof Item item) {
            ItemStack stack = item.getItemStack();
            key = stack.getType().name();
            units = Math.max(1, stack.getAmount());
        } else key = caught.getType().name();
        plugin.grants().handle(event.getPlayer(), ActivityType.FISH, key, units, "paper:fish:" + caught.getUniqueId());
    }
}
