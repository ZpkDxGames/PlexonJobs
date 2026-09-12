package com.plexon.jobs.runtime.listener;

import com.plexon.jobs.PlexonJobs;
import com.plexon.jobs.model.ActivityType;
import com.plexon.jobs.runtime.BrewerAttributionTracker;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;

public final class BrewerActivityListener implements Listener {
    private final PlexonJobs plugin;
    private final BrewerAttributionTracker attribution;

    public BrewerActivityListener(PlexonJobs plugin) {
        this.plugin = Objects.requireNonNull(plugin);
        this.attribution = new BrewerAttributionTracker(plugin.runtime().config().activity().brewerAttributionSeconds());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteraction(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getView().getTopInventory().getType() != InventoryType.BREWING) return;
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;
        attribution.touch(event.getView().getTopInventory().getLocation(), player.getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBrew(BrewEvent event) {
        var playerId = attribution.consume(event.getBlock().getLocation(), System.currentTimeMillis()).orElse(null);
        if (playerId == null) return;
        Player player = plugin.getServer().getPlayer(playerId);
        if (player == null) return;
        ItemStack ingredient = event.getContents().getIngredient();
        String key = ingredient == null || ingredient.getType().isAir() ? "*" : ingredient.getType().name();
        long resultCount = event.getResults().stream().filter(Objects::nonNull).filter(stack -> !stack.getType().isAir()).count();
        if (resultCount <= 0) return;
        plugin.grants().handle(player, ActivityType.BREW, key, resultCount,
                "paper:brew:" + event.getBlock().getWorld().getUID() + ":" + event.getBlock().getX() + ":" + event.getBlock().getY() + ":" + event.getBlock().getZ());
    }
}
