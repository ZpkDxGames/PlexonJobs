package com.plexon.jobs.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable player-facing message snapshot. Existing installations may have an older messages.yml,
 * so newly introduced keys intentionally fall back to embedded defaults instead of making an upgrade
 * fail merely because saveResource(..., false) preserved the administrator's existing file.
 */
public final class Messages {
    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final Map<String, String> DEFAULTS = defaults();

    private final Map<String, String> templates;
    private final String prefix;

    private Messages(Map<String, String> templates) {
        this.templates = Map.copyOf(templates);
        this.prefix = this.templates.getOrDefault("prefix", "");
    }

    public static Messages load(File file) {
        Objects.requireNonNull(file, "file");
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
        } catch (Exception failure) {
            throw new IllegalArgumentException("Invalid YAML in messages.yml; current runtime remains unchanged", failure);
        }

        Map<String, String> resolved = new LinkedHashMap<>(DEFAULTS);
        flatten(yaml, "", resolved);
        return new Messages(resolved);
    }

    public Component render(String key, TagResolver... resolvers) {
        String template = templates.get(key);
        if (template == null) template = "<red>Missing message: " + key + "</red>";
        return MINI.deserialize(prefix + template, resolvers);
    }

    public Component renderBare(String key, TagResolver... resolvers) {
        String template = templates.get(key);
        if (template == null) template = "<red>Missing message: " + key + "</red>";
        return MINI.deserialize(template, resolvers);
    }

    public Component parse(String template, TagResolver... resolvers) {
        return MINI.deserialize(template == null ? "" : template, resolvers);
    }

    public String template(String key) {
        return templates.getOrDefault(key, "");
    }

    private static void flatten(ConfigurationSection section, String prefix, Map<String, String> out) {
        for (String key : section.getKeys(false)) {
            String path = prefix.isEmpty() ? key : prefix + "." + key;
            Object raw = section.get(key);
            if (raw instanceof ConfigurationSection child) {
                flatten(child, path, out);
            } else if (raw instanceof String value) {
                out.put(path, value);
            } else if (raw != null) {
                throw new IllegalArgumentException("messages.yml value " + path + " must be a string");
            }
        }
    }

    private static Map<String, String> defaults() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("prefix", "<dark_gray>[<gold>PlexonJobs</gold>]</dark_gray> ");
        map.put("profile-loading", "<yellow>Your Jobs profile is still loading.</yellow>");
        map.put("joined", "<green>Joined <job>.</green>");
        map.put("left", "<yellow>Left <job>.</yellow>");
        map.put("already-joined", "<red>You are already in that job.</red>");
        map.put("not-joined", "<red>You are not in that job.</red>");
        map.put("max-jobs", "<red>You have reached your active job limit.</red>");
        map.put("unknown-job", "<red>Unknown job.</red>");
        map.put("job-disabled", "<red>That job is not currently available.</red>");
        map.put("left-all", "<yellow>Left all active jobs.</yellow>");
        map.put("no-permission", "<red>You do not have permission to do that.</red>");
        map.put("player-only", "<red>This command must be run in game.</red>");
        map.put("player-not-found", "<red>Player not found online.</red>");
        map.put("no-progression", "<gray>No job progression yet.</gray>");
        map.put("usage", "<yellow><usage></yellow>");
        map.put("daily-state-loading", "<yellow>Your daily Jobs state is still loading. Try again in a moment.</yellow>");
        map.put("menu.title", "<gold>PlexonJobs</gold> <dark_gray>•</dark_gray> <gray>Jobs</gray>");
        map.put("menu.details-title", "<gold><job></gold> <dark_gray>•</dark_gray> <gray>Details</gray>");
        map.put("menu.confirm-leave-title", "<red>Confirm leaving <job></red>");
        map.put("menu.available", "<green>Available</green>");
        map.put("menu.disabled", "<red>Unavailable</red>");
        map.put("menu.joined", "<green>Active</green>");
        map.put("menu.inactive", "<gray>Not joined</gray>");
        map.put("menu.loading", "<yellow>Loading profile…</yellow>");
        map.put("menu.open-details", "<aqua>Left-click: View details</aqua>");
        map.put("menu.back", "<yellow>Back</yellow>");
        map.put("menu.close", "<red>Close</red>");
        map.put("menu.join", "<green>Join job</green>");
        map.put("menu.leave", "<yellow>Leave job</yellow>");
        map.put("menu.confirm-leave", "<red>Confirm leave</red>");
        map.put("menu.cancel", "<gray>Cancel</gray>");
        map.put("menu.disabled-detail", "<gray>This job stays disabled until PlexonCore provides an authoritative activity context for it.</gray>");
        map.put("menu.reset-warning", "<red>Leaving resets this job's stored XP and level.</red>");
        map.put("menu.active-summary", "<gray>Active jobs: <white><active></white>/<white><max></white></gray>");
        return Map.copyOf(map);
    }
}
