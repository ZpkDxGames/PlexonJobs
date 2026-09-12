package com.plexon.jobs.runtime;

import com.plexon.jobs.PlexonJobs;
import com.plexon.jobs.model.ActivityType;
import com.plexon.jobs.runtime.listener.BlacksmithActivityListener;
import com.plexon.jobs.runtime.listener.BrewerActivityListener;
import com.plexon.jobs.runtime.listener.BuilderActivityListener;
import com.plexon.jobs.runtime.listener.CrafterActivityListener;
import com.plexon.jobs.runtime.listener.EnchanterActivityListener;
import com.plexon.jobs.runtime.listener.FarmerActivityListener;
import com.plexon.jobs.runtime.listener.FisherActivityListener;
import com.plexon.jobs.runtime.listener.HunterActivityListener;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Primary-thread coordinator that registers a family listener only while its online subscriber count is non-zero. */
public final class ActivityListenerCoordinator implements AutoCloseable {
    private enum Family { FARMER, HUNTER, FISHER, BUILDER, CRAFTER, BLACKSMITH, BREWER, ENCHANTER }

    private final PlexonJobs plugin;
    private final EnumMap<Family, Listener> listeners = new EnumMap<>(Family.class);
    private final EnumMap<Family, Set<ActivityType>> types = new EnumMap<>(Family.class);
    private final EnumSet<Family> active = EnumSet.noneOf(Family.class);

    public ActivityListenerCoordinator(PlexonJobs plugin) {
        this.plugin = java.util.Objects.requireNonNull(plugin);
        listeners.put(Family.FARMER, new FarmerActivityListener(plugin));
        listeners.put(Family.HUNTER, new HunterActivityListener(plugin));
        listeners.put(Family.FISHER, new FisherActivityListener(plugin));
        listeners.put(Family.BUILDER, new BuilderActivityListener(plugin));
        listeners.put(Family.CRAFTER, new CrafterActivityListener(plugin));
        listeners.put(Family.BLACKSMITH, new BlacksmithActivityListener(plugin));
        listeners.put(Family.BREWER, new BrewerActivityListener(plugin));
        listeners.put(Family.ENCHANTER, new EnchanterActivityListener(plugin));
        types.put(Family.FARMER, EnumSet.of(ActivityType.FARM));
        types.put(Family.HUNTER, EnumSet.of(ActivityType.KILL));
        types.put(Family.FISHER, EnumSet.of(ActivityType.FISH));
        types.put(Family.BUILDER, EnumSet.of(ActivityType.PLACE));
        types.put(Family.CRAFTER, EnumSet.of(ActivityType.CRAFT, ActivityType.SMELT));
        types.put(Family.BLACKSMITH, EnumSet.of(ActivityType.REPAIR));
        types.put(Family.BREWER, EnumSet.of(ActivityType.BREW));
        types.put(Family.ENCHANTER, EnumSet.of(ActivityType.ENCHANT));
    }

    public void reconcile() {
        requirePrimary();
        boolean dynamic = plugin.runtime().config().performance().dynamicListeners();
        for (Family family : Family.values()) {
            boolean configured = types.get(family).stream().anyMatch(plugin.runtime().registry()::handles);
            boolean subscribed = types.get(family).stream().anyMatch(plugin.interest()::hasGlobalInterest);
            boolean required = configured && (!dynamic || subscribed);
            if (required && active.add(family)) {
                Bukkit.getPluginManager().registerEvents(listeners.get(family), plugin);
            } else if (!required && active.remove(family)) {
                HandlerList.unregisterAll(listeners.get(family));
                if (listeners.get(family) instanceof HunterActivityListener hunter) hunter.clear();
            }
            boolean isActive = active.contains(family);
            for (ActivityType type : types.get(family)) plugin.metrics().listenerActive(type, isActive);
        }
    }

    public boolean active(ActivityType type) {
        for (Map.Entry<Family, Set<ActivityType>> entry : types.entrySet()) {
            if (entry.getValue().contains(type)) return active.contains(entry.getKey());
        }
        return false;
    }

    public void playerQuit(UUID playerId) {
        Listener listener = listeners.get(Family.BUILDER);
        if (listener instanceof BuilderActivityListener builder) builder.removePlayer(playerId);
    }

    @Override
    public void close() {
        requirePrimary();
        for (Family family : EnumSet.copyOf(active)) {
            HandlerList.unregisterAll(listeners.get(family));
            for (ActivityType type : types.get(family)) plugin.metrics().listenerActive(type, false);
        }
        active.clear();
        Listener hunter = listeners.get(Family.HUNTER);
        if (hunter instanceof HunterActivityListener tracked) tracked.clear();
    }

    private static void requirePrimary() {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Activity listener topology must change on the primary thread");
    }
}
