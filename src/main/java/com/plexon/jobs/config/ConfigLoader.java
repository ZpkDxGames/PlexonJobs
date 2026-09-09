package com.plexon.jobs.config;

import com.plexon.jobs.model.ActivityReward;
import com.plexon.jobs.model.JobDefinition;
import com.plexon.jobs.runtime.RuntimeMode;
import com.plexon.jobs.util.Money;
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

        var cfg = plugin.getConfig();
        int moneyScale = Math.max(0, Math.min(6, cfg.getInt("payout.money-scale", 2)));
        BigDecimal moneyCap = parseDecimal(cfg.getString("limits.default-money-per-day", "0"));
        JobsConfig jobsConfig = new JobsConfig(
                RuntimeMode.parse(cfg.getString("runtime.mode")),
                cfg.getString("runtime.core-api-range", ">=2.0 <3.0"),
                Math.max(1, cfg.getInt("membership.default-max-jobs", 3)),
                cfg.getBoolean("membership.keep-level-on-leave", true),
                JobsConfig.payoutMode(cfg.getString("payout.mode", "COALESCED")),
                Math.max(1, cfg.getInt("payout.flush-ticks", 40)),
                Math.max(1, cfg.getInt("payout.max-commits-per-tick", 100)),
                Math.max(0, cfg.getInt("payout.retry-limit", 3)),
                moneyScale,
                Money.toMinor(moneyCap, moneyScale),
                Math.max(0L, cfg.getLong("limits.default-xp-per-day", 0L)),
                Math.max(20, cfg.getInt("profiles.save-interval-ticks", 200)),
                parseZone(cfg.getString("limits.reset-timezone", "UTC")),
                normalizeSet(cfg.getStringList("gameplay.allowed-game-modes")),
                normalizeSet(cfg.getStringList("gameplay.disabled-worlds"))
        );

        YamlConfiguration jobsYaml = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "jobs.yml"));
        ConfigurationSection jobs = jobsYaml.getConfigurationSection("jobs");
        if (jobs == null) throw new IllegalStateException("jobs.yml has no jobs section");

        List<JobDefinition> definitions = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (String rawId : jobs.getKeys(false)) {
            String id = rawId.toLowerCase(Locale.ROOT);
            if (!id.matches("[a-z0-9._-]{1,64}")) throw new IllegalArgumentException("Invalid job id: " + rawId);
            if (!ids.add(id)) throw new IllegalArgumentException("Duplicate job id: " + id);
            ConfigurationSection section = jobs.getConfigurationSection(rawId);
            if (section == null) continue;
            String display = section.getString("display-name", id);
            Material icon = requireMaterial(section.getString("icon", "PAPER"));
            boolean enabled = section.getBoolean("enabled", true);
            int maxLevel = Math.max(1, section.getInt("max-level", 200));
            Map<Material, ActivityReward> breakRewards = new EnumMap<>(Material.class);
            ConfigurationSection breakSection = section.getConfigurationSection("break");
            if (breakSection != null) {
                for (String materialName : breakSection.getKeys(false)) {
                    Material material = requireMaterial(materialName);
                    ConfigurationSection reward = breakSection.getConfigurationSection(materialName);
                    if (reward == null) throw new IllegalArgumentException("Reward for " + id + "/" + materialName + " must be a section");
                    long money = Money.toMinor(parseDecimal(reward.getString("money", "0")), moneyScale);
                    long xp = Math.max(0L, reward.getLong("xp", 0L));
                    breakRewards.put(material, new ActivityReward(xp, money));
                }
            }
            definitions.add(new JobDefinition(id, display, icon, enabled, maxLevel, breakRewards));
        }
        return new Loaded(jobsConfig, definitions);
    }

    private void saveIfMissing(String name) {
        File target = new File(plugin.getDataFolder(), name);
        if (!target.exists()) plugin.saveResource(name, false);
    }

    private static Material requireMaterial(String value) {
        Material material = Material.matchMaterial(value == null ? "" : value);
        if (material == null) throw new IllegalArgumentException("Unknown material: " + value);
        return material;
    }

    private static BigDecimal parseDecimal(String raw) {
        try { return new BigDecimal(raw == null ? "0" : raw.trim()); }
        catch (NumberFormatException ex) { throw new IllegalArgumentException("Invalid decimal: " + raw, ex); }
    }

    private static ZoneId parseZone(String raw) {
        try { return ZoneId.of(raw); }
        catch (Exception ex) { throw new IllegalArgumentException("Invalid reset timezone: " + raw, ex); }
    }

    private static Set<String> normalizeSet(List<String> values) {
        Set<String> set = new HashSet<>();
        for (String value : values) set.add(value.toUpperCase(Locale.ROOT));
        return Set.copyOf(set);
    }

    public record Loaded(JobsConfig config, List<JobDefinition> definitions) {}
}
