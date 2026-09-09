package com.plexon.jobs.command;

import com.plexon.jobs.PlexonJobs;
import com.plexon.jobs.migration.LegacyJobsMigration;
import com.plexon.jobs.util.Money;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;

import java.util.List;
import java.util.Locale;

public final class JobsAdminCommand implements TabExecutor {
    private final PlexonJobs plugin;

    public JobsAdminCommand(PlexonJobs plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("plexonjobs.admin")) { sender.sendMessage(ChatColor.RED + "No permission."); return true; }
        if (args.length == 0) return usage(sender);
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "diagnostics" -> diagnostics(sender);
            case "reload" -> {
                try {
                    plugin.reloadJobs();
                    sender.sendMessage(ChatColor.GREEN + "PlexonJobs runtime configuration reloaded atomically.");
                } catch (RuntimeException ex) {
                    sender.sendMessage(ChatColor.RED + "Reload rejected; previous runtime remains active: " + ex.getMessage());
                }
            }
            case "payout" -> {
                if (args.length >= 2 && args[1].equalsIgnoreCase("retry")) {
                    plugin.payouts().retryAll();
                    int committed = plugin.payouts().flush(plugin.runtime().config().maxCommitsPerTick());
                    sender.sendMessage(ChatColor.YELLOW + "Retry gate reset; committed " + committed + " payout batch(es) now.");
                } else sender.sendMessage(ChatColor.YELLOW + "/jobsadmin payout retry");
            }
            case "migration" -> migration(sender, args);
            case "simulate" -> simulate(sender, args);
            case "backup" -> sender.sendMessage(ChatColor.YELLOW + "Stop/flush PlexonJobs and copy plugins/PlexonJobs/jobs.db plus YAML configuration. See docs/RECOVERY.md.");
            default -> usage(sender);
        }
        return true;
    }

    private void diagnostics(CommandSender sender) {
        var runtime = plugin.runtime();
        var metrics = plugin.metrics().snapshot();
        sender.sendMessage(ChatColor.GOLD + "PlexonJobs diagnostics");
        sender.sendMessage(ChatColor.GRAY + "Mode: " + runtime.config().mode() + " | Core: " + plugin.core().version().pluginVersion() + " API " + plugin.core().version().apiVersion());
        sender.sendMessage(ChatColor.GRAY + "Jobs: " + runtime.registry().definitions().size() + " | Core break materials: " + runtime.registry().breakMaterials().size());
        sender.sendMessage(ChatColor.GRAY + "Profiles: cached=" + runtime.profiles().onlineCached() + ", loading=" + runtime.profiles().loadingCount() + ", dirty=" + runtime.profiles().dirtyCount());
        sender.sendMessage(ChatColor.GRAY + "Economy: available=" + plugin.payouts().economyAvailable() + ", pendingPlayers=" + plugin.payouts().pendingPlayers() +
                ", pending=" + Money.format(plugin.payouts().totalPending(), runtime.config().moneyScale()) + ", oldestMs=" + plugin.payouts().oldestPendingAgeMillis() +
                ", retryBlocked=" + plugin.payouts().blockedPlayers());
        sender.sendMessage(ChatColor.GRAY + "Activities: callbacks=" + metrics.callbacks() + ", fastRejects=" + metrics.fastRejects() + ", originRejects=" + metrics.originRejects() +
                ", eligible=" + metrics.eligible() + ", capped=" + metrics.capped());
        sender.sendMessage(ChatColor.GRAY + "Shadow: moneyMinor=" + metrics.shadowMoneyMinor() + ", xp=" + metrics.shadowXp() + " | shadowBuffer=" + plugin.shadow().size());
        sender.sendMessage(ChatColor.GRAY + "Persistence: Core IO queue=" + plugin.core().scheduler().ioQueueSize() + " | day=" + plugin.limits().day());
        sender.sendMessage(ChatColor.GRAY + "Core gateway routes=" + plugin.core().events().compiledBlockRoutes() + " | metrics=" + plugin.core().events().metrics());
    }

    private void migration(CommandSender sender, String[] args) {
        LegacyJobsMigration migration = plugin.migration();
        String action = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "status";
        switch (action) {
            case "scan" -> {
                var scan = migration.scan();
                sender.sendMessage(ChatColor.YELLOW + "Source: " + scan.sourcePath() + " | exists=" + scan.exists() + " | candidates=" + scan.candidates().size());
                scan.candidates().forEach(path -> sender.sendMessage(ChatColor.GRAY + "- " + path));
            }
            case "plan", "status" -> sender.sendMessage(ChatColor.YELLOW + migration.plan());
            case "execute" -> sender.sendMessage(ChatColor.RED + "Migration execute is fail-closed in this candidate until the administrator's actual Jobs schema is inspected and a staging backup dry-run passes.");
            default -> sender.sendMessage(ChatColor.YELLOW + "/jobsadmin migration <scan|plan|status|execute>");
        }
    }

    private void simulate(CommandSender sender, String[] args) {
        if (args.length < 4) { sender.sendMessage(ChatColor.YELLOW + "/jobsadmin simulate <job> <material> <count>"); return; }
        var job = plugin.runtime().registry().find(args[1]).orElse(null);
        Material material = Material.matchMaterial(args[2]);
        if (job == null || material == null) { sender.sendMessage(ChatColor.RED + "Unknown job or material."); return; }
        long count;
        try { count = Math.max(0, Long.parseLong(args[3])); } catch (NumberFormatException ex) { sender.sendMessage(ChatColor.RED + "Count must be an integer."); return; }
        var reward = job.breakReward(material);
        long money = Math.multiplyExact(reward.moneyMinorUnits(), count);
        long xp = Math.multiplyExact(reward.jobXpUnits(), count);
        sender.sendMessage(ChatColor.GOLD + job.id() + " / " + material + " x" + count + ": " +
                ChatColor.GREEN + Money.format(money, plugin.runtime().config().moneyScale()) + ChatColor.GRAY + " money, " + xp + " job XP before daily caps.");
    }

    private static boolean usage(CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "/jobsadmin <diagnostics|reload|payout retry|migration|simulate|backup>");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return List.of("diagnostics", "reload", "payout", "migration", "simulate", "backup").stream().filter(v -> v.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        if (args.length == 2 && args[0].equalsIgnoreCase("migration")) return List.of("scan", "plan", "status", "execute");
        if (args.length == 2 && args[0].equalsIgnoreCase("payout")) return List.of("retry");
        if (args.length == 2 && args[0].equalsIgnoreCase("simulate")) return plugin.runtime().registry().definitions().stream().map(d -> d.id()).toList();
        return List.of();
    }
}
