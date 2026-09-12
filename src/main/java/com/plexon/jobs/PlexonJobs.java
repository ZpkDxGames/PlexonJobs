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
import com.plexon.jobs.runtime.ActivityGrantService;
import com.plexon.jobs.runtime.ActivityInterestIndex;
import com.plexon.jobs.runtime.ActivityListenerCoordinator;
import com.plexon.jobs.runtime.BlockActivityRouter;
import com.plexon.jobs.runtime.CompiledJobRoutes;
import com.plexon.jobs.runtime.CoreBlockSubscriptionCoordinator;
import com.plexon.jobs.runtime.DailyLimitPersistence;
import com.plexon.jobs.runtime.DailyLimitService;
import com.plexon.jobs.runtime.ExplorerDiscoveryService;
import com.plexon.jobs.runtime.JobRegistry;
import com.plexon.jobs.runtime.JobsMetrics;
import com.plexon.jobs.runtime.JobsRuntime;
import com.plexon.jobs.runtime.PlayerFeedbackService;
import com.plexon.jobs.runtime.PlayerStateListener;
import com.plexon.jobs.runtime.ProfileManager;
import com.plexon.jobs.runtime.RuntimeMode;
import com.plexon.jobs.runtime.ShadowLedger;
import com.plexon.jobs.storage.JobsDatabase;
import com.zpkdxgames.plexoncore.api.PlexonCoreAPI;
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
    private volatile ActivityGrantService grants;
    private volatile BlockActivityRouter blockRouter;
    private volatile CompiledJobRoutes compiledRoutes;
    private volatile ActivityInterestIndex interest;
    private volatile Messages messages;
    private final List<BukkitTask> tasks = new ArrayList<>();
    private PlexonJobsExpansion expansion;
    private LegacyJobsMigration migration;
    private JobsMenuController menus;
    private ExplorerDiscoveryService explorer;
    private PlayerFeedbackService feedback;
    private ActivityListenerCoordinator activityListeners;
    private CoreBlockSubscriptionCoordinator coreBlockSubscriptions;

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
            feedback = new PlayerFeedbackService(this);

            ConfigLoader.Loaded loaded = new ConfigLoader(this).load();
            PreparedRuntime prepared = prepareRuntime(loaded);
            installPrepared(prepared);
            acceptProfileDefinitions(prepared);
            profiles.readyListener(this::refreshInterest);
            dailyPersistence.readyListener(this::refreshInterest);

            menus = new JobsMenuController(this);
            explorer = new ExplorerDiscoveryService(this);
            activityListeners = new ActivityListenerCoordinator(this);
            coreBlockSubscriptions = new CoreBlockSubscriptionCoordinator(this);
            interest.topologyListener(this::reconcileTopology);

            registerApiService();
            Bukkit.getPluginManager().registerEvents(menus, this);
            Bukkit.getPluginManager().registerEvents(new PlayerStateListener(this), this);
            registerCommands();
            registerModule();
            registerPlaceholderApi();
            feedback.refreshCache();
            warmOnlinePlayers();
            interest.rebuildOnline();
            scheduleRuntimeTasks();
            getLogger().info("PlexonJobs " + getPluginMeta().getVersion() + " enabled in " + runtime.config().mode()
                    + " mode with compiled participation routing on Core " + core.version().pluginVersion()
                    + " / API " + core.version().apiVersion() + ".");
        } catch (RuntimeException failure) {
            getLogger().log(Level.SEVERE, "PlexonJobs failed to enable safely", failure);
            Bukkit.getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        cancelTasks();
        if (explorer != null) explorer.close();
        if (activityListeners != null) activityListeners.close();
        if (coreBlockSubscriptions != null) coreBlockSubscriptions.close();
        if (feedback != null) feedback.hideAll();
        unregisterExpansion();
        if (profiles != null) profiles.readyListener(null);
        if (dailyPersistence != null) dailyPersistence.readyListener(null);
        if (payouts != null) {
            while (payouts.totalPending() > 0) {
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
            getLogger().severe("PlexonJobs disabled with " + payouts.totalPending()
                    + " minor money units still pending; live-process safety prevented duplicate acknowledgement,"
                    + " but a process crash cannot provide cross-process exactly-once Vault semantics.");
        }
    }

    public synchronized void reloadJobs() {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("PlexonJobs reload must run on the primary thread");
        ConfigLoader.Loaded loaded = new ConfigLoader(this).load();
        JobsConfig oldConfig = runtime.config();
        JobsConfig nextConfig = loaded.config();
        if (oldConfig.moneyScale() != nextConfig.moneyScale() && pendingLedger.totalPending() > 0) {
            throw new IllegalStateException("money-scale cannot change while pending payouts exist");
        }
        if (!oldConfig.resetZone().equals(nextConfig.resetZone())
                || oldConfig.defaultMoneyCapMinor() != nextConfig.defaultMoneyCapMinor()
                || oldConfig.defaultXpCap() != nextConfig.defaultXpCap()) {
            throw new IllegalStateException("daily limit/timezone changes require a restart to preserve current-day counters");
        }

        PreparedRuntime next = prepareRuntime(loaded);
        PreparedRuntime previous = currentPrepared();
        ActivityListenerCoordinator previousListeners = activityListeners;

        try {
            cancelTasks();
            unregisterExpansion();
            unregisterApiService();
            previousListeners.close();

            installPrepared(next);
            interest.topologyListener(this::reconcileTopology);
            activityListeners = new ActivityListenerCoordinator(this);
            feedback.refreshCache();
            interest.rebuildOnline();
            registerApiService();
            registerPlaceholderApi();
            scheduleRuntimeTasks();
            updateModuleState();
            acceptProfileDefinitions(next);
        } catch (RuntimeException failure) {
            cancelTasks();
            unregisterExpansion();
            unregisterApiService();
            if (activityListeners != null && activityListeners != previousListeners) {
                try { activityListeners.close(); } catch (RuntimeException ignored) { }
            }
            installPrepared(previous);
            interest.topologyListener(this::reconcileTopology);
            activityListeners = new ActivityListenerCoordinator(this);
            feedback.refreshCache();
            interest.rebuildOnline();
            registerApiService();
            registerPlaceholderApi();
            scheduleRuntimeTasks();
            updateModuleState();
            throw failure;
        }
    }

    private PreparedRuntime prepareRuntime(ConfigLoader.Loaded loaded) {
        JobRegistry registry = new JobRegistry(loaded.definitions());
        CompiledJobRoutes routes = new CompiledJobRoutes(loaded.definitions());
        JobsConfig config = loaded.config();
        if (!ModuleRegistry.ModuleVersionRange.parse(config.coreApiRange()).contains(core.version())) {
            throw new IllegalStateException("Configured Core range " + config.coreApiRange() + " rejects API " + core.version().apiVersion());
        }
        if (limits == null) {
            limits = new DailyLimitService(config.resetZone(), config.defaultMoneyCapMinor(), config.defaultXpCap());
            dailyPersistence = new DailyLimitPersistence(core, database, limits, getLogger());
        }
        PayoutService nextPayouts = new PayoutService(this, economy, pendingLedger, metrics, config.moneyScale(), config.retryLimit());
        ActivityInterestIndex nextInterest = new ActivityInterestIndex(profiles, dailyPersistence, routes, config.mode());
        JobsRuntime nextRuntime = new JobsRuntime(profiles, registry, config, nextPayouts, nextInterest);
        ActivityGrantService nextGrants = new ActivityGrantService(config, registry, routes, nextInterest, profiles,
                limits, nextPayouts, shadow, metrics, feedback);
        BlockActivityRouter nextRouter = new BlockActivityRouter(routes, nextInterest, nextGrants, metrics);
        return new PreparedRuntime(registry, config, routes, nextInterest, nextPayouts, nextRuntime,
                nextGrants, nextRouter, loaded.messages());
    }

    private PreparedRuntime currentPrepared() {
        return new PreparedRuntime(runtime.registry(), runtime.config(), compiledRoutes, interest, payouts,
                runtime, grants, blockRouter, messages);
    }

    private void installPrepared(PreparedRuntime prepared) {
        payouts = prepared.payouts();
        runtime = prepared.runtime();
        compiledRoutes = prepared.routes();
        interest = prepared.interest();
        grants = prepared.grants();
        blockRouter = prepared.router();
        messages = prepared.messages();
    }

    private void acceptProfileDefinitions(PreparedRuntime prepared) {
        profiles.acceptKnownJobs(prepared.registry().definitions().stream().map(definition -> definition.id()).toList());
    }

    private void refreshInterest(UUID playerId) {
        ActivityInterestIndex live = interest;
        if (live != null) live.refresh(playerId);
    }

    private void warmOnlinePlayers() {
        for (var player : Bukkit.getOnlinePlayers()) {
            UUID id = player.getUniqueId();
            profiles.ensure(id);
            dailyPersistence.ensure(id);
        }
    }

    private void reconcileTopology() {
        if (activityListeners != null) activityListeners.reconcile();
        if (coreBlockSubscriptions != null) coreBlockSubscriptions.reconcile();
        if (explorer != null) explorer.reconcile();
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
                Set.of("jobs", "economy", "compiled-activity-routing", "dynamic-listeners", "dynamic-block-subscription", "player-feedback"),
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
        if (runtime.config().mode() == RuntimeMode.SHADOW) return "SHADOW compiled job calculation active; no live XP or Vault deposits";
        if (runtime.config().mode() == RuntimeMode.PRIMARY && !payouts.economyAvailable()) return "PRIMARY job XP active; Vault money provider unavailable";
        return runtime.config().mode() + " compiled participation runtime ready";
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
        tasks.add(Bukkit.getScheduler().runTaskTimer(this, feedback::flush,
                cfg.performance().feedbackFlushTicks(), cfg.performance().feedbackFlushTicks()));
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
        if (!inFlight.isEmpty()) CompletableFuture.allOf(inFlight.toArray(CompletableFuture[]::new)).join();
    }

    private void flushShadowBlocking() {
        var batch = shadow.drain();
        if (batch.isEmpty()) return;
        try { database.addShadowBatch(shadowDeltas(batch)); }
        catch (RuntimeException failure) {
            restoreShadowBatch(batch);
            getLogger().log(Level.SEVERE, "Failed to persist shutdown shadow aggregate batch", failure);
        }
    }

    private List<JobsDatabase.ShadowDelta> shadowDeltas(java.util.Map<ShadowLedger.Key, ShadowLedger.Snapshot> batch) {
        return batch.entrySet().stream().map(entry -> new JobsDatabase.ShadowDelta(
                entry.getKey().playerId(), entry.getKey().jobId(), entry.getValue().moneyMinor(),
                entry.getValue().xp(), entry.getValue().events())).toList();
    }

    private void restoreShadowBatch(java.util.Map<ShadowLedger.Key, ShadowLedger.Snapshot> batch) {
        batch.forEach((key, total) -> shadow.addAggregate(key.playerId(), key.jobId(), total.moneyMinor(), total.xp(), total.events()));
    }

    private void cancelTasks() {
        for (BukkitTask task : tasks) task.cancel();
        tasks.clear();
    }

    public PlexonCoreAPI core() { return core; }
    public JobsDatabase database() { return database; }
    public JobsRuntime runtime() { return runtime; }
    public ActivityGrantService grants() { return grants; }
    public BlockActivityRouter blockRouter() { return blockRouter; }
    public CompiledJobRoutes compiledRoutes() { return compiledRoutes; }
    public ActivityInterestIndex interest() { return interest; }
    public ActivityListenerCoordinator activityListeners() { return activityListeners; }
    public CoreBlockSubscriptionCoordinator coreBlockSubscriptions() { return coreBlockSubscriptions; }
    public DailyLimitService limits() { return limits; }
    public DailyLimitPersistence dailyPersistence() { return dailyPersistence; }
    public PayoutService payouts() { return payouts; }
    public ShadowLedger shadow() { return shadow; }
    public JobsMetrics metrics() { return metrics; }
    public LegacyJobsMigration migration() { return migration; }
    public Messages messages() { return messages; }
    public JobsMenuController menus() { return menus; }
    public PlayerFeedbackService feedback() { return feedback; }

    private record PreparedRuntime(JobRegistry registry, JobsConfig config, CompiledJobRoutes routes,
                                   ActivityInterestIndex interest, PayoutService payouts, JobsRuntime runtime,
                                   ActivityGrantService grants, BlockActivityRouter router, Messages messages) { }
}
