package com.plexon.jobs.config;

import com.plexon.jobs.model.ActivityReward;
import com.plexon.jobs.model.ActivityType;
import com.plexon.jobs.model.JobDefinition;
import com.plexon.jobs.runtime.RuntimeMode;
import com.plexon.jobs.util.Money;
import net.kyori.adventure.key.Key;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ConfigLoader {
    private final JavaPlugin plugin;

    public ConfigLoader(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public Loaded load() {
        plugin.saveDefaultConfig();
        saveIfMissing("jobs.yml");
        saveIfMissing("messages.yml");
        saveIfMissing("migration.yml");

        File configFile = new File(plugin.getDataFolder(), "config.yml");
        validateYaml(configFile, "config.yml");
        plugin.reloadConfig();

        ConfigurationSection cfg = plugin.getConfig();
        int moneyScale = integer(cfg, "payout.money-scale", 2, 0, 6);
        BigDecimal moneyCap = decimal(cfg, "limits.default-money-per-day", new BigDecimal("5000.00"));
        if (moneyCap.signum() < 0) throw invalid("limits.default-money-per-day", "must be >= 0");
        String payoutMode = string(cfg, "payout.mode", "COALESCED").trim().toUpperCase(Locale.ROOT);
        if (!payoutMode.equals("COALESCED")) {
            throw invalid("payout.mode", "must be COALESCED; event-path Vault deposits are forbidden");
        }
        Set<String> gameModes = normalizedStringSet(cfg, "gameplay.allowed-game-modes", List.of("SURVIVAL"));
        for (String mode : gameModes) {
            try { GameMode.valueOf(mode); }
            catch (IllegalArgumentException ex) { throw invalid("gameplay.allowed-game-modes", "unknown game mode " + mode); }
        }

        Set<String> hunterReasons = normalizedStringSet(cfg, "activity.hunter.allowed-spawn-reasons",
                List.of("NATURAL", "RAID", "PATROL", "TRAP", "REINFORCEMENTS", "NETHER_PORTAL", "VILLAGE_INVASION"));
        for (String reason : hunterReasons) {
            try { CreatureSpawnEvent.SpawnReason.valueOf(reason); }
            catch (IllegalArgumentException ex) { throw invalid("activity.hunter.allowed-spawn-reasons", "unknown spawn reason " + reason); }
        }

        FeedbackConfig feedback = new FeedbackConfig(
                bool(cfg, "feedback.enabled", true),
                bool(cfg, "feedback.bossbar.enabled", true),
                integer(cfg, "feedback.bossbar.duration-ticks", 60, 10, 1_200),
                bool(cfg, "feedback.reward-sound.enabled", true),
                soundKey(cfg, "feedback.reward-sound.key", "minecraft:entity.experience_orb.pickup"),
                decimal(cfg, "feedback.reward-sound.volume", new BigDecimal("0.35")).floatValue(),
                decimal(cfg, "feedback.reward-sound.pitch", new BigDecimal("1.35")).floatValue(),
                bool(cfg, "feedback.level-up.title-enabled", true),
                bool(cfg, "feedback.level-up.sound-enabled", true),
                soundKey(cfg, "feedback.level-up.sound", "minecraft:entity.player.levelup"),
                decimal(cfg, "feedback.level-up.volume", new BigDecimal("0.8")).floatValue(),
                decimal(cfg, "feedback.level-up.pitch", new BigDecimal("1.1")).floatValue()
        );
        validateVolumePitch(feedback);

        JobsConfig jobsConfig = new JobsConfig(
                RuntimeMode.parse(string(cfg, "runtime.mode", "PRIMARY")),
                nonBlank(string(cfg, "runtime.core-api-range", ">=2.0 <3.0"), "runtime.core-api-range"),
                integer(cfg, "membership.default-max-jobs", 3, 1, 64),
                bool(cfg, "membership.keep-level-on-leave", true),
                integer(cfg, "payout.flush-ticks", 40, 1, 72_000),
                integer(cfg, "payout.max-commits-per-tick", 100, 1, 10_000),
                integer(cfg, "payout.retry-limit", 3, 0, 100),
                moneyScale,
                Money.toMinor(moneyCap, moneyScale),
                longInteger(cfg, "limits.default-xp-per-day", 250_000L, 0L, Long.MAX_VALUE),
                integer(cfg, "profiles.save-interval-ticks", 200, 20, 72_000),
                parseZone(string(cfg, "limits.reset-timezone", "America/Sao_Paulo")),
                gameModes,
                normalizedStringSet(cfg, "gameplay.disabled-worlds", List.of()),
                new ActivityConfig(
                        hunterReasons,
                        integer(cfg, "activity.builder.repeat-window-seconds", 600, 1, 86_400),
                        integer(cfg, "activity.builder.max-tracked-positions", 4_096, 128, 65_536),
                        integer(cfg, "activity.brewer.attribution-seconds", 90, 5, 600),
                        integer(cfg, "activity.explorer.sample-ticks", 40, 10, 1_200)
                ),
                feedback
        );

        File jobsFile = new File(plugin.getDataFolder(), "jobs.yml");
        validateYaml(jobsFile, "jobs.yml");
        YamlConfiguration jobsYaml = YamlConfiguration.loadConfiguration(jobsFile);
        ConfigurationSection jobs = jobsYaml.getConfigurationSection("jobs");
        if (jobs == null) throw new IllegalStateException("jobs.yml has no jobs map");

        List<JobDefinition> definitions = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (String rawId : jobs.getKeys(false)) {
            String id = rawId.toLowerCase(Locale.ROOT);
            if (!id.matches("[a-z0-9._-]{1,64}")) throw new IllegalArgumentException("Invalid job id: " + rawId);
            if (!ids.add(id)) throw new IllegalArgumentException("Duplicate job id: " + id);
            Object rawSection = jobs.get(rawId);
            if (!(rawSection instanceof ConfigurationSection section)) {
                throw new IllegalArgumentException("Job " + rawId + " must be a map");
            }
            String display = nonBlank(string(section, "display-name", id), id + ".display-name");
            Material icon = requireMaterial(string(section, "icon", "PAPER"));
            boolean enabled = bool(section, "enabled", true);
            int maxLevel = integer(section, "max-level", 200, 1, 10_000);
            Map<Material, ActivityReward> breakRewards = parseBreakRewards(section, id, moneyScale);
            Map<ActivityType, Map<String, ActivityReward>> activityRewards = parseActivityRewards(section, id, moneyScale);
            definitions.add(new JobDefinition(id, display, icon, enabled, maxLevel, breakRewards, activityRewards));
        }
        if (definitions.isEmpty()) throw new IllegalStateException("jobs.yml must define at least one job");

        File messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        validateYaml(messagesFile, "messages.yml");
        Messages messages = Messages.load(messagesFile);
        return new Loaded(jobsConfig, List.copyOf(definitions), messages);
    }

    private static Map<Material, ActivityReward> parseBreakRewards(ConfigurationSection job, String id, int moneyScale) {
        Map<Material, ActivityReward> rewards = new EnumMap<>(Material.class);
        Object rawBreak = job.get("break");
        if (rawBreak == null) return rewards;
        if (!(rawBreak instanceof ConfigurationSection section)) {
            throw new IllegalArgumentException("Job " + id + " break must be a map");
        }
        for (String materialName : section.getKeys(false)) {
            Material material = requireMaterial(materialName);
            rewards.put(material, parseReward(section, materialName, id + "/break/" + materialName, moneyScale));
        }
        return rewards;
    }

    private static Map<ActivityType, Map<String, ActivityReward>> parseActivityRewards(ConfigurationSection job, String id, int moneyScale) {
        EnumMap<ActivityType, Map<String, ActivityReward>> activities = new EnumMap<>(ActivityType.class);
        for (ActivityType type : ActivityType.values()) {
            if (type == ActivityType.BREAK || type == ActivityType.DAMAGE) continue;
            String path = type.name().toLowerCase(Locale.ROOT);
            Object raw = job.get(path);
            if (raw == null) continue;
            if (!(raw instanceof ConfigurationSection section)) {
                throw new IllegalArgumentException("Job " + id + " " + path + " must be a map");
            }
            Map<String, ActivityReward> keyed = new LinkedHashMap<>();
            for (String rawKey : section.getKeys(false)) {
                String key = normalizeActivityKey(rawKey, id, path);
                keyed.put(key, parseReward(section, rawKey, id + "/" + path + "/" + rawKey, moneyScale));
            }
            if (!keyed.isEmpty()) activities.put(type, Map.copyOf(keyed));
        }
        return activities;
    }

    private static ActivityReward parseReward(ConfigurationSection parent, String key, String label, int moneyScale) {
        Object rawReward = parent.get(key);
        if (!(rawReward instanceof ConfigurationSection reward)) {
            throw new IllegalArgumentException("Reward for " + label + " must be a map");
        }
        BigDecimal rawMoney = decimal(reward, "money", BigDecimal.ZERO);
        if (rawMoney.signum() < 0) throw new IllegalArgumentException("Money reward for " + label + " must be >= 0");
        long money = Money.toMinor(rawMoney, moneyScale);
        long xp = longInteger(reward, "xp", 0L, 0L, Long.MAX_VALUE);
        return new ActivityReward(xp, money);
    }

    private static String normalizeActivityKey(String raw, String jobId, String activity) {
        if (raw == null || raw.isBlank()) throw new IllegalArgumentException("Blank reward key for " + jobId + "/" + activity);
        String key = raw.trim().toUpperCase(Locale.ROOT);
        if (key.equals("*")) return key;
        if (!key.matches("[A-Z0-9_.:-]{1,128}")) {
            throw new IllegalArgumentException("Invalid reward key for " + jobId + "/" + activity + ": " + raw);
        }
        return key;
    }

    static void validateYaml(File file, String label) {
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
        } catch (IOException | InvalidConfigurationException failure) {
            throw new IllegalArgumentException("Invalid YAML in " + label + "; current runtime remains unchanged", failure);
        }
    }

    private void saveIfMissing(String name) {
        File target = new File(plugin.getDataFolder(), name);
        if (!target.exists()) plugin.saveResource(name, false);
    }

    private static String string(ConfigurationSection section, String path, String fallback) {
        Object raw = section.get(path);
        if (raw == null) return fallback;
        if (!(raw instanceof String value)) throw invalid(path, "must be a string");
        return value;
    }

    private static boolean bool(ConfigurationSection section, String path, boolean fallback) {
        Object raw = section.get(path);
        if (raw == null) return fallback;
        if (!(raw instanceof Boolean value)) throw invalid(path, "must be a boolean");
        return value;
    }

    private static int integer(ConfigurationSection section, String path, int fallback, int min, int max) {
        long value = longInteger(section, path, fallback, min, max);
        return Math.toIntExact(value);
    }

    private static long longInteger(ConfigurationSection section, String path, long fallback, long min, long max) {
        Object raw = section.get(path);
        if (raw == null) return fallback;
        if (!(raw instanceof Byte || raw instanceof Short || raw instanceof Integer || raw instanceof Long)) {
            throw invalid(path, "must be an integer");
        }
        long value = ((Number) raw).longValue();
        if (value < min || value > max) throw invalid(path, "must be between " + min + " and " + max);
        return value;
    }

    private static BigDecimal decimal(ConfigurationSection section, String path, BigDecimal fallback) {
        Object raw = section.get(path);
        if (raw == null) return fallback;
        try {
            if (raw instanceof Number number) return new BigDecimal(number.toString());
            if (raw instanceof String value) return new BigDecimal(value.trim());
        } catch (NumberFormatException ex) {
            throw invalid(path, "must be a decimal number");
        }
        throw invalid(path, "must be a decimal number");
    }

    private static Set<String> normalizedStringSet(ConfigurationSection section, String path, List<String> fallback) {
        Object raw = section.get(path);
        List<?> values;
        if (raw == null) values = fallback;
        else if (raw instanceof List<?> list) values = list;
        else throw invalid(path, "must be a list of strings");
        Set<String> set = new HashSet<>();
        for (Object item : values) {
            if (!(item instanceof String value)) throw invalid(path, "must contain only strings");
            String normalized = value.trim().toUpperCase(Locale.ROOT);
            if (normalized.isEmpty()) throw invalid(path, "must not contain blank values");
            set.add(normalized);
        }
        return Set.copyOf(set);
    }

    private static Material requireMaterial(String value) {
        Material material = Material.matchMaterial(value == null ? "" : value);
        if (material == null) throw new IllegalArgumentException("Unknown material: " + value);
        return material;
    }

    private static ZoneId parseZone(String raw) {
        try { return ZoneId.of(raw); }
        catch (Exception ex) { throw new IllegalArgumentException("Invalid reset timezone: " + raw, ex); }
    }

    private static String soundKey(ConfigurationSection cfg, String path, String fallback) {
        String raw = nonBlank(string(cfg, path, fallback), path);
        try { return Key.key(raw).asString(); }
        catch (IllegalArgumentException ex) { throw invalid(path, "must be a valid namespaced Adventure key"); }
    }

    private static void validateVolumePitch(FeedbackConfig feedback) {
        if (feedback.rewardVolume() < 0 || feedback.rewardVolume() > 4) throw invalid("feedback.reward-sound.volume", "must be between 0 and 4");
        if (feedback.rewardPitch() <= 0 || feedback.rewardPitch() > 2) throw invalid("feedback.reward-sound.pitch", "must be > 0 and <= 2");
        if (feedback.levelVolume() < 0 || feedback.levelVolume() > 4) throw invalid("feedback.level-up.volume", "must be between 0 and 4");
        if (feedback.levelPitch() <= 0 || feedback.levelPitch() > 2) throw invalid("feedback.level-up.pitch", "must be > 0 and <= 2");
    }

    private static String nonBlank(String value, String path) {
        if (value == null || value.isBlank()) throw invalid(path, "must not be blank");
        return value.trim();
    }

    private static IllegalArgumentException invalid(String path, String detail) {
        return new IllegalArgumentException("Invalid configuration at " + path + ": " + detail);
    }

    public record Loaded(JobsConfig config, List<JobDefinition> definitions, Messages messages) {}
}
