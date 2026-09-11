package com.plexon.jobs.integration;

import com.plexon.jobs.model.JobDefinition;
import com.plexon.jobs.model.JobProgress;
import com.plexon.jobs.model.PlayerJobsProfile;
import com.plexon.jobs.runtime.DailyLimitService;
import com.plexon.jobs.runtime.JobsRuntime;
import com.plexon.jobs.util.Money;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

public final class PlexonJobsExpansion extends PlaceholderExpansion {
    private static final String[] JOB_FIELDS = {"earned_today", "xp_next", "progress", "joined", "level", "xp"};
    private final JobsRuntime runtime;
    private final DailyLimitService limits;

    public PlexonJobsExpansion(JobsRuntime runtime, DailyLimitService limits) {
        this.runtime = runtime;
        this.limits = limits;
    }

    @Override public @NotNull String getIdentifier() { return "plexonjobs"; }
    @Override public @NotNull String getAuthor() { return "ZpkDxGames"; }
    @Override public @NotNull String getVersion() { return "1.0.0"; }
    @Override public boolean persist() { return true; }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) return "";
        PlayerJobsProfile profile = runtime.profiles().get(player.getUniqueId());
        if (profile == null || profile.state() != PlayerJobsProfile.State.READY) return "";
        String key = params.toLowerCase(Locale.ROOT);
        if (key.equals("active_count")) return Long.toString(profile.activeCount());
        if (key.equals("active_list")) return String.join(", ", profile.activeJobIds());
        if (key.equals("total_earned_today")) {
            return Money.format(limits.total(player.getUniqueId()).moneyMinor(), runtime.config().moneyScale());
        }

        for (String field : JOB_FIELDS) {
            String suffix = "_" + field;
            if (!key.endsWith(suffix) || key.length() <= suffix.length()) continue;
            String jobId = key.substring(0, key.length() - suffix.length());
            JobDefinition job = runtime.registry().find(jobId).orElse(null);
            if (job == null) return null;
            JobProgress progress = profile.jobs().get(job.id());
            long xp = progress == null ? 0 : progress.totalXp();
            int level = progress == null ? 1 : progress.level();
            boolean joined = progress != null && progress.joined();
            return switch (field) {
                case "joined" -> Boolean.toString(joined);
                case "level" -> Integer.toString(level);
                case "xp" -> Long.toString(xp);
                case "xp_next" -> Long.toString(runtime.registry().curve(job.id()).xpToNextLevel(xp));
                case "progress" -> progressPercent(job.id(), xp, level);
                case "earned_today" -> Money.format(limits.view(player.getUniqueId(), job.id()).moneyMinor(), runtime.config().moneyScale());
                default -> null;
            };
        }
        return null;
    }

    private String progressPercent(String jobId, long xp, int level) {
        var curve = runtime.registry().curve(jobId);
        if (level >= curve.maxLevel()) return "100";
        long start = curve.totalXpForLevel(level);
        long end = curve.totalXpForLevel(level + 1);
        if (end <= start) return "100";
        double pct = (xp - start) * 100.0 / (end - start);
        return String.format(Locale.ROOT, "%.1f", Math.max(0, Math.min(100, pct)));
    }
}
