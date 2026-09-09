package com.plexon.jobs;

import com.plexon.jobs.api.PlexonJobsAPI;
import com.plexon.jobs.command.JobsAdminCommand;
import com.plexon.jobs.command.JobsCommand;
import com.plexon.jobs.config.ConfigLoader;
import com.plexon.jobs.config.JobsConfig;
import com.plexon.jobs.economy.JobsEconomy;
import com.plexon.jobs.economy.PayoutService;
import com.plexon.jobs.economy.PendingPayoutLedger;
import com.plexon.jobs.economy.UnavailableJobsEconomy;
import com.plexon.jobs.economy.VaultJobsEconomy;
import com.plexon.jobs.integration.PlexonJobsExpansion;
import com.plexon.jobs.migration.LegacyJobsMigration;
import com.plexon.jobs.runtime.BlockActivityRouter;
import com.plexon.jobs.runtime.DailyLimitService;
import com.plexon.jobs.runtime.JobRegistry;
import com.plexon.jobs.runtime.JobsMetrics;
import com.plexon.jobs.runtime.JobsRuntime;
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
import java.util.logging.Level;

public final class PlexonJobs extends JavaPlugin {
    private PlexonCoreAPI core;
    private JobsDatabase database;
    private ProfileManager profiles;
    private DailyLimitService limits;
    private final PendingPayoutLedger pendingLedger = new PendingPayoutLedger();
    private final ShadowLedger shadow = new ShadowLedger();
    private final JobsMetrics metrics = new JobsMetrics();
    private JobsEconomy economy;
    private volatile PayoutService payouts;
    private volatile JobsRuntime runtime;
    private volatile BlockActivityRouter blockRouter;
    private AutoCloseable blockSubscription;
    private final List<BukkitTask> tasks = new ArrayList<>();
    private PlexonJobsExpansion expansion;
    private LegacyJobsMigration migration;

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
            installRuntime(new ConfigLoader(this).load(), true);
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
        cancelTasks();
        closeBlockSubscription();
        if (expansion != null) {
            try { expansion.unregister(); } catch (RuntimeException ignored) { }
            expansion = null;
        }
        if (payouts != null) {
            for (int i = 0; i < 4 && payouts.totalPending() > 0 && payouts.economyAvailable(); i++) {
                payouts.flush(runtime == null ? 100 : runtime.config().maxCommitsPerTick());
            }
        }
        flushShadowBlocking();
        if (profiles != null) profiles.saveAllBlocking();
        Bukkit.getServicesManager().unregisterAll(this);
        if (core != null) {
            try { core.modules().unregister("jobs"); } catch (RuntimeException ignored) { }
        }
        if (payouts != null && payouts.totalPending() > 0) {
            getLogger().severe("PlexonJobs disabled with " + payouts.totalPending() + " minor money units still pending; review diagnostics before removing the database/runtime.");
        }
    }

    public synchronized void reloadJobs() {
        ConfigLoader.Loaded loaded = new ConfigLoader(this).load();
        JobsConfig old = runtime.config();
        JobsConfig next = loaded.config();
        if (old.moneyScale() != next.moneyScale() && pendingLedger.totalPending() > 0) {
            throw new IllegalStateException("money-scale cannot change while pending payouts exist");
        }
        if (!old.resetZone().equals(next.resetZone()) || old.defaultMoneyCapMinor() != next.defaultMoneyCapMinor() || old.defaultXpCap() != next.defaultXpCap()) {
            throw new IllegalStateException("daily limit/timezone changes require a restart to preserve current-day counters");
        }
        closeBlockSubscription();
        cancelTasks();
        unregisterApiService();
        if (expansion != null) {
            expansion.unregister();
            expansion = null;
        }
        installRuntime(loaded, false);
        registerApiService();
        registerPlaceholderApi();
        scheduleRuntimeTasks();
        updateModuleState();
    }

    private void installRuntime(ConfigLoader.Loaded loaded, boolean registerService) {
        JobRegistry registry = new JobRegistry(loaded.definitions());
        JobsConfig config = loaded.config();
        if (!ModuleRegistry.ModuleVersionRange.parse(config.coreApiRange()).contains(core.version())) {
            throw new IllegalStateException("Configured Core range " + config.coreApiRange() + " rejects API " + core.version().apiVersion());
        }
        if (limits == null) limits = new DailyLimitService(config.resetZone(), config.defaultMoneyCapMinor(), config.defaultXpCap());
        payouts = new PayoutService(this, economy, pendingLedger, metrics, config.moneyScale(), config.retryLimit());
        runtime = new JobsRuntime(profiles, registry, config, payouts);
        blockRouter = new BlockActivityRouter(config, registry, profiles, limits, payouts, shadow, metrics);
        subscribeBlockBreaks(registry);
        if (registerService) registerApiService();
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

    private void subscribeBlockBreaks(JobRegistry registry) {
        if (registry.breakMaterials().isEmpty()) return;
        blockSubscription = core.events().subscribeBlockBreak("plexonjobs",
                CoreBlockSubscription.builder().materials(registry.breakMaterials()).requiresNaturalOrigin(true).build(),
                context -> blockRouter.handle(context));
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

    private void registerPlaceholderApi() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) return;
        try {
            expansion = new PlexonJobsExpansion(runtime, limits);
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
        core.modules().updateState("jobs", moduleState(), moduleDetail());
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
            if (cfg.payoutMode() == JobsConfig.PayoutMode.COALESCED) payouts.flush(cfg.maxCommitsPerTick());
            updateModuleState();
        }, cfg.payoutFlushTicks(), cfg.payoutFlushTicks()));
        tasks.add(Bukkit.getScheduler().runTaskTimer(this, () -> {
            List<UUID> online = Bukkit.getOnlinePlayers().stream().map(player -> player.getUniqueId()).toList();
            profiles.sweepOnline(online);
            profiles.flushDirty();
            flushShadowAsync();
        }, cfg.saveIntervalTicks(), cfg.saveIntervalTicks()));
    }

    private void flushShadowAsync() {
        var batch = shadow.drain();
        if (batch.isEmpty()) return;
        core.scheduler().runIo(() -> batch.forEach((key, total) -> database.addShadow(key.playerId(), key.jobId(), total.moneyMinor(), total.xp(), total.events())))
                .whenComplete((unused, error) -> {
                    if (error != null) {
                        batch.forEach((key, total) -> shadow.addAggregate(key.playerId(), key.jobId(),
                                total.moneyMinor(), total.xp(), total.events()));
                        getLogger().log(Level.SEVERE, "Failed to persist shadow aggregate; batch returned to memory", error);
                    }
                });
    }

    private void flushShadowBlocking() {
        var batch = shadow.drain();
        batch.forEach((key, total) -> {
            try { database.addShadow(key.playerId(), key.jobId(), total.moneyMinor(), total.xp(), total.events()); }
            catch (RuntimeException failure) { getLogger().log(Level.SEVERE, "Failed to persist shutdown shadow totals for " + key.playerId(), failure); }
        });
    }

    private void cancelTasks() {
        for (BukkitTask task : tasks) task.cancel();
        tasks.clear();
    }

    private void closeBlockSubscription() {
        if (blockSubscription == null) return;
        try { blockSubscription.close(); }
        catch (Exception failure) { getLogger().log(Level.WARNING, "Failed to close Core block subscription", failure); }
        blockSubscription = null;
    }

    public PlexonCoreAPI core() { return core; }
    public JobsRuntime runtime() { return runtime; }
    public DailyLimitService limits() { return limits; }
    public PayoutService payouts() { return payouts; }
    public ShadowLedger shadow() { return shadow; }
    public JobsMetrics metrics() { return metrics; }
    public LegacyJobsMigration migration() { return migration; }
}
