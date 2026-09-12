package com.plexon.jobs.runtime;

import com.plexon.jobs.model.PlayerJobsProfile;
import com.plexon.jobs.storage.JobsDatabase;
import com.zpkdxgames.plexoncore.api.PlexonCoreAPI;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ProfileManager {
    private static final long LOAD_RETRY_NANOS = 5_000_000_000L;

    private final PlexonCoreAPI core;
    private final JobsDatabase database;
    private final Logger logger;
    private final Map<UUID, PlayerJobsProfile> profiles = new ConcurrentHashMap<>();
    private final Map<UUID, Long> generations = new ConcurrentHashMap<>();
    private final Map<UUID, Long> loadRetryAfter = new ConcurrentHashMap<>();
    private final Set<UUID> dirty = ConcurrentHashMap.newKeySet();
    private final Set<UUID> loading = ConcurrentHashMap.newKeySet();
    private final Set<UUID> saving = ConcurrentHashMap.newKeySet();
    private final Map<UUID, CompletableFuture<Void>> saveFutures = new ConcurrentHashMap<>();

    public ProfileManager(PlexonCoreAPI core, JobsDatabase database, Logger logger) {
        this.core = core;
        this.database = database;
        this.logger = logger;
    }

    public PlayerJobsProfile get(UUID playerId) { return profiles.get(playerId); }

    public PlayerJobsProfile ensure(UUID playerId) {
        while (true) {
            PlayerJobsProfile existing = profiles.get(playerId);
            if (existing == null) {
                PlayerJobsProfile loadingProfile = new PlayerJobsProfile(playerId, PlayerJobsProfile.State.LOADING);
                PlayerJobsProfile previous = profiles.putIfAbsent(playerId, loadingProfile);
                if (previous != null) continue;
                long generation = generations.merge(playerId, 1L, Long::sum);
                requestLoad(playerId, loadingProfile, generation);
                return loadingProfile;
            }
            if (existing.state() != PlayerJobsProfile.State.FAILED) return existing;
            if (loadRetryAfter.getOrDefault(playerId, 0L) > System.nanoTime()) return existing;

            PlayerJobsProfile retryProfile = new PlayerJobsProfile(playerId, PlayerJobsProfile.State.LOADING);
            if (!profiles.replace(playerId, existing, retryProfile)) continue;
            long generation = generations.merge(playerId, 1L, Long::sum);
            requestLoad(playerId, retryProfile, generation);
            return retryProfile;
        }
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
                if (!dirty.contains(playerId) && !loading.contains(playerId) && !saving.contains(playerId)) {
                    if (profiles.remove(playerId) != null) {
                        generations.merge(playerId, 1L, Long::sum);
                        loadRetryAfter.remove(playerId);
                    }
                }
            }
        }
    }

    /**
     * Waits for every async profile write that was already scheduled at the shutdown boundary.
     * The caller must stop event/task producers before invoking this method so no newer async save
     * can be created after the snapshot of futures is taken.
     */
    public void awaitInFlightSaves() {
        List<CompletableFuture<Void>> futures = List.copyOf(saveFutures.values());
        if (futures.isEmpty()) return;
        CompletableFuture<?>[] settled = futures.stream()
                .map(future -> future.handle((unused, error) -> null))
                .toArray(CompletableFuture[]::new);
        CompletableFuture.allOf(settled).join();
    }

    public void saveAllBlocking() {
        // An older async snapshot must never be allowed to land after the final authoritative save.
        awaitInFlightSaves();
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
    public int savingCount() { return saving.size(); }
    public int inFlightSaveCount() { return (int) saveFutures.values().stream().filter(future -> !future.isDone()).count(); }

    private void requestLoad(UUID playerId, PlayerJobsProfile loadingProfile, long generation) {
        if (!loading.add(playerId)) return;
        try {
            core.scheduler().supplyIo(() -> database.load(playerId)).whenComplete((loaded, error) ->
                    core.scheduler().runPrimary(() -> {
                        loading.remove(playerId);
                        if (generations.getOrDefault(playerId, 0L) != generation || profiles.get(playerId) != loadingProfile) return;
                        if (error != null) {
                            loadingProfile.state(PlayerJobsProfile.State.FAILED);
                            loadRetryAfter.put(playerId, System.nanoTime() + LOAD_RETRY_NANOS);
                            logger.log(Level.SEVERE, "Failed to load PlexonJobs profile " + playerId + "; retrying after backoff", error);
                            return;
                        }
                        loadRetryAfter.remove(playerId);
                        profiles.replace(playerId, loadingProfile, loaded);
                    }));
        } catch (RuntimeException failure) {
            loading.remove(playerId);
            if (generations.getOrDefault(playerId, 0L) == generation && profiles.get(playerId) == loadingProfile) {
                loadingProfile.state(PlayerJobsProfile.State.FAILED);
                loadRetryAfter.put(playerId, System.nanoTime() + LOAD_RETRY_NANOS);
            }
            logger.log(Level.SEVERE, "Failed to schedule PlexonJobs profile load " + playerId + "; retrying after backoff", failure);
        }
    }

    private void saveAsync(UUID playerId) {
        if (!dirty.contains(playerId) || !saving.add(playerId)) return;
        PlayerJobsProfile profile = profiles.get(playerId);
        if (profile == null || profile.state() != PlayerJobsProfile.State.READY) {
            saving.remove(playerId);
            return;
        }
        PlayerJobsProfile.Snapshot snapshot = profile.snapshot();
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerId);
        String name = offlinePlayer.getName();
        try {
            CompletableFuture<Void> future = core.scheduler().runIo(() -> database.save(snapshot, name));
            saveFutures.put(playerId, future);
            future.whenComplete((unused, error) ->
                    core.scheduler().runPrimary(() -> {
                        saveFutures.remove(playerId, future);
                        saving.remove(playerId);
                        if (error != null) {
                            logger.log(Level.SEVERE, "Failed to save PlexonJobs profile " + playerId, error);
                        } else {
                            PlayerJobsProfile current = profiles.get(playerId);
                            if (current != null && current.revision() == snapshot.revision()) dirty.remove(playerId);
                        }
                    }));
        } catch (RuntimeException failure) {
            saveFutures.remove(playerId);
            saving.remove(playerId);
            throw failure;
        }
    }
}
