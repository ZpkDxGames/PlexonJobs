package com.plexon.jobs.runtime;

import com.plexon.jobs.model.PlayerJobsProfile;
import com.plexon.jobs.storage.JobsDatabase;
import com.zpkdxgames.plexoncore.api.PlexonCoreAPI;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ProfileManager {
    private final PlexonCoreAPI core;
    private final JobsDatabase database;
    private final Logger logger;
    private final Map<UUID, PlayerJobsProfile> profiles = new ConcurrentHashMap<>();
    private final Set<UUID> dirty = ConcurrentHashMap.newKeySet();
    private final Set<UUID> loading = ConcurrentHashMap.newKeySet();

    public ProfileManager(PlexonCoreAPI core, JobsDatabase database, Logger logger) {
        this.core = core;
        this.database = database;
        this.logger = logger;
    }

    public PlayerJobsProfile get(UUID playerId) { return profiles.get(playerId); }

    public PlayerJobsProfile ensure(UUID playerId) {
        PlayerJobsProfile existing = profiles.get(playerId);
        if (existing != null) return existing;
        PlayerJobsProfile loadingProfile = new PlayerJobsProfile(playerId, PlayerJobsProfile.State.LOADING);
        PlayerJobsProfile previous = profiles.putIfAbsent(playerId, loadingProfile);
        if (previous != null) return previous;
        requestLoad(playerId);
        return loadingProfile;
    }

    public void markDirty(UUID playerId) {
        dirty.add(playerId);
        PlayerJobsProfile profile = profiles.get(playerId);
        if (profile != null) profile.touch();
    }

    public void flushDirty() {
        for (UUID playerId : Set.copyOf(dirty)) saveAsync(playerId);
    }

    public void sweepOnline(Collection<UUID> online) {
        Set<UUID> onlineSet = Set.copyOf(online);
        for (UUID playerId : onlineSet) ensure(playerId);
        for (UUID playerId : Set.copyOf(profiles.keySet())) {
            if (!onlineSet.contains(playerId)) {
                saveAsync(playerId);
                if (!dirty.contains(playerId) && !loading.contains(playerId)) profiles.remove(playerId);
            }
        }
    }

    public void saveAllBlocking() {
        for (UUID playerId : Set.copyOf(profiles.keySet())) {
            PlayerJobsProfile profile = profiles.get(playerId);
            if (profile == null || profile.state() != PlayerJobsProfile.State.READY) continue;
            PlayerJobsProfile.Snapshot snapshot = profile.snapshot();
            OfflinePlayer player = Bukkit.getOfflinePlayer(playerId);
            try {
                database.save(snapshot, player.getName());
                if (profile.revision() == snapshot.revision()) dirty.remove(playerId);
            } catch (RuntimeException ex) {
                logger.log(Level.SEVERE, "Failed to persist profile during shutdown: " + playerId, ex);
            }
        }
    }

    public int onlineCached() { return profiles.size(); }
    public long loadingCount() { return profiles.values().stream().filter(p -> p.state() == PlayerJobsProfile.State.LOADING).count(); }
    public int dirtyCount() { return dirty.size(); }

    private void requestLoad(UUID playerId) {
        if (!loading.add(playerId)) return;
        core.scheduler().supplyIo(() -> database.load(playerId)).whenComplete((loaded, error) ->
                core.scheduler().runPrimary(() -> {
                    loading.remove(playerId);
                    if (error != null) {
                        PlayerJobsProfile failed = profiles.get(playerId);
                        if (failed != null) failed.state(PlayerJobsProfile.State.FAILED);
                        logger.log(Level.SEVERE, "Failed to load PlexonJobs profile " + playerId, error);
                        return;
                    }
                    profiles.put(playerId, loaded);
                }));
    }

    private void saveAsync(UUID playerId) {
        if (!dirty.contains(playerId)) return;
        PlayerJobsProfile profile = profiles.get(playerId);
        if (profile == null || profile.state() != PlayerJobsProfile.State.READY) return;
        PlayerJobsProfile.Snapshot snapshot = profile.snapshot();
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerId);
        String name = offlinePlayer.getName();
        core.scheduler().runIo(() -> database.save(snapshot, name)).whenComplete((unused, error) ->
                core.scheduler().runPrimary(() -> {
                    if (error != null) {
                        logger.log(Level.SEVERE, "Failed to save PlexonJobs profile " + playerId, error);
                    } else {
                        PlayerJobsProfile current = profiles.get(playerId);
                        if (current != null && current.revision() == snapshot.revision()) dirty.remove(playerId);
                    }
                }));
    }
}
