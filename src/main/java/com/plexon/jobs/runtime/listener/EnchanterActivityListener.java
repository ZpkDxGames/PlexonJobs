package com.plexon.jobs.runtime.listener;

import com.plexon.jobs.PlexonJobs;
import com.plexon.jobs.model.ActivityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;

import java.util.Objects;

public final class EnchanterActivityListener implements Listener {
    private final PlexonJobs plugin;
    public EnchanterActivityListener(PlexonJobs plugin) { this.plugin = Objects.requireNonNull(plugin); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent event) {
        plugin.grants().handle(event.getEnchanter(), ActivityType.ENCHANT, event.getItem().getType().name(),
                Math.max(1, event.getExpLevelCost()), "paper:enchant:" + event.getItem().getType().name());
    }
}
