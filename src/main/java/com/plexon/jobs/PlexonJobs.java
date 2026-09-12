package com.plexon.jobs;

import com.plexon.jobs.api.PlexonJobsAPI;
import com.plexon.jobs.command.JobsAdminCommand;
import com.plexon.jobs.command.JobsCommand;
import com.plexon.jobs.config.ConfigLoader;
import com.plexon.jobs.config.JobsConfig;
import com.plexon.jobs.config.Messages;
import com.plexon.jobs.economy.JobsEconomy;
import com.plexon.jobs.economy.PayoutService;
import com.plexon.jobs.economy.PendingPayoutLedger;
import com.plexon.jobs.economy.UnavailableJobsEconomy;
import com.plexon.jobs.economy.VaultJobsEconomy;
import com.plexon.jobs.gui.JobsMenuController;
import com.plexon.jobs.integration.PlexonJobsExpansion;
import com.plexon.jobs.migration.LegacyJobsMigration;
import com.plexon.jobs.runtime.BlockActivityRouter;
import com.plexon.jobs.runtime.DailyLimitPersistence;
import com.plexon.jobs.runtime.DailyLimitService;
import com.plexon.jobs.runtime.JobRegistry;
import com.plexon.jobs.runtime.JobsMetrics;
import com.plexon.jobs.runtime.JobsRuntime;
import com.plexon.jobs.runtime.PlayerStateListener;
import com.plexon.jobs.runtime.ProfileManager;
import com.plexon.jobs.runtime.RuntimeMode;
import com.plexon.jobs.runtime.ShadowLedger;
import com.plexon.jobs.storage.JobsDatabase;
import com.zpkdxgames.plexoncore.api.PlexonCoreAPI;
import com.zpkdxgames.plexoncore.event.CoreBlockSubscription;
import com.zpkdxgames.plexoncore.module.ModuleRegistry;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class PlexonJobs extends JavaPlugin {
    private PlexonCoreAPI core;
    private JobsDatabase database;
    private ProfileManager profiles;
    private DailyLimitService limits;
    private DailyLimitPersistence dailyPersistence;
    private final PendingPayoutLedger pendingLedger = new PendingPayoutLedger();
    private final ShadowLedger shadow = new ShadowLedger();
    private final JobsMetrics metrics = new JobsMetrics();
    private final Set<CompletableFuture<Void>> shadowWrites = ConcurrentHashMap.newKeySet();
    private JobsEconomy economy;
    private volatile PayoutService payouts;
    private volatile JobsRuntime runtime;
    private volatile BlockActivityRouter blockRouter;
    private volatile Messages messages;
    private AutoCloseable blockSubscription;
    private final List<BukkitTask> tasks = new ArrayList<>();
    private PlexonJobsExpansion expansion;
    private LegacyJobsMigration migration;
    private JobsMenuController menus;

    @Override
    public void onEnable() {
        RegisteredServiceProvider<PlexonCoreAPI> registration = Bukkit.getServicesManager().getRegistration(PlexonCoreAPI.class);
        if (registration == null || registration.getProvider() == null) {
            getLogger().severe("PlexonCore Runtime API is required; disabling PlexonJobs.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        core = registration.getProvider();
        if (!core.supportsApi(2, 0)) {
            getLogger().severe("PlexonJobs requires PlexonCore API 2.x; running " + core.version().apiVersion());
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        try {
            database = new JobsDatabase(getDataFolder().toPath().resolve("jobs.db"));
            database.initialize();
            profiles = new ProfileManager(core, database, getLogger());
            migration = new LegacyJobsMigration(this);
            economy = createEconomy();
            ConfigLoader.Loaded loaded = new ConfigLoader(this).load();
            PreparedRuntime prepared = prepareRuntime(loaded);
            AutoCloseable subscription = subscribeBlockBreaks(prepared.registry(), prepared.router());
            installPrepared(prepared);
            blockSubscription = subscription;

            // The API contract must exist on an ordinary initial enable, not only after reload.
            registerApiService();
            menus = new JobsMenuController(this);
            Bukkit.getPluginManager().registerEvents(menus, this);
            Bukkit.getPluginManager().registerEvents(new PlayerStateListener(profiles, dailyPersistence), this);
            registerCommands();
            registerModule();
            registerPlaceholderApi();
            scheduleRuntimeTasks();
            getLogger().info("PlexonJobs " + getPluginMeta().getVersion() + " enabled in " + runtime.config().mode() +
                    " mode on Core " + core.version().pluginVersion() + " / API " + core.version().apiVersion() + ".");
        } catch (RuntimeException failure) {
            getLogger().log(Level.SEVERE, "PlexonJobs failed to enable safely", failure);
            Bukkit.getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        // Stop every producer before establishing the final persistence barriers.
        cancelTasks();
        closeBlockSubscription();
        unregisterExpansion();
        if (payouts != null) {
            while (payouts.totalPending() > 0 && payouts.economyAvailable()) {
                int committed = payouts.flush(runtime == null ? 100 : runtime.config().maxCommitsPerTick());
                if (committed == 0) break;
            }
        }
        awaitShadowWrites();
        flushShadowBlocking();
        if (dailyPersistence != null) dailyPersistence.saveAllBlocking();
        if (profiles != null) profiles.saveAllBlocking();
        Bukkit.getServicesManager().unregisterAll(this);
        if (core != null) {
            try { core.modules().unregisterOwnedBy(this); } catch (RuntimeException ignored) { }
        }
        if (payouts != null && payouts.totalPending() > 0) {
            getLogger().severe("PlexonJobs disabled with " + payouts.totalPending() + " minor money units still pending; live-process safety prevented duplicate acknowledgement, but a process crash cannot provide cross-process exactly-once Vault semantics.");
        }
    }

    public synchronized void reloadJobs() {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("PlexonJobs reload must run on the primary thread");
        ConfigLoader.Loaded loaded = new ConfigLoader(this).load();
        JobsConfig old = runtime.config();
        JobsConfig nextConfig = loaded.config();
        if (old.moneyScale() != nextConfig.moneyScale() && pendingLedger.totalPending() > 0) {
            throw new IllegalStateException("money-scale cannot change while pending payouts exist");
        }
        if (!old.resetZone().equals(nextConfig.resetZone()) || old.defaultMoneyCapMinor() != nextConfig.defaultMoneyCapMinor() || old.defaultXpCap() != nextConfig.defaultXpCap()) {
            throw new IllegalStateException("daily limit/timezone changes require a restart to preserve current-day counters");
        }

        PreparedRuntime next = prepareRuntime(loaded);
        AutoCloseable nextSubscription = subscribeBlockBreaks(next.registry(), next.router());
        AutoCloseable previousSubscription = blockSubscription;
        JobsRuntime previousRuntime = runtime;
        PayoutService previousPayouts = payouts;
        BlockActivityRouter previousRouter = blockRouter;
        Messages previousMessages = messages;

        try {
            cancelTasks();
            unregisterExpansion();
            unregisterApiService();
            installPrepared(next);
            blockSubscription = nextSubscription;
            closeSubscription(previousSubscription);
            registerApiService();
            registerPlaceholderApi();
            scheduleRuntimeTasks();
            updateModuleState();
        } catch (RuntimeException failure) {
            cancelTasks();
            unregisterExpansion();
            unregisterApiService();
            closeSubscription(nextSubscription);
            runtime = previousRuntime;
            payouts = previousPayouts;
            blockRouter = previousRouter;
            messages = previousMessages;
            blockSubscription = subscribeBlockBreaks(previousRuntime.registry(), previousRouter);
            registerApiService();
            registerPlaceholderApi();
            scheduleRuntimeTasks();
            updateModuleState();
            throw failure;
        }
    }

    private PreparedRuntime prepareRuntime(ConfigLoader.Loaded loaded) {
        JobRegistry registry = new JobRegistry(loaded.definitions());
        JobsConfig config = loaded.config();
        if (!ModuleRegistry.ModuleVersionRange.parse(config.coreApiRange()).contains(core.version())) {
            throw new IllegalStateException("Configured Core range " + config.coreApiRange() + " rejects API " + core.version().apiVersion());
        }
        if (limits == null) {
            limits = new DailyLimitService(config.resetZone(), config.defaultMoneyCapMinor(), config.defaultXpCap());
            dailyPersistence = new DailyLimitPersistence(core, database, limits, getLogger());
        }
        PayoutService nextPayouts = new PayoutService(this, economy, pendingLedger, metrics, config.moneyScale(), config.retryLimit());
        JobsRuntime nextRuntime = new JobsRuntime(profiles, registry, config, nextPayouts);
        BlockActivityRouter nextRouter = new BlockActivityRouter(config, registry, profiles, limits, dailyPersistence,
                nextPayouts, shadow, metrics);
        return new PreparedRuntime(registry, config, nextPayouts, nextRuntime, nextRouter, loaded.messages());
    }

    private void installPrepared(PreparedRuntime prepared) {
        payouts = prepared.payouts();
        runtime = prepared.runtime();
        blockRouter = prepared.router();
        messages = prepared.messages();
    }

    private JobsEconomy createEconomy() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) return new UnavailableJobsEconomy();
        try {
            VaultJobsEconomy vault = new VaultJobsEconomy();
            vault.refresh();
            return vault;
        } catch (LinkageError error) {
            getLogger().warning("Vault API could not be linked: " + error.getMessage());
            return new UnavailableJobsEconomy();
        }
    }

    private AutoCloseable subscribeBlockBreaks(JobRegistry registry, BlockActivityRouter router) {
        if (registry.breakMaterials().isEmpty()) return null;
        return core.events().subscribeBlockBreak("plexonjobs",
                CoreBlockSubscription.builder().materials(registry.breakMaterials()).requiresNaturalOrigin(true).build(),
                router::handle);
    }

    private void registerCommands() {
        PluginCommand jobs = Objects.requireNonNull(getCommand("jobs"), "jobs command missing from plugin.yml");
        JobsCommand jobsCommand = new JobsCommand(this);
        jobs.setExecutor(jobsCommand);
        jobs.setTabCompleter(jobsCommand);
        PluginCommand admin = Objects.requireNonNull(getCommand("jobsadmin"), "jobsadmin command missing from plugin.yml");
        JobsAdminCommand adminCommand = new JobsAdminCommand(this);
        admin.setExecutor(adminCommand);
        admin.setTabCompleter(adminCommand);
    }

    private void registerApiService() {
        Bukkit.getServicesManager().register(PlexonJobsAPI.class, runtime, this, ServicePriority.Normal);
    }

    private void unregisterApiService() {
        if (runtime != null) Bukkit.getServicesManager().unregister(PlexonJobsAPI.class, runtime);
    }

    private void unregisterExpansion() {
        if (expansion == null) return;
        try { expansion.unregister(); } catch (RuntimeException ignored) { }
        expansion = null;
    }

    private void registerPlaceholderApi() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) return;
        try {
            expansion = new PlexonJobsExpansion(runtime, limits, dailyPersistence, getPluginMeta().getVersion());
            if (!expansion.register()) {
                getLogger().warning("PlaceholderAPI rejected the PlexonJobs expansion registration.");
                expansion = null;
            }
        } catch (LinkageError error) {
            getLogger().warning("PlaceholderAPI could not be linked: " + error.getMessage());
        }
    }

    private void registerModule() {
        var result = core.modules().register(new ModuleRegistry.ModuleDescriptor(
                "jobs", "PlexonJobs", getName(), getPluginMeta().getVersion(), this,
                ModuleRegistry.ModuleVersionRange.parse(runtime.config().coreApiRange()),
                Set.of("jobs", "economy", "block-break-consumer", "shadow-rollout"),
                moduleState(), moduleDetail(), Instant.now()));
        if (!result.success()) throw new IllegalStateException("Core module registration failed: " + result.message());
    }

    private void updateModuleState() {
        core.modules().updateState("jobs", this, moduleState(), moduleDetail());
    }

    private ModuleRegistry.ModuleState moduleState() {
        if (runtime.config().mode() == RuntimeMode.PRIMARY && !payouts.economyAvailable()) return ModuleRegistry.ModuleState.DEGRADED;
        return ModuleRegistry.ModuleState.READY;
    }

    private String moduleDetail() {
        if (runtime.config().mode() == RuntimeMode.SHADOW) return "SHADOW calculation active; no live XP or Vault deposits";
        if (runtime.config().mode() == RuntimeMode.PRIMARY && !payouts.economyAvailable()) return "PRIMARY job XP active; Vault money provider unavailable";
        return runtime.config().mode() + " runtime ready";
    }

    private void scheduleRuntimeTasks() {
        JobsConfig cfg = runtime.config();
        tasks.add(Bukkit.getScheduler().runTaskTimer(this, () -> {
            payouts.flush(cfg.maxCommitsPerTick());
            updateModuleState();
        }, cfg.payoutFlushTicks(), cfg.payoutFlushTicks()));
        tasks.add(Bukkit.getScheduler().runTaskTimer(this, () -> {
            List<UUID> online = Bukkit.getOnlinePlayers().stream().map(player -> player.getUniqueId()).toList();
            profiles.sweepOnline(online);
            profiles.flushDirty();
            dailyPersistence.sweepOnline(online);
            dailyPersistence.flushDirty();
            flushShadowAsync();
        }, cfg.saveIntervalTicks(), cfg.saveIntervalTicks()));
    }

    private void flushShadowAsync() {
        var batch = shadow.drain();
        if (batch.isEmpty()) return;
        List<JobsDatabase.ShadowDelta> deltas = shadowDeltas(batch);
        try {
            CompletableFuture<Void> io = core.scheduler().runIo(() -> database.addShadowBatch(deltas));
            CompletableFuture<Void> settled = io.handle((unused, error) -> {
                if (error != null) {
                    restoreShadowBatch(batch);
                    getLogger().log(Level.SEVERE, "Failed to persist atomic shadow aggregate batch; batch returned to memory", error);
                }
                return null;
            });
            shadowWrites.add(settled);
            settled.whenComplete((unused, error) -> shadowWrites.remove(settled));
        } catch (RuntimeException failure) {
            restoreShadowBatch(batch);
            getLogger().log(Level.SEVERE, "Failed to schedule shadow aggregate persistence; batch returned to memory", failure);
        }
    }

    private void awaitShadowWrites() {
        List<CompletableFuture<Void>> inFlight = List.copyOf(shadowWrites);
        if (inFlight.isEmpty()) return;
        CompletableFuture.allOf(inFlight.toArray(CompletableFuture[]::new)).join();
    }

    private void flushShadowBlocking() {
        var batch = shadow.drain();
        if (batch.isEmpty()) return;
        try {
            database.addShadowBatch(shadowDeltas(batch));
        } catch (RuntimeException failure) {
            restoreShadowBatch(batch);
            getLogger().log(Level.SEVERE, "Failed to persist shutdown shadow aggregate batch", failure);
        }
    }

    private List<JobsDatabase.ShadowDelta> shadowDeltas(java.util.Map<ShadowLedger.Key, ShadowLedger.Snapshot> batch) {
        return batch.entrySet().stream()
                .map(entry -> new JobsDatabase.ShadowDelta(
                        entry.getKey().playerId(), entry.getKey().jobId(),
                        entry.getValue().moneyMinor(), entry.getValue().xp(), entry.getValue().events()))
                .toList();
    }

    private void restoreShadowBatch(java.util.Map<ShadowLedger.Key, ShadowLedger.Snapshot> batch) {
        batch.forEach((key, total) -> shadow.addAggregate(
                key.playerId(), key.jobId(), total.moneyMinor(), total.xp(), total.events()));
    }

    private void cancelTasks() {
        for (BukkitTask task : tasks) task.cancel();
        tasks.clear();
    }

    private void closeBlockSubscription() {
        closeSubscription(blockSubscription);
        blockSubscription = null;
    }

    private void closeSubscription(AutoCloseable subscription) {
        if (subscription == null) return;
        try { subscription.close(); }
        catch (Exception failure) { getLogger().log(Level.WARNING, "Failed to close Core block subscription", failure); }
    }

    public PlexonCoreAPI core() { return core; }
    public JobsDatabase database() { return database; }
    public JobsRuntime runtime() { return runtime; }
    public DailyLimitService limits() { return limits; }
    public DailyLimitPersistence dailyPersistence() { return dailyPersistence; }
    public PayoutService payouts() { return payouts; }
    public ShadowLedger shadow() { return shadow; }
    public JobsMetrics metrics() { return metrics; }
    public LegacyJobsMigration migration() { return migration; }
    public Messages messages() { return messages; }
    public JobsMenuController menus() { return menus; }

    private record PreparedRuntime(JobRegistry registry, JobsConfig config, PayoutService payouts,
                                   JobsRuntime runtime, BlockActivityRouter router, Messages messages) {}
}
