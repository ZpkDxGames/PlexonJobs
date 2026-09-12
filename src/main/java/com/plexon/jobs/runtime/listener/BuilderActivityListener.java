package com.plexon.jobs.runtime.listener;

import com.plexon.jobs.PlexonJobs;
import com.plexon.jobs.model.ActivityType;
import com.plexon.jobs.runtime.PlacementCreditCache;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.Objects;
import java.util.UUID;

public final class BuilderActivityListener implements Listener {
    private final PlexonJobs plugin;
    private final PlacementCreditCache credits;

    public BuilderActivityListener(PlexonJobs plugin) {
        this.plugin = Objects.requireNonNull(plugin);
        this.credits = new PlacementCreditCache(plugin.runtime().config().activity().builderRepeatWindowSeconds(),
                plugin.runtime().config().activity().builderMaxTrackedPositions());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!event.canBuild()) return;
        var block = event.getBlockPlaced();
        if (!credits.credit(event.getPlayer().getUniqueId(), block.getWorld().getUID(), block.getX(), block.getY(), block.getZ(), System.nanoTime())) return;
        plugin.grants().handle(event.getPlayer(), ActivityType.PLACE, block.getType().name(), 1,
                "paper:place:" + block.getX() + ":" + block.getY() + ":" + block.getZ());
    }

    public void removePlayer(UUID playerId) { credits.remove(playerId); }
}
