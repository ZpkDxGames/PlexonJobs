package com.plexon.jobs.runtime;

import com.plexon.jobs.PlexonJobs;
import com.plexon.jobs.model.ActivityType;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/** Periodic Explorer sampler; deliberately avoids PlayerMoveEvent and stores the finite discovery set in player PDC. */
public final class ExplorerDiscoveryService {
    private static final int MAX_DISCOVERIES = 512;
    private final PlexonJobs plugin;
    private final ActivityGrantService grants;
    private final NamespacedKey discoveriesKey;

    public ExplorerDiscoveryService(PlexonJobs plugin, ActivityGrantService grants) {
        this.plugin = plugin;
        this.grants = grants;
        this.discoveriesKey = new NamespacedKey(plugin, "explorer_discoveries");
    }

    public void sampleOnline() {
        if (!plugin.runtime().registry().handles(ActivityType.EXPLORE)) return;
        for (Player player : plugin.getServer().getOnlinePlayers()) sample(player);
    }

    public void sample(Player player) {
        String biome = player.getLocation().getBlock().getBiome().toString().toUpperCase(Locale.ROOT);
        String discovery = player.getWorld().getEnvironment().name() + ":" + biome;
        Set<String> known = read(player);
        if (known.contains(discovery)) return;
        if (!grants.hasJoinedRoute(player.getUniqueId(), ActivityType.EXPLORE, discovery)) return;
        ActivityGrantService.Outcome outcome = grants.handle(player, ActivityType.EXPLORE, discovery, 1,
                "paper:explore:" + discovery);
        if (!outcome.matchedMembership()) return;
        known.add(discovery);
        if (known.size() > MAX_DISCOVERIES) return;
        player.getPersistentDataContainer().set(discoveriesKey, PersistentDataType.STRING, String.join("\n", known));
    }

    private Set<String> read(Player player) {
        String raw = player.getPersistentDataContainer().get(discoveriesKey, PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) return new LinkedHashSet<>();
        LinkedHashSet<String> set = new LinkedHashSet<>(Arrays.asList(raw.split("\\n")));
        set.removeIf(String::isBlank);
        return set;
    }
}
