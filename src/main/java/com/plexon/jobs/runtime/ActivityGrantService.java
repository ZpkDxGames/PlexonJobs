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
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Compiled 2.5 reward pipeline. Gameplay callbacks consult pre-hydrated execution state and never
 * perform SQL, Vault commits, YAML parsing, task creation, or membership reconstruction.
 */
public final class ActivityGrantService {
    private final JobsConfig config;
    private final JobRegistry registry;
    private final CompiledJobRoutes routes;
    private final ActivityInterestIndex interest;
    private final ProfileManager profiles;
    private final DailyLimitService limits;
    private final PayoutService payouts;
    private final ShadowLedger shadow;
    private final JobsMetrics metrics;
    private final RewardFeedbackSink feedback;

    public ActivityGrantService(JobsConfig config, JobRegistry registry, CompiledJobRoutes routes,
                                ActivityInterestIndex interest, ProfileManager profiles,
                                DailyLimitService limits, PayoutService payouts, ShadowLedger shadow,
                                JobsMetrics metrics, RewardFeedbackSink feedback) {
        this.config = Objects.requireNonNull(config);
        this.registry = Objects.requireNonNull(registry);
        this.routes = Objects.requireNonNull(routes);
        this.interest = Objects.requireNonNull(interest);
        this.profiles = Objects.requireNonNull(profiles);
        this.limits = Objects.requireNonNull(limits);
        this.payouts = Objects.requireNonNull(payouts);
        this.shadow = Objects.requireNonNull(shadow);
        this.metrics = Objects.requireNonNull(metrics);
        this.feedback = Objects.requireNonNullElse(feedback, RewardFeedbackSink.NOOP);
    }

    public Outcome handle(Player player, ActivityType activity, String key, long units, String source) {
        metrics.eventSeen();
        if (player == null || units <= 0 || config.mode() == RuntimeMode.DISABLED) {
            metrics.fastReject();
            return Outcome.REJECTED;
        }
        CompiledJobRoutes.CompiledRoute route = routes.route(activity, key);
        if (route.jobMask() == 0L) {
            metrics.rejectedNoGlobalInterest();
            return Outcome.REJECTED;
        }
        UUID playerId = player.getUniqueId();
        PlayerExecutionState state = interest.state(playerId);
        long matched = state.joinedJobMask() & route.jobMask();
        if (matched == 0L) {
            metrics.rejectedNoPlayerInterest();
            return Outcome.REJECTED;
        }
        if (!state.rewardReady()) {
            metrics.rejectedNotReady();
            return new Outcome(true, false);
        }
        return grantRoute(player, activity, route, matched, units, source);
    }

    /** Specialized typed BREAK path; the caller supplies the already-resolved route/mask. */
    public Outcome handleBreak(Player player, Material material, CompiledJobRoutes.CompiledRoute route,
                               long matchedJobMask, long eventId) {
        if (player == null || route == null || matchedJobMask == 0L) return Outcome.REJECTED;
        return grantRoute(player, ActivityType.BREAK, route, matchedJobMask, 1L, "core:block:" + eventId);
    }

    public boolean hasJoinedRoute(UUID playerId, ActivityType activity, String key) {
        CompiledJobRoutes.CompiledRoute route = routes.route(activity, key);
        return (interest.state(playerId).joinedJobMask() & route.jobMask()) != 0L;
    }

    public void originReject() { metrics.rejectedOrigin(); }
    public void fastReject() { metrics.fastReject(); }

    private Outcome grantRoute(Player player, ActivityType activity, CompiledJobRoutes.CompiledRoute route,
                               long matchedMask, long units, String source) {
        if (config.disabledWorlds().contains(player.getWorld().getName().toUpperCase(Locale.ROOT))) {
            metrics.fastReject();
            return new Outcome(true, false);
        }
        if (!config.allowedGameModes().isEmpty() && !config.allowedGameModes().contains(player.getGameMode().name())) {
            metrics.fastReject();
            return new Outcome(true, false);
        }

        UUID playerId = player.getUniqueId();
        PlayerJobsProfile profile = profiles.get(playerId);
        if (profile == null || profile.state() != PlayerJobsProfile.State.READY) {
            metrics.rejectedNotReady();
            return new Outcome(true, false);
        }

        boolean granted = false;
        long remaining = matchedMask;
        while (remaining != 0L) {
            int bit = Long.numberOfTrailingZeros(remaining);
            remaining &= remaining - 1L;
            JobDefinition job = routes.job(bit);
            JobProgress progress = profile.jobs().get(job.id());
            if (progress == null || !progress.joined()) continue; // defensive stale-state guard
            ActivityReward reward = scale(route.reward(bit), units);
            if (reward.empty()) continue;
            metrics.routeMatch();
            metrics.calculated();

            if (config.mode() == RuntimeMode.SHADOW) {
                shadow.add(playerId, job.id(), reward.moneyMinorUnits(), reward.jobXpUnits());
                metrics.shadow(reward.moneyMinorUnits(), reward.jobXpUnits());
                granted = true;
                continue;
            }

            String safeSource = source == null || source.isBlank()
                    ? "native:" + activity.name().toLowerCase(Locale.ROOT) : source;
            long xp = reward.jobXpUnits();
            long money = reward.moneyMinorUnits();
            if (hasPayoutListeners()) {
                PlexonJobPayoutEvent calculated = new PlexonJobPayoutEvent(playerId, job.id(), activity.name(), xp, money, safeSource);
                Bukkit.getPluginManager().callEvent(calculated);
                metrics.customEventDispatched();
                if (calculated.isCancelled()) continue;
                xp = calculated.jobXp();
                money = calculated.moneyMinor();
            } else {
                metrics.customEventSkipped();
            }

            DailyLimitService.Clamped clamped = limits.clamp(playerId, job.id(), money, xp);
            if (clamped.capped()) metrics.capped();
            if (clamped.moneyMinor() <= 0 && clamped.xp() <= 0) continue;

            limits.commit(playerId, job.id(), clamped.moneyMinor(), clamped.xp());
            JobProgress.ProgressDelta delta = null;
            if (clamped.xp() > 0) {
                delta = progress.addXp(clamped.xp(), registry.curve(job.id()));
                profiles.markDirty(playerId);
                if (hasXpListeners()) {
                    Bukkit.getPluginManager().callEvent(new PlexonJobXpGainEvent(playerId, job.id(),
                            clamped.xp(), progress.totalXp(), safeSource));
                    metrics.customEventDispatched();
                } else metrics.customEventSkipped();
                if (delta.leveledUp()) {
                    feedback.onLevelUp(new RewardFeedbackSink.LevelUpFeedback(playerId, job.id(),
                            delta.oldLevel(), delta.newLevel(), delta.totalXp()));
                    if (hasLevelListeners()) {
                        Bukkit.getPluginManager().callEvent(new PlexonJobLevelUpEvent(playerId, job.id(),
                                delta.oldLevel(), delta.newLevel(), delta.totalXp(), safeSource));
                        metrics.customEventDispatched();
                    } else metrics.customEventSkipped();
                }
            }
            if (clamped.moneyMinor() > 0) payouts.accrue(playerId, clamped.moneyMinor());

            feedback.onReward(new RewardFeedbackSink.RewardFeedback(playerId, job.id(), clamped.xp(),
                    clamped.moneyMinor(), progress.totalXp(), progress.level()));
            metrics.feedbackAccumulation();
            if (hasRewardListeners()) {
                Bukkit.getPluginManager().callEvent(new PlexonJobRewardGrantedEvent(
                        playerId, job.id(), activity.name(), clamped.xp(), clamped.moneyMinor(),
                        progress.totalXp(), progress.level(), registry.curve(job.id()).xpToNextLevel(progress.totalXp()), safeSource));
                metrics.customEventDispatched();
            } else metrics.customEventSkipped();
            metrics.grantCommitted();
            granted = true;
        }
        return new Outcome(true, granted);
    }

    private static boolean hasPayoutListeners() {
        return PlexonJobPayoutEvent.getHandlerList().getRegisteredListeners().length != 0;
    }
    private static boolean hasXpListeners() {
        return PlexonJobXpGainEvent.getHandlerList().getRegisteredListeners().length != 0;
    }
    private static boolean hasLevelListeners() {
        return PlexonJobLevelUpEvent.getHandlerList().getRegisteredListeners().length != 0;
    }
    private static boolean hasRewardListeners() {
        return PlexonJobRewardGrantedEvent.getHandlerList().getRegisteredListeners().length != 0;
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
