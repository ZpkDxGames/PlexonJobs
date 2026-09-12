package com.plexon.jobs.runtime.listener;

import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import com.plexon.jobs.PlexonJobs;
import com.plexon.jobs.model.ActivityType;
import com.plexon.jobs.runtime.HunterOriginTracker;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;

import java.util.Objects;

public final class HunterActivityListener implements Listener {
    private final PlexonJobs plugin;
    private final HunterOriginTracker origins;

    public HunterActivityListener(PlexonJobs plugin) {
        this.plugin = Objects.requireNonNull(plugin);
        this.origins = new HunterOriginTracker(new NamespacedKey(plugin, "hunter_spawn_reason"));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        origins.onSpawn(event.getEntity(), event.getSpawnReason().name(), plugin.runtime().config().activity());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(EntityDeathEvent event) {
        LivingEntity dead = event.getEntity();
        Player killer = dead.getKiller();
        if (killer == null) {
            origins.remove(dead.getUniqueId());
            return;
        }
        if (!origins.consumeEligible(dead, plugin.runtime().config().activity())) {
            plugin.metrics().eventSeen();
            plugin.metrics().rejectedOrigin();
            return;
        }
        plugin.grants().handle(killer, ActivityType.KILL, dead.getType().name(), 1,
                "paper:kill:" + dead.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRemove(EntityRemoveFromWorldEvent event) {
        origins.remove(event.getEntity().getUniqueId());
    }

    public void clear() { origins.clear(); }
    public int trackedOrigins() { return origins.tracked(); }
}
