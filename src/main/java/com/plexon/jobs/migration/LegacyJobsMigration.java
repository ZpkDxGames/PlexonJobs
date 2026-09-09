package com.plexon.jobs.migration;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public final class LegacyJobsMigration {
    private final JavaPlugin plugin;

    public LegacyJobsMigration(JavaPlugin plugin) { this.plugin = plugin; }

    public ScanResult scan() {
        File configFile = new File(plugin.getDataFolder(), "migration.yml");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        String raw = config.getString("legacy-jobs.source-path", "plugins/Jobs");
        File source = new File(raw);
        if (!source.isAbsolute()) source = new File(plugin.getServer().getWorldContainer(), raw);
        List<String> candidates = new ArrayList<>();
        if (source.isDirectory()) {
            File[] files = source.listFiles();
            if (files != null) for (File file : files) {
                String name = file.getName().toLowerCase(java.util.Locale.ROOT);
                if (name.endsWith(".db") || name.endsWith(".sqlite") || name.endsWith(".sqlite3") ||
                        name.endsWith(".yml") || name.endsWith(".yaml")) candidates.add(file.getAbsolutePath());
            }
        } else if (source.isFile()) candidates.add(source.getAbsolutePath());
        return new ScanResult(source.getAbsolutePath(), source.exists(), List.copyOf(candidates));
    }

    public String plan() {
        ScanResult scan = scan();
        if (!scan.exists()) return "Legacy Jobs source not found at " + scan.sourcePath();
        if (scan.candidates().isEmpty()) return "Source exists but no candidate data files were found.";
        return "Found " + scan.candidates().size() + " candidate legacy data file(s). Schema inspection is required before execute; no source file will be mutated.";
    }

    public record ScanResult(String sourcePath, boolean exists, List<String> candidates) {}
}
