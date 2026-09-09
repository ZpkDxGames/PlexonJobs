package com.plexon.jobs.command;

import com.plexon.jobs.PlexonJobs;
import com.plexon.jobs.api.PlexonJobsAPI;
import com.plexon.jobs.gui.JobsGui;
import com.plexon.jobs.model.JobProgress;
import com.plexon.jobs.model.PlayerJobsProfile;
import com.plexon.jobs.util.Money;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class JobsCommand implements TabExecutor {
    private final PlexonJobs plugin;

    public JobsCommand(PlexonJobs plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("PlexonJobs player commands must be run in game.");
            return true;
        }
        var runtime = plugin.runtime();
        if (args.length == 0 || args[0].equalsIgnoreCase("browse")) {
            new JobsGui(runtime, plugin.limits()).open(player);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "join" -> {
                if (args.length < 2) return usage(player, "/jobs join <job>");
                sendResult(player, runtime.joinJob(player.getUniqueId(), args[1]));
            }
            case "leave" -> {
                if (args.length < 2) return usage(player, "/jobs leave <job>");
                sendResult(player, runtime.leaveJob(player.getUniqueId(), args[1]));
            }
            case "leaveall" -> {
                for (String id : List.copyOf(runtime.activeJobs(player.getUniqueId()))) runtime.leaveJob(player.getUniqueId(), id);
                player.sendMessage(ChatColor.YELLOW + "Left all active jobs.");
            }
            case "info" -> {
                if (args.length < 2) return usage(player, "/jobs info <job>");
                var definition = runtime.registry().find(args[1]).orElse(null);
                if (definition == null) { player.sendMessage(ChatColor.RED + "Unknown job."); return true; }
                player.sendMessage(ChatColor.GOLD + stripMini(definition.displayName()) + ChatColor.GRAY + " (" + definition.id() + ")");
                player.sendMessage(ChatColor.GRAY + "Enabled: " + definition.enabled() + ", max level: " + definition.maxLevel());
                player.sendMessage(ChatColor.GRAY + "Core-native break rules: " + definition.breakRewards().size());
            }
            case "stats" -> showStats(player, args.length >= 2 ? Bukkit.getPlayerExact(args[1]) : player);
            case "earnings" -> showEarnings(player);
            case "top" -> player.sendMessage(ChatColor.YELLOW + "Leaderboard cache is reserved for a later candidate; no synchronous DB sort is performed.");
            default -> usage(player, "/jobs [browse|info|join|leave|leaveall|stats|earnings]");
        }
        return true;
    }

    private void showStats(Player viewer, Player target) {
        if (target == null) { viewer.sendMessage(ChatColor.RED + "Player not found online."); return; }
        PlayerJobsProfile profile = plugin.runtime().profiles().ensure(target.getUniqueId());
        if (profile.state() != PlayerJobsProfile.State.READY) { viewer.sendMessage(ChatColor.YELLOW + "Jobs profile is still loading."); return; }
        viewer.sendMessage(ChatColor.GOLD + "Jobs stats for " + target.getName());
        if (profile.jobs().isEmpty()) { viewer.sendMessage(ChatColor.GRAY + "No job progression yet."); return; }
        profile.jobs().forEach((id, progress) -> viewer.sendMessage(ChatColor.GRAY + id + ": " +
                (progress.joined() ? ChatColor.GREEN + "joined" : ChatColor.DARK_GRAY + "inactive") +
                ChatColor.GRAY + ", level " + progress.level() + ", XP " + progress.totalXp()));
    }

    private void showEarnings(Player player) {
        long total = 0;
        for (var job : plugin.runtime().registry().definitions()) {
            long amount = plugin.limits().view(player.getUniqueId(), job.id()).moneyMinor();
            if (amount > 0) player.sendMessage(ChatColor.GRAY + job.id() + ": " + ChatColor.GREEN + Money.format(amount, plugin.runtime().config().moneyScale()));
            total = Math.addExact(total, amount);
        }
        player.sendMessage(ChatColor.GOLD + "Today: " + ChatColor.GREEN + Money.format(total, plugin.runtime().config().moneyScale()) +
                ChatColor.GRAY + " | pending: " + Money.format(plugin.runtime().pendingPayout(player.getUniqueId()), plugin.runtime().config().moneyScale()));
    }

    private static void sendResult(Player player, PlexonJobsAPI.Result result) {
        player.sendMessage((result.success() ? ChatColor.GREEN : ChatColor.RED) + result.message());
    }

    private static boolean usage(Player player, String text) { player.sendMessage(ChatColor.YELLOW + text); return true; }
    private static String stripMini(String input) { return input == null ? "Job" : input.replaceAll("<[^>]+>", ""); }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return filter(List.of("browse", "info", "join", "leave", "leaveall", "stats", "earnings", "top"), args[0]);
        if (args.length == 2 && List.of("info", "join", "leave", "top").contains(args[0].toLowerCase(Locale.ROOT))) {
            return filter(plugin.runtime().registry().definitions().stream().map(d -> d.id()).toList(), args[1]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> values, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(p)).toList();
    }
}
