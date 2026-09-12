package com.plexon.jobs.runtime;

import com.plexon.jobs.config.JobsConfig;
import com.plexon.jobs.economy.PayoutService;
import com.plexon.jobs.event.PlexonJobLevelUpEvent;
import com.plexon.jobs.event.PlexonJobPayoutEvent;
import com.plexon.jobs.event.PlexonJobRewardGrantedEvent;
import com.plexon.jobs.event.PlexonJobXpGainEvent;
import com.plexon.jobs.model.ActivityReward;
import com.plexon.jobs.model.ActivityType;
import com.plexon.jobs.model.JobDefinition;
import com.plexon.jobs.model.JobProgress;
import com.plexon.jobs.model.PlayerJobsProfile;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Authoritative 2.0 reward pipeline shared by Core-owned block activity and native Paper activity listeners.
 * Gameplay callbacks remain memory-only: no SQL, Vault commit, YAML parsing, or task creation occurs here.
 */
public final class ActivityGrantService {
    private final JobsConfig config;
    private final JobRegistry registry;
    private final ProfileManager profiles;
    private final DailyLimitService limits;
    private final DailyLimitPersistence dailyPersistence;
    private final PayoutService payouts;
    private final ShadowLedger shadow;
    private final JobsMetrics metrics;

    public ActivityGrantService(JobsConfig config, JobRegistry registry, ProfileManager profiles,
                                DailyLimitService limits, DailyLimitPersistence dailyPersistence,
                                PayoutService payouts, ShadowLedger shadow, JobsMetrics metrics) {
        this.config = Objects.requireNonNull(config);
        this.registry = Objects.requireNonNull(registry);
        this.profiles = Objects.requireNonNull(profiles);
        this.limits = Objects.requireNonNull(limits);
        this.dailyPersistence = Objects.requireNonNull(dailyPersistence);
        this.payouts = Objects.requireNonNull(payouts);
        this.shadow = Objects.requireNonNull(shadow);
        this.metrics = Objects.requireNonNull(metrics);
    }

    public Outcome handle(Player player, ActivityType activity, String key, long units, String source) {
        metrics.callback();
        if (player == null || units <= 0 || config.mode() == RuntimeMode.DISABLED) {
            metrics.fastReject();
            return Outcome.REJECTED;
        }
        List<JobDefinition> routes = registry.activityJobs(activity, key);
        if (routes.isEmpty()) {
            metrics.fastReject();
            return Outcome.REJECTED;
        }
        if (config.disabledWorlds().contains(player.getWorld().getName().toUpperCase(Locale.ROOT))) {
            metrics.fastReject();
            return Outcome.REJECTED;
        }
        if (!config.allowedGameModes().isEmpty() && !config.allowedGameModes().contains(player.getGameMode().name())) {
            metrics.fastReject();
            return Outcome.REJECTED;
        }

        UUID playerId = player.getUniqueId();
        PlayerJobsProfile profile = profiles.ensure(playerId);
        if (profile.state() != PlayerJobsProfile.State.READY || profile.activeCount() == 0) {
            metrics.fastReject();
            return Outcome.REJECTED;
        }
        if (config.mode() == RuntimeMode.PRIMARY && !dailyPersistence.ensure(playerId)) {
            metrics.fastReject();
            return Outcome.REJECTED;
        }

        boolean matchedMembership = false;
        boolean granted = false;
        for (JobDefinition job : routes) {
            JobProgress progress = profile.jobs().get(job.id());
            if (progress == null || !progress.joined()) continue;
            matchedMembership = true;
            ActivityReward base = job.reward(activity, key);
            ActivityReward reward = scale(base, units);
            if (reward.empty()) continue;
            metrics.eligible();
            metrics.calculated();

            if (config.mode() == RuntimeMode.SHADOW) {
                shadow.add(playerId, job.id(), reward.moneyMinorUnits(), reward.jobXpUnits());
                metrics.shadow(reward.moneyMinorUnits(), reward.jobXpUnits());
                granted = true;
                continue;
            }

            String safeSource = source == null || source.isBlank() ? "native:" + activity.name().toLowerCase(Locale.ROOT) : source;
            PlexonJobPayoutEvent calculated = new PlexonJobPayoutEvent(playerId, job.id(), activity.name(),
                    reward.jobXpUnits(), reward.moneyMinorUnits(), safeSource);
            Bukkit.getPluginManager().callEvent(calculated);
            if (calculated.isCancelled()) continue;

            DailyLimitService.Clamped clamped = limits.clamp(playerId, job.id(), calculated.moneyMinor(), calculated.jobXp());
            if (clamped.capped()) metrics.capped();
            if (clamped.moneyMinor() <= 0 && clamped.xp() <= 0) continue;

            limits.commit(playerId, job.id(), clamped.moneyMinor(), clamped.xp());
            JobProgress.ProgressDelta delta = null;
            if (clamped.xp() > 0) {
                delta = progress.addXp(clamped.xp(), registry.curve(job.id()));
                profiles.markDirty(playerId);
                Bukkit.getPluginManager().callEvent(new PlexonJobXpGainEvent(playerId, job.id(),
                        clamped.xp(), progress.totalXp(), safeSource));
                if (delta.leveledUp()) {
                    Bukkit.getPluginManager().callEvent(new PlexonJobLevelUpEvent(playerId, job.id(),
                            delta.oldLevel(), delta.newLevel(), delta.totalXp(), safeSource));
                }
            }
            if (clamped.moneyMinor() > 0) payouts.accrue(playerId, clamped.moneyMinor());

            Bukkit.getPluginManager().callEvent(new PlexonJobRewardGrantedEvent(
                    playerId, job.id(), activity.name(), clamped.xp(), clamped.moneyMinor(),
                    progress.totalXp(), progress.level(), registry.curve(job.id()).xpToNextLevel(progress.totalXp()), safeSource));
            granted = true;
        }
        return new Outcome(matchedMembership, granted);
    }

    public boolean hasJoinedRoute(UUID playerId, ActivityType activity, String key) {
        PlayerJobsProfile profile = profiles.get(playerId);
        if (profile == null || profile.state() != PlayerJobsProfile.State.READY) return false;
        for (JobDefinition job : registry.activityJobs(activity, key)) {
            JobProgress progress = profile.jobs().get(job.id());
            if (progress != null && progress.joined()) return true;
        }
        return false;
    }

    public void originReject() {
        metrics.callback();
        metrics.originReject();
    }

    public void fastReject() {
        metrics.callback();
        metrics.fastReject();
    }

    private static ActivityReward scale(ActivityReward base, long units) {
        if (base == null || base.empty() || units <= 0) return ActivityReward.ZERO;
        return new ActivityReward(saturatingMultiply(base.jobXpUnits(), units),
                saturatingMultiply(base.moneyMinorUnits(), units));
    }

    private static long saturatingMultiply(long value, long multiplier) {
        if (value <= 0 || multiplier <= 0) return 0;
        if (value > Long.MAX_VALUE / multiplier) return Long.MAX_VALUE;
        return value * multiplier;
    }

    public record Outcome(boolean matchedMembership, boolean granted) {
        public static final Outcome REJECTED = new Outcome(false, false);
    }
}
