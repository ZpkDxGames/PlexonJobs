package com.plexon.jobs.config;

import com.plexon.jobs.model.ActivityReward;
import com.plexon.jobs.model.JobDefinition;
import com.plexon.jobs.runtime.RuntimeMode;
import com.plexon.jobs.util.Money;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
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
        plugin.reloadConfig();

        ConfigurationSection cfg = plugin.getConfig();
        int moneyScale = integer(cfg, "payout.money-scale", 2, 0, 6);
        BigDecimal moneyCap = decimal(cfg, "limits.default-money-per-day", new BigDecimal("0"));
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

        JobsConfig jobsConfig = new JobsConfig(
                RuntimeMode.parse(string(cfg, "runtime.mode", "SHADOW")),
                nonBlank(string(cfg, "runtime.core-api-range", ">=2.0 <3.0"), "runtime.core-api-range"),
                integer(cfg, "membership.default-max-jobs", 3, 1, 64),
                bool(cfg, "membership.keep-level-on-leave", true),
                integer(cfg, "payout.flush-ticks", 40, 1, 72_000),
                integer(cfg, "payout.max-commits-per-tick", 100, 1, 10_000),
                integer(cfg, "payout.retry-limit", 3, 0, 100),
                moneyScale,
                Money.toMinor(moneyCap, moneyScale),
                longInteger(cfg, "limits.default-xp-per-day", 0L, 0L, Long.MAX_VALUE),
                integer(cfg, "profiles.save-interval-ticks", 200, 20, 72_000),
                parseZone(string(cfg, "limits.reset-timezone", "UTC")),
                gameModes,
                normalizedStringSet(cfg, "gameplay.disabled-worlds", List.of())
        );

        File jobsFile = new File(plugin.getDataFolder(), "jobs.yml");
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
            Map<Material, ActivityReward> breakRewards = new EnumMap<>(Material.class);
            Object rawBreak = section.get("break");
            if (rawBreak != null) {
                if (!(rawBreak instanceof ConfigurationSection breakSection)) {
                    throw new IllegalArgumentException("Job " + id + " break must be a map");
                }
                for (String materialName : breakSection.getKeys(false)) {
                    Material material = requireMaterial(materialName);
                    Object rawReward = breakSection.get(materialName);
                    if (!(rawReward instanceof ConfigurationSection reward)) {
                        throw new IllegalArgumentException("Reward for " + id + "/" + materialName + " must be a map");
                    }
                    BigDecimal rawMoney = decimal(reward, "money", BigDecimal.ZERO);
                    if (rawMoney.signum() < 0) throw new IllegalArgumentException("Money reward for " + id + "/" + materialName + " must be >= 0");
                    long money = Money.toMinor(rawMoney, moneyScale);
                    long xp = longInteger(reward, "xp", 0L, 0L, Long.MAX_VALUE);
                    breakRewards.put(material, new ActivityReward(xp, money));
                }
            }
            definitions.add(new JobDefinition(id, display, icon, enabled, maxLevel, breakRewards));
        }
        if (definitions.isEmpty()) throw new IllegalStateException("jobs.yml must define at least one job");
        return new Loaded(jobsConfig, List.copyOf(definitions));
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

    private static String nonBlank(String value, String path) {
        if (value == null || value.isBlank()) throw invalid(path, "must not be blank");
        return value.trim();
    }

    private static IllegalArgumentException invalid(String path, String detail) {
        return new IllegalArgumentException("Invalid configuration at " + path + ": " + detail);
    }

    public record Loaded(JobsConfig config, List<JobDefinition> definitions) {}
}
