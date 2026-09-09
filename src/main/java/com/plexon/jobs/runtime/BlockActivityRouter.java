package com.plexon.jobs.runtime;

import com.plexon.jobs.config.JobsConfig;
import com.plexon.jobs.economy.PayoutService;
import com.plexon.jobs.event.PlexonJobLevelUpEvent;
import com.plexon.jobs.event.PlexonJobPayoutEvent;
import com.plexon.jobs.event.PlexonJobXpGainEvent;
import com.plexon.jobs.model.ActivityReward;
import com.plexon.jobs.model.JobDefinition;
import com.plexon.jobs.model.JobProgress;
import com.plexon.jobs.model.PlayerJobsProfile;
import com.zpkdxgames.plexoncore.context.BlockOrigin;
import com.zpkdxgames.plexoncore.context.CoreBlockBreakContext;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class BlockActivityRouter {
    private final JobsConfig config;
    private final JobRegistry registry;
    private final ProfileManager profiles;
    private final DailyLimitService limits;
    private final PayoutService payouts;
    private final ShadowLedger shadow;
    private final JobsMetrics metrics;

    public BlockActivityRouter(JobsConfig config, JobRegistry registry, ProfileManager profiles,
                               DailyLimitService limits, PayoutService payouts, ShadowLedger shadow,
                               JobsMetrics metrics) {
        this.config = config;
        this.registry = registry;
        this.profiles = profiles;
        this.limits = limits;
        this.payouts = payouts;
        this.shadow = shadow;
        this.metrics = metrics;
    }

    public void handle(CoreBlockBreakContext context) {
        metrics.callback();
        if (config.mode() == RuntimeMode.DISABLED) { metrics.fastReject(); return; }
        var routes = registry.breakJobs(context.material());
        if (routes.isEmpty()) { metrics.fastReject(); return; }
        if (context.origin() != BlockOrigin.NATURAL) { metrics.originReject(); return; }
        if (config.disabledWorlds().contains(context.worldName().toUpperCase(java.util.Locale.ROOT))) { metrics.fastReject(); return; }

        Player player = Bukkit.getPlayer(context.playerId());
        if (player == null) { metrics.fastReject(); return; }
        if (!config.allowedGameModes().isEmpty() && !config.allowedGameModes().contains(player.getGameMode().name())) {
            metrics.fastReject(); return;
        }

        PlayerJobsProfile profile = profiles.ensure(context.playerId());
        if (profile.state() != PlayerJobsProfile.State.READY || profile.activeCount() == 0) {
            metrics.fastReject(); return;
        }

        for (JobDefinition job : routes) {
            JobProgress progress = profile.jobs().get(job.id());
            if (progress == null || !progress.joined()) continue;
            ActivityReward base = job.breakReward(context.material());
            if (base == null || base.empty()) continue;
            metrics.eligible();
            metrics.calculated();

            if (config.mode() == RuntimeMode.SHADOW) {
                shadow.add(context.playerId(), job.id(), base.moneyMinorUnits(), base.jobXpUnits());
                metrics.shadow(base.moneyMinorUnits(), base.jobXpUnits());
                continue;
            }

            PlexonJobPayoutEvent calculated = new PlexonJobPayoutEvent(context.playerId(), job.id(),
                    "BREAK", base.jobXpUnits(), base.moneyMinorUnits(), "core:block:" + context.eventId());
            Bukkit.getPluginManager().callEvent(calculated);
            if (calculated.isCancelled()) continue;

            DailyLimitService.Clamped clamped = limits.clamp(context.playerId(), job.id(),
                    calculated.moneyMinor(), calculated.jobXp());
            if (clamped.capped()) metrics.capped();
            if (clamped.moneyMinor() <= 0 && clamped.xp() <= 0) continue;

            limits.commit(context.playerId(), job.id(), clamped.moneyMinor(), clamped.xp());
            if (clamped.xp() > 0) {
                JobProgress.ProgressDelta delta = progress.addXp(clamped.xp(), registry.curve(job.id()));
                profiles.markDirty(context.playerId());
                Bukkit.getPluginManager().callEvent(new PlexonJobXpGainEvent(context.playerId(), job.id(),
                        clamped.xp(), progress.totalXp(), "core:block:" + context.eventId()));
                if (delta.leveledUp()) {
                    Bukkit.getPluginManager().callEvent(new PlexonJobLevelUpEvent(context.playerId(), job.id(),
                            delta.oldLevel(), delta.newLevel(), delta.totalXp(), "core:block:" + context.eventId()));
                }
            }
            if (clamped.moneyMinor() > 0) {
                payouts.accrue(context.playerId(), clamped.moneyMinor());
                if (config.payoutMode() == JobsConfig.PayoutMode.IMMEDIATE) payouts.flush(1);
            }
        }
    }
}
