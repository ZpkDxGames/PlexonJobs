package com.plexon.jobs.runtime;

import com.plexon.jobs.PlexonJobs;
import com.plexon.jobs.config.FeedbackConfig;
import com.plexon.jobs.event.PlexonJobLevelUpEvent;
import com.plexon.jobs.event.PlexonJobRewardGrantedEvent;
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
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.time.Duration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** One mutable BossBar per active player; reward callbacks only mutate in-memory feedback state. */
public final class PlayerFeedbackService implements Listener {
    private static final long REWARD_SOUND_COOLDOWN_NANOS = 250_000_000L;
    private final PlexonJobs plugin;
    private final Map<UUID, FeedbackState> states = new HashMap<>();

    public PlayerFeedbackService(PlexonJobs plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onReward(PlexonJobRewardGrantedEvent event) {
        Player player = plugin.getServer().getPlayer(event.playerId());
        if (!allowed(player)) return;
        FeedbackConfig config = plugin.runtime().config().feedback();
        JobDefinition job = plugin.runtime().registry().find(event.jobId()).orElse(null);
        if (job == null) return;

        long now = System.nanoTime();
        FeedbackState state = states.computeIfAbsent(event.playerId(), ignored -> new FeedbackState());
        if (!event.jobId().equals(state.jobId) || now >= state.expiresAtNanos) {
            state.jobId = event.jobId();
            state.accumulatedXp = 0;
            state.accumulatedMoney = 0;
        }
        state.accumulatedXp = saturatingAdd(state.accumulatedXp, event.jobXp());
        state.accumulatedMoney = saturatingAdd(state.accumulatedMoney, event.moneyMinor());
        state.expiresAtNanos = now + config.bossBarDurationTicks() * 50_000_000L;

        if (config.bossBarEnabled()) {
            Component title = plugin.messages().renderBare("feedback.bossbar",
                    Placeholder.component("job", plugin.messages().parse(job.displayName())),
                    Placeholder.unparsed("xp", Long.toString(state.accumulatedXp)),
                    Placeholder.unparsed("money", Money.format(state.accumulatedMoney, plugin.runtime().config().moneyScale())),
                    Placeholder.unparsed("level", Integer.toString(event.level())));
            float progress = progress(plugin.runtime().registry().curve(event.jobId()), event.totalXp(), event.level());
            if (state.bar == null) {
                state.bar = BossBar.bossBar(title, progress, BossBar.Color.GREEN, BossBar.Overlay.PROGRESS);
                player.showBossBar(state.bar);
            } else {
                state.bar.name(title);
                state.bar.progress(progress);
                state.bar.color(BossBar.Color.GREEN);
            }
        }

        if (config.rewardSoundEnabled() && now - state.lastRewardSoundNanos >= REWARD_SOUND_COOLDOWN_NANOS) {
            player.playSound(sound(config.rewardSound(), config.rewardVolume(), config.rewardPitch()));
            state.lastRewardSoundNanos = now;
        }
    }

    @EventHandler
    public void onLevelUp(PlexonJobLevelUpEvent event) {
        Player player = plugin.getServer().getPlayer(event.playerId());
        if (!allowed(player)) return;
        FeedbackConfig config = plugin.runtime().config().feedback();
        JobDefinition job = plugin.runtime().registry().find(event.jobId()).orElse(null);
        if (job == null) return;

        if (config.levelTitleEnabled()) {
            Component title = plugin.messages().renderBare("feedback.level-up-title",
                    Placeholder.component("job", plugin.messages().parse(job.displayName())),
                    Placeholder.unparsed("level", Integer.toString(event.newLevel())));
            Component subtitle = plugin.messages().renderBare("feedback.level-up-subtitle",
                    Placeholder.component("job", plugin.messages().parse(job.displayName())),
                    Placeholder.unparsed("old", Integer.toString(event.oldLevel())),
                    Placeholder.unparsed("level", Integer.toString(event.newLevel())));
            player.showTitle(Title.title(title, subtitle,
                    Title.Times.times(Duration.ofMillis(250), Duration.ofMillis(1_500), Duration.ofMillis(400))));
        }
        if (config.levelSoundEnabled()) {
            player.playSound(sound(config.levelSound(), config.levelVolume(), config.levelPitch()));
        }
    }

    /** Called by one global plugin task; never scheduled per reward. */
    public void tick() {
        long now = System.nanoTime();
        Iterator<Map.Entry<UUID, FeedbackState>> iterator = states.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, FeedbackState> entry = iterator.next();
            FeedbackState state = entry.getValue();
            Player player = plugin.getServer().getPlayer(entry.getKey());
            if (player == null || !player.isOnline() || now >= state.expiresAtNanos || !allowed(player)) {
                if (player != null && state.bar != null) player.hideBossBar(state.bar);
                iterator.remove();
            }
        }
    }

    public void hideAll() {
        states.forEach((playerId, state) -> {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player != null && state.bar != null) player.hideBossBar(state.bar);
        });
        states.clear();
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
        private String jobId = "";
        private long accumulatedXp;
        private long accumulatedMoney;
        private long expiresAtNanos;
        private long lastRewardSoundNanos;
        private BossBar bar;
    }
}
