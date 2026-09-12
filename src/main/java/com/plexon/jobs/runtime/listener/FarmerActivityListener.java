package com.plexon.jobs.runtime.listener;

import com.plexon.jobs.PlexonJobs;
import com.plexon.jobs.model.ActivityType;
import org.bukkit.block.data.Ageable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerHarvestBlockEvent;

import java.util.Objects;

public final class FarmerActivityListener implements Listener {
    private final PlexonJobs plugin;
    public FarmerActivityListener(PlexonJobs plugin) { this.plugin = Objects.requireNonNull(plugin); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMatureCropBreak(BlockBreakEvent event) {
        if (!(event.getBlock().getBlockData() instanceof Ageable ageable) || ageable.getAge() < ageable.getMaximumAge()) return;
        plugin.grants().handle(event.getPlayer(), ActivityType.FARM, event.getBlock().getType().name(), 1,
                "paper:farm-break:" + pos(event.getBlock().getX(), event.getBlock().getY(), event.getBlock().getZ()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHarvest(PlayerHarvestBlockEvent event) {
        plugin.grants().handle(event.getPlayer(), ActivityType.FARM, event.getHarvestedBlock().getType().name(), 1,
                "paper:harvest:" + pos(event.getHarvestedBlock().getX(), event.getHarvestedBlock().getY(), event.getHarvestedBlock().getZ()));
    }

    private static String pos(int x, int y, int z) { return x + ":" + y + ":" + z; }
}
