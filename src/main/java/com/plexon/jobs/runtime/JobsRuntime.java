package com.plexon.jobs.runtime;

import com.plexon.jobs.api.PlexonJobsAPI;
import com.plexon.jobs.config.JobsConfig;
import com.plexon.jobs.economy.PayoutService;
import com.plexon.jobs.event.PlexonJobJoinEvent;
import com.plexon.jobs.event.PlexonJobLeaveEvent;
import com.plexon.jobs.event.PlexonJobLevelUpEvent;
import com.plexon.jobs.event.PlexonJobXpGainEvent;
import com.plexon.jobs.model.JobDefinition;
import com.plexon.jobs.model.JobProgress;
import com.plexon.jobs.model.PlayerJobsProfile;
import org.bukkit.Bukkit;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class JobsRuntime implements PlexonJobsAPI {
    private final ProfileManager profiles;
    private final JobRegistry registry;
    private final JobsConfig config;
    private final PayoutService payouts;
    private final ActivityInterestIndex interest;

    public JobsRuntime(ProfileManager profiles, JobRegistry registry, JobsConfig config,
                       PayoutService payouts, ActivityInterestIndex interest) {
        this.profiles = profiles;
        this.registry = registry;
        this.config = config;
        this.payouts = payouts;
        this.interest = interest;
    }

    public ProfileManager profiles() { return profiles; }
    public JobRegistry registry() { return registry; }
    public JobsConfig config() { return config; }
    public PayoutService payouts() { return payouts; }

    @Override
    public Optional<PlayerJobsView> profile(UUID playerId) {
        PlayerJobsProfile profile = profiles.get(playerId);
        if (profile == null || profile.state() != PlayerJobsProfile.State.READY) return Optional.empty();
        Map<String, PlayerJobView> jobs = new LinkedHashMap<>();
        for (JobDefinition definition : registry.definitions()) {
            JobProgress progress = profile.jobs().get(definition.id());
            if (progress == null) progress = new JobProgress(0, 1, false);
            jobs.put(definition.id(), new PlayerJobView(definition.id(), progress.joined(), progress.level(),
                    progress.totalXp(), registry.curve(definition.id()).xpToNextLevel(progress.totalXp())));
        }
        return Optional.of(new PlayerJobsView(playerId, Map.copyOf(jobs)));
    }

    @Override public Collection<String> activeJobs(UUID playerId) {
        PlayerJobsProfile profile = profiles.get(playerId);
        return profile == null || profile.state() != PlayerJobsProfile.State.READY ? java.util.List.of() : profile.activeJobIds();
    }

    @Override public boolean isInJob(UUID playerId, String jobId) {
        PlayerJobsProfile profile = profiles.get(playerId);
        if (profile == null || profile.state() != PlayerJobsProfile.State.READY) return false;
        JobProgress progress = profile.jobs().get(normalize(jobId));
        return progress != null && progress.joined();
    }

    @Override public int level(UUID playerId, String jobId) {
        PlayerJobsProfile profile = profiles.get(playerId);
        JobProgress progress = profile == null ? null : profile.jobs().get(normalize(jobId));
        return progress == null ? 1 : progress.level();
    }

    @Override public long totalXp(UUID playerId, String jobId) {
        PlayerJobsProfile profile = profiles.get(playerId);
        JobProgress progress = profile == null ? null : profile.jobs().get(normalize(jobId));
        return progress == null ? 0 : progress.totalXp();
    }

    @Override
    public Result joinJob(UUID playerId, String jobId) {
        if (!Bukkit.isPrimaryThread()) return Result.fail("joinJob must run on the primary thread");
        String id = normalize(jobId);
        JobDefinition definition = registry.find(id).orElse(null);
        if (definition == null) return Result.fail("Unknown job: " + jobId);
        if (!definition.enabled()) return Result.fail("Job is currently disabled: " + id);
        PlayerJobsProfile profile = profiles.ensure(playerId);
        if (profile.state() != PlayerJobsProfile.State.READY) return Result.fail("Jobs profile is still loading");
        JobProgress progress = profile.progress(id);
        if (progress.joined()) return Result.fail("Already joined " + id);
        if (profile.activeCount() >= maxJobs(playerId)) return Result.fail("Maximum active jobs reached");
        progress.joined(true);
        profiles.markDirty(playerId);
        interest.refresh(playerId);
        Bukkit.getPluginManager().callEvent(new PlexonJobJoinEvent(playerId, id));
        return Result.ok("Joined " + id);
    }

    @Override
    public Result leaveJob(UUID playerId, String jobId) {
        if (!Bukkit.isPrimaryThread()) return Result.fail("leaveJob must run on the primary thread");
        String id = normalize(jobId);
        PlayerJobsProfile profile = profiles.ensure(playerId);
        if (profile.state() != PlayerJobsProfile.State.READY) return Result.fail("Jobs profile is still loading");
        JobProgress progress = profile.jobs().get(id);
        if (progress == null || !progress.joined()) return Result.fail("Not joined: " + id);
        progress.joined(false);
        if (!config.keepLevelOnLeave()) progress.setTotalXp(0, registry.curve(id));
        profiles.markDirty(playerId);
        interest.refresh(playerId);
        Bukkit.getPluginManager().callEvent(new PlexonJobLeaveEvent(playerId, id));
        return Result.ok("Left " + id);
    }

    @Override
    public Result addJobXp(UUID playerId, String jobId, long amount, String source) {
        if (!Bukkit.isPrimaryThread()) return Result.fail("addJobXp must run on the primary thread");
        if (amount < 0) return Result.fail("XP amount must be >= 0");
        String id = normalize(jobId);
        JobDefinition definition = registry.find(id).orElse(null);
        if (definition == null) return Result.fail("Unknown job: " + id);
        PlayerJobsProfile profile = profiles.ensure(playerId);
        if (profile.state() != PlayerJobsProfile.State.READY) return Result.fail("Jobs profile is still loading");
        if (amount == 0) return Result.ok("XP unchanged");
        JobProgress progress = profile.progress(id);
        JobProgress.ProgressDelta delta = progress.addXp(amount, registry.curve(id));
        profiles.markDirty(playerId);
        Bukkit.getPluginManager().callEvent(new PlexonJobXpGainEvent(playerId, id, amount, progress.totalXp(), source));
        if (delta.leveledUp()) Bukkit.getPluginManager().callEvent(new PlexonJobLevelUpEvent(playerId, id,
                delta.oldLevel(), delta.newLevel(), delta.totalXp(), source));
        return Result.ok("XP updated");
    }

    @Override public Collection<JobView> jobDefinitions() {
        return registry.definitions().stream().map(job -> new JobView(job.id(), job.displayName(), job.icon().name(), job.enabled(), job.maxLevel())).toList();
    }

    @Override public long pendingPayout(UUID playerId) { return payouts.pending(playerId); }

    public int maxJobs(UUID playerId) {
        var player = Bukkit.getPlayer(playerId);
        if (player != null && player.hasPermission("plexonjobs.maxjobs.unlimited")) return Integer.MAX_VALUE;
        int max = config.defaultMaxJobs();
        if (player != null) for (int i = 1; i <= 64; i++) if (player.hasPermission("plexonjobs.maxjobs." + i)) max = Math.max(max, i);
        return max;
    }

    private static String normalize(String jobId) {
        return jobId == null ? "" : jobId.trim().toLowerCase(Locale.ROOT);
    }
}
