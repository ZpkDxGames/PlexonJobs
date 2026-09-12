package com.plexon.jobs.runtime;

import com.plexon.jobs.PlexonJobs;
import com.plexon.jobs.model.ActivityType;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/** Explorer sampler that exists only while at least one online Explorer participant exists. */
public final class ExplorerDiscoveryService implements AutoCloseable {
    private static final int MAX_DISCOVERIES = 512;
    private final PlexonJobs plugin;
    private final NamespacedKey discoveriesKey;
    private BukkitTask task;
    private int scheduledTicks;

    public ExplorerDiscoveryService(PlexonJobs plugin) {
        this.plugin = plugin;
        this.discoveriesKey = new NamespacedKey(plugin, "explorer_discoveries");
    }

    public void reconcile() {
        boolean needed = plugin.runtime().registry().handles(ActivityType.EXPLORE)
                && plugin.interest().participantCount(ActivityType.EXPLORE) > 0;
        int ticks = plugin.runtime().config().activity().explorerSampleTicks();
        if (!needed) {
            cancel();
            return;
        }
        if (task != null && scheduledTicks == ticks) return;
        cancel();
        scheduledTicks = ticks;
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::sampleParticipants, ticks, ticks);
    }

    public void sampleParticipants() {
        for (var playerId : plugin.interest().participants(ActivityType.EXPLORE)) {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player != null && player.isOnline()) sample(player);
        }
    }

    public void sample(Player player) {
        String biome = player.getLocation().getBlock().getBiome().toString().toUpperCase(Locale.ROOT);
        String discovery = player.getWorld().getEnvironment().name() + ":" + biome;
        Set<String> known = read(player);
        if (known.contains(discovery) || known.size() >= MAX_DISCOVERIES) return;
        ActivityGrantService.Outcome outcome = plugin.grants().handle(player, ActivityType.EXPLORE, discovery, 1,
                "paper:explore:" + discovery);
        if (!outcome.matchedMembership()) return;
        known.add(discovery);
        player.getPersistentDataContainer().set(discoveriesKey, PersistentDataType.STRING, String.join("\n", known));
    }

    public boolean active() { return task != null; }

    @Override public void close() { cancel(); }

    private void cancel() {
        if (task != null) task.cancel();
        task = null;
        scheduledTicks = 0;
    }

    private Set<String> read(Player player) {
        String raw = player.getPersistentDataContainer().get(discoveriesKey, PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) return new LinkedHashSet<>();
        LinkedHashSet<String> set = new LinkedHashSet<>(Arrays.asList(raw.split("\\n")));
        set.removeIf(String::isBlank);
        return set;
    }
}
