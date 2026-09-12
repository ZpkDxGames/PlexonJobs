package com.plexon.jobs.runtime;

import com.plexon.jobs.PlexonJobs;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Objects;

/** Always-registered low-frequency lifecycle warmup and compiled-state cleanup. */
public final class PlayerStateListener implements Listener {
    private final PlexonJobs plugin;

    public PlayerStateListener(PlexonJobs plugin) { this.plugin = Objects.requireNonNull(plugin); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        var id = event.getPlayer().getUniqueId();
        plugin.runtime().profiles().ensure(id);
        plugin.dailyPersistence().ensure(id);
        plugin.interest().refresh(id);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        var id = event.getPlayer().getUniqueId();
        plugin.interest().remove(id);
        if (plugin.activityListeners() != null) plugin.activityListeners().playerQuit(id);
    }
}
