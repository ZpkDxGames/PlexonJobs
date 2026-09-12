package com.plexon.jobs.runtime;

import com.plexon.jobs.PlexonJobs;
import com.plexon.jobs.config.FeedbackConfig;
import com.plexon.jobs.model.JobDefinition;
import com.plexon.jobs.model.XpCurve;
import com.plexon.jobs.util.Money;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Direct feedback sink. Reward callbacks only mutate compact memory; one global flush renders dirty players.
 */
public final class PlayerFeedbackService implements RewardFeedbackSink {
    private static final long REWARD_SOUND_COOLDOWN_NANOS = 250_000_000L;
    private final PlexonJobs plugin;
    private final Map<UUID, FeedbackState> states = new HashMap<>();
    private Map<String, Component> displayNames = Map.of();
    private int dirtyPlayers;

    public PlayerFeedbackService(PlexonJobs plugin) {
        this.plugin = plugin;
    }

    public void refreshCache() {
        if (plugin.runtime() == null || plugin.messages() == null) {
            displayNames = Map.of();
            return;
        }
        Map<String, Component> next = new HashMap<>();
        for (JobDefinition job : plugin.runtime().registry().definitions()) {
            next.put(job.id(), plugin.messages().parse(job.displayName()));
        }
        displayNames = Map.copyOf(next);
    }

    @Override
    public void onReward(RewardFeedback reward) {
        FeedbackConfig config = plugin.runtime().config().feedback();
        if (!config.enabled()) return;
        long now = System.nanoTime();
        FeedbackState state = states.computeIfAbsent(reward.playerId(), ignored -> new FeedbackState());
        if (state.jobId != null && !state.jobId.equals(reward.jobId())) {
            state.xpDelta = 0L;
            state.moneyDelta = 0L;
        }
        state.jobId = reward.jobId();
        state.xpDelta = saturatingAdd(state.xpDelta, reward.jobXp());
        state.moneyDelta = saturatingAdd(state.moneyDelta, reward.moneyMinor());
        state.totalXp = reward.totalXp();
        state.level = reward.level();
        state.expiryNanos = now + config.bossBarDurationTicks() * 50_000_000L;
        state.soundPending |= config.rewardSoundEnabled();
        if (!state.dirty) {
            state.dirty = true;
            dirtyPlayers++;
            plugin.metrics().feedbackDirtyPlayers(dirtyPlayers);
        }
    }

    /** Called by the single plugin-level feedback task. */
    public void flush() {
        long now = System.nanoTime();
        Iterator<Map.Entry<UUID, FeedbackState>> iterator = states.entrySet().iterator();
        boolean gaugeChanged = false;
        while (iterator.hasNext()) {
            Map.Entry<UUID, FeedbackState> entry = iterator.next();
            FeedbackState state = entry.getValue();
            Player player = plugin.getServer().getPlayer(entry.getKey());
            if (player == null || !player.isOnline() || !allowed(player) || now >= state.expiryNanos) {
                if (player != null && state.bar != null) player.hideBossBar(state.bar);
                if (state.dirty) {
                    dirtyPlayers = Math.max(0, dirtyPlayers - 1);
                    gaugeChanged = true;
                }
                iterator.remove();
                continue;
            }
            if (!state.dirty) continue;
            render(player, state, now);
            state.dirty = false;
            dirtyPlayers = Math.max(0, dirtyPlayers - 1);
            gaugeChanged = true;
            plugin.metrics().feedbackVisualFlush();
        }
        if (gaugeChanged) plugin.metrics().feedbackDirtyPlayers(dirtyPlayers);
    }

    @Override
    public void onLevelUp(LevelUpFeedback event) {
        Player player = plugin.getServer().getPlayer(event.playerId());
        if (!allowed(player)) return;
        FeedbackConfig config = plugin.runtime().config().feedback();
        Component job = displayNames.getOrDefault(event.jobId(), Component.text(event.jobId()));
        if (config.levelTitleEnabled()) {
            Component title = plugin.messages().renderBare("feedback.level-up-title",
                    Placeholder.component("job", job),
                    Placeholder.unparsed("level", Integer.toString(event.newLevel())));
            Component subtitle = plugin.messages().renderBare("feedback.level-up-subtitle",
                    Placeholder.component("job", job),
                    Placeholder.unparsed("old", Integer.toString(event.oldLevel())),
                    Placeholder.unparsed("level", Integer.toString(event.newLevel())));
            player.showTitle(Title.title(title, subtitle,
                    Title.Times.times(Duration.ofMillis(250), Duration.ofMillis(1_500), Duration.ofMillis(400))));
        }
        if (config.levelSoundEnabled()) player.playSound(sound(config.levelSound(), config.levelVolume(), config.levelPitch()));
    }

    private void render(Player player, FeedbackState state, long now) {
        FeedbackConfig config = plugin.runtime().config().feedback();
        JobDefinition jobDefinition = plugin.runtime().registry().find(state.jobId).orElse(null);
        if (jobDefinition == null) return;
        Component job = displayNames.getOrDefault(state.jobId, Component.text(state.jobId));
        if (config.bossBarEnabled()) {
            Component title = plugin.messages().renderBare("feedback.bossbar",
                    Placeholder.component("job", job),
                    Placeholder.unparsed("xp", Long.toString(state.xpDelta)),
                    Placeholder.unparsed("money", Money.format(state.moneyDelta, plugin.runtime().config().moneyScale())),
                    Placeholder.unparsed("level", Integer.toString(state.level)));
            float progress = progress(plugin.runtime().registry().curve(state.jobId), state.totalXp, state.level);
            if (state.bar == null) {
                state.bar = BossBar.bossBar(title, progress, BossBar.Color.GREEN, BossBar.Overlay.PROGRESS);
                player.showBossBar(state.bar);
            } else {
                state.bar.name(title);
                state.bar.progress(progress);
                state.bar.color(BossBar.Color.GREEN);
            }
        }
        if (state.soundPending && now - state.lastRewardSoundNanos >= REWARD_SOUND_COOLDOWN_NANOS) {
            player.playSound(sound(config.rewardSound(), config.rewardVolume(), config.rewardPitch()));
            state.lastRewardSoundNanos = now;
        }
        state.soundPending = false;
        state.xpDelta = 0L;
        state.moneyDelta = 0L;
    }

    public void hideAll() {
        states.forEach((playerId, state) -> {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player != null && state.bar != null) player.hideBossBar(state.bar);
        });
        states.clear();
        dirtyPlayers = 0;
        plugin.metrics().feedbackDirtyPlayers(0);
    }

    public int activeBars() { return states.size(); }

    private boolean allowed(Player player) {
        return player != null && player.isOnline() && plugin.runtime() != null
                && plugin.runtime().config().feedback().enabled()
                && player.hasPermission("plexonjobs.feedback");
    }

    private static float progress(XpCurve curve, long totalXp, int level) {
        if (level >= curve.maxLevel()) return 1.0f;
        long start = curve.totalXpForLevel(level);
        long end = curve.totalXpForLevel(level + 1);
        if (end <= start) return 1.0f;
        double ratio = (double) Math.max(0, totalXp - start) / (double) (end - start);
        return (float) Math.max(0.0, Math.min(1.0, ratio));
    }

    private static Sound sound(String key, float volume, float pitch) {
        return Sound.sound(Key.key(key), Sound.Source.PLAYER, volume, pitch);
    }

    private static long saturatingAdd(long left, long right) {
        if (right <= 0) return left;
        if (left > Long.MAX_VALUE - right) return Long.MAX_VALUE;
        return left + right;
    }

    private static final class FeedbackState {
        private String jobId;
        private long xpDelta;
        private long moneyDelta;
        private long totalXp;
        private int level;
        private long expiryNanos;
        private long lastRewardSoundNanos;
        private boolean soundPending;
        private boolean dirty;
        private BossBar bar;
    }
}
