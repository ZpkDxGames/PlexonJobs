package com.plexon.jobs.runtime;

import com.plexon.jobs.storage.JobsDatabase;
import com.zpkdxgames.plexoncore.api.PlexonCoreAPI;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Async persistence boundary for daily-cap state; gameplay never waits for SQL. */
public final class DailyLimitPersistence {
    private static final long LOAD_RETRY_NANOS = 5_000_000_000L;

    private final PlexonCoreAPI core;
    private final JobsDatabase database;
    private final DailyLimitService limits;
    private final Logger logger;
    private final Set<UUID> loaded = ConcurrentHashMap.newKeySet();
    private final Set<UUID> loading = ConcurrentHashMap.newKeySet();
    private final Set<UUID> saving = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> loadRetryAfter = new ConcurrentHashMap<>();
    private final Map<UUID, CompletableFuture<?>> loadFutures = new ConcurrentHashMap<>();
    private final Map<UUID, CompletableFuture<Void>> saveFutures = new ConcurrentHashMap<>();
    private volatile Consumer<UUID> readyListener = ignored -> { };

    public DailyLimitPersistence(PlexonCoreAPI core, JobsDatabase database, DailyLimitService limits, Logger logger) {
        this.core = core;
        this.database = database;
        this.limits = limits;
        this.logger = logger;
    }

    public boolean ready(UUID playerId) { return loaded.contains(playerId); }
    public void readyListener(Consumer<UUID> listener) { readyListener = listener == null ? ignored -> { } : listener; }

    public boolean ensure(UUID playerId) {
        if (loaded.contains(playerId)) return true;
        long retryAfter = loadRetryAfter.getOrDefault(playerId, 0L);
        if (retryAfter > System.nanoTime()) return false;
        if (!loading.add(playerId)) return false;
        long dayId = limits.dayId();
        try {
            CompletableFuture<Map<String, JobsDatabase.DailyRow>> future = core.scheduler().supplyIo(() -> database.loadDaily(playerId, dayId));
            loadFutures.put(playerId, future);
            future.whenComplete((rows, error) -> core.scheduler().runPrimary(() -> {
                loadFutures.remove(playerId, future);
                loading.remove(playerId);
                if (error != null) {
                    loadRetryAfter.put(playerId, System.nanoTime() + LOAD_RETRY_NANOS);
                    logger.log(Level.SEVERE, "Failed to hydrate daily Jobs state for " + playerId, error);
                    return;
                }
                Map<String, DailyLimitService.CounterView> restored = rows.entrySet().stream()
                        .collect(java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey,
                                entry -> new DailyLimitService.CounterView(entry.getValue().moneyMinor(), entry.getValue().xp())));
                if (limits.restore(playerId, dayId, restored)) {
                    loadRetryAfter.remove(playerId);
                    loaded.add(playerId);
                    notifyReady(playerId);
                }
            }));
        } catch (RuntimeException failure) {
            loading.remove(playerId);
            loadRetryAfter.put(playerId, System.nanoTime() + LOAD_RETRY_NANOS);
            logger.log(Level.SEVERE, "Failed to schedule daily Jobs hydration for " + playerId, failure);
        }
        return false;
    }

    public void flushDirty() {
        for (UUID playerId : limits.dirtyPlayers()) if (loaded.contains(playerId)) saveAsync(playerId);
    }

    public void sweepOnline(Collection<UUID> onlinePlayers) {
        Set<UUID> online = Set.copyOf(onlinePlayers);
        for (UUID playerId : online) ensure(playerId);
        for (UUID playerId : Set.copyOf(loaded)) {
            if (online.contains(playerId)) continue;
            if (limits.isDirty(playerId) || saving.contains(playerId)) {
                saveAsync(playerId);
                continue;
            }
            loaded.remove(playerId);
            loadRetryAfter.remove(playerId);
            limits.remove(playerId);
        }
    }

    public void saveAllBlocking() {
        awaitInFlightSaves();
        for (UUID playerId : limits.dirtyPlayers()) {
            if (!loaded.contains(playerId)) continue;
            DailyLimitService.Snapshot snapshot = limits.snapshot(playerId);
            try {
                database.saveDaily(playerId, snapshot.dayId(), databaseRows(snapshot));
                limits.markPersisted(playerId, snapshot.revision());
            } catch (RuntimeException failure) {
                logger.log(Level.SEVERE, "Failed to persist daily Jobs state during shutdown: " + playerId, failure);
            }
        }
    }

    public void awaitInFlightSaves() {
        List<CompletableFuture<Void>> futures = List.copyOf(saveFutures.values());
        if (futures.isEmpty()) return;
        CompletableFuture<?>[] settled = futures.stream().map(future -> future.handle((unused, error) -> null)).toArray(CompletableFuture[]::new);
        CompletableFuture.allOf(settled).join();
    }

    public int loadedCount() { return loaded.size(); }
    public int loadingCount() { return loading.size(); }
    public int savingCount() { return saving.size(); }

    private void notifyReady(UUID playerId) {
        try { readyListener.accept(playerId); }
        catch (RuntimeException failure) { logger.log(Level.SEVERE, "Daily ready listener failed for " + playerId, failure); }
    }

    private void saveAsync(UUID playerId) {
        if (!limits.isDirty(playerId) || !saving.add(playerId)) return;
        DailyLimitService.Snapshot snapshot = limits.snapshot(playerId);
        try {
            CompletableFuture<Void> future = core.scheduler().runIo(() -> database.saveDaily(playerId, snapshot.dayId(), databaseRows(snapshot)));
            saveFutures.put(playerId, future);
            future.whenComplete((unused, error) -> core.scheduler().runPrimary(() -> {
                saveFutures.remove(playerId, future);
                saving.remove(playerId);
                if (error != null) logger.log(Level.SEVERE, "Failed to persist daily Jobs state for " + playerId, error);
                else limits.markPersisted(playerId, snapshot.revision());
            }));
        } catch (RuntimeException failure) {
            saveFutures.remove(playerId);
            saving.remove(playerId);
            logger.log(Level.SEVERE, "Failed to schedule daily Jobs persistence for " + playerId, failure);
        }
    }

    private static Map<String, JobsDatabase.DailyRow> databaseRows(DailyLimitService.Snapshot snapshot) {
        return snapshot.rows().entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                Map.Entry::getKey, entry -> new JobsDatabase.DailyRow(entry.getValue().moneyMinor(), entry.getValue().xp())));
    }
}
