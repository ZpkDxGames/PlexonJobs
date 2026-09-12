package com.plexon.jobs.command;

import com.plexon.jobs.PlexonJobs;
import com.plexon.jobs.api.PlexonJobsAPI;
import com.plexon.jobs.model.PlayerJobsProfile;
import com.plexon.jobs.util.Money;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

public final class JobsCommand implements TabExecutor {
    private final PlexonJobs plugin;
    public JobsCommand(PlexonJobs plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.messages().render("player-only"));
            return true;
        }
        var runtime = plugin.runtime();
        if (args.length == 0) {
            plugin.menus().openDashboard(player);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (runtime.registry().find(sub).isPresent()) {
            plugin.menus().openDetails(player, sub);
            return true;
        }
        switch (sub) {
            case "browse" -> plugin.menus().openBrowser(player);
            case "profile" -> plugin.menus().openProfile(player);
            case "join" -> {
                if (args.length < 2) return usage(player, "/jobs join <job>");
                var job = runtime.registry().find(args[1]).orElse(null);
                PlexonJobsAPI.Result result = runtime.joinJob(player.getUniqueId(), args[1]);
                if (result.success() && job != null) player.sendMessage(plugin.messages().render("joined",
                        Placeholder.component("job", plugin.messages().parse(job.displayName()))));
                else sendFailure(player, result);
            }
            case "leave" -> {
                if (args.length < 2) return usage(player, "/jobs leave <job>");
                var job = runtime.registry().find(args[1]).orElse(null);
                if (job == null) {
                    player.sendMessage(plugin.messages().render("unknown-job"));
                    return true;
                }
                if (!runtime.config().keepLevelOnLeave() && !confirmed(args, 2)) {
                    player.sendMessage(plugin.messages().render("menu.reset-warning"));
                    return usage(player, "/jobs leave " + job.id() + " confirm");
                }
                PlexonJobsAPI.Result result = runtime.leaveJob(player.getUniqueId(), job.id());
                if (result.success()) player.sendMessage(plugin.messages().render("left",
                        Placeholder.component("job", plugin.messages().parse(job.displayName()))));
                else sendFailure(player, result);
            }
            case "leaveall" -> {
                if (!runtime.config().keepLevelOnLeave() && !confirmed(args, 1)) {
                    player.sendMessage(plugin.messages().render("menu.reset-warning"));
                    return usage(player, "/jobs leaveall confirm");
                }
                for (String id : List.copyOf(runtime.activeJobs(player.getUniqueId()))) runtime.leaveJob(player.getUniqueId(), id);
                player.sendMessage(plugin.messages().render("left-all"));
            }
            case "info" -> {
                if (args.length < 2) return usage(player, "/jobs info <job>");
                if (runtime.registry().find(args[1]).isEmpty()) player.sendMessage(plugin.messages().render("unknown-job"));
                else plugin.menus().openDetails(player, args[1]);
            }
            case "stats" -> showStats(player, args.length >= 2 ? Bukkit.getPlayerExact(args[1]) : player);
            case "earnings" -> showEarnings(player);
            default -> usage(player, "/jobs [browse|profile|<job>|info|join|leave|leaveall|stats|earnings]");
        }
        return true;
    }

    private void showStats(Player viewer, Player target) {
        if (target == null) {
            viewer.sendMessage(plugin.messages().render("player-not-found"));
            return;
        }
        PlayerJobsProfile profile = plugin.runtime().profiles().ensure(target.getUniqueId());
        if (profile.state() != PlayerJobsProfile.State.READY) {
            viewer.sendMessage(plugin.messages().render("profile-loading"));
            return;
        }
        viewer.sendMessage(Component.text("Jobs stats for " + target.getName(), NamedTextColor.GOLD));
        if (profile.jobs().isEmpty()) {
            viewer.sendMessage(plugin.messages().render("no-progression"));
            return;
        }
        profile.jobs().forEach((id, progress) -> viewer.sendMessage(Component.text(id + ": ", NamedTextColor.GRAY)
                .append(Component.text(progress.joined() ? "active" : "inactive",
                        progress.joined() ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY))
                .append(Component.text(", level " + progress.level() + ", XP " + progress.totalXp(), NamedTextColor.GRAY))));
    }

    private void showEarnings(Player player) {
        if (!plugin.dailyPersistence().ensure(player.getUniqueId())) {
            player.sendMessage(plugin.messages().render("daily-state-loading"));
            return;
        }
        long total = 0;
        for (var job : plugin.runtime().registry().definitions()) {
            long amount = plugin.limits().view(player.getUniqueId(), job.id()).moneyMinor();
            if (amount > 0) player.sendMessage(Component.text(job.id() + ": ", NamedTextColor.GRAY)
                    .append(Component.text(Money.format(amount, plugin.runtime().config().moneyScale()), NamedTextColor.GREEN)));
            total = Math.addExact(total, amount);
        }
        player.sendMessage(Component.text("Today: ", NamedTextColor.GOLD)
                .append(Component.text(Money.format(total, plugin.runtime().config().moneyScale()), NamedTextColor.GREEN))
                .append(Component.text(" | pending: " + Money.format(plugin.runtime().pendingPayout(player.getUniqueId()),
                        plugin.runtime().config().moneyScale()), NamedTextColor.GRAY)));
    }

    private static boolean confirmed(String[] args, int index) {
        return args.length > index && args[index].equalsIgnoreCase("confirm");
    }

    private static void sendFailure(Player player, PlexonJobsAPI.Result result) {
        player.sendMessage(Component.text(result.message(), NamedTextColor.RED));
    }

    private boolean usage(Player player, String text) {
        player.sendMessage(plugin.messages().render("usage", Placeholder.unparsed("usage", text)));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> values = new java.util.ArrayList<>(List.of("browse", "profile", "info", "join", "leave", "leaveall", "stats", "earnings"));
            values.addAll(plugin.runtime().registry().definitions().stream().map(d -> d.id()).toList());
            return filter(values, args[0]);
        }
        if (args.length == 2 && List.of("info", "join", "leave").contains(args[0].toLowerCase(Locale.ROOT)))
            return filter(plugin.runtime().registry().definitions().stream().map(d -> d.id()).toList(), args[1]);
        if (args.length == 2 && args[0].equalsIgnoreCase("leaveall") && !plugin.runtime().config().keepLevelOnLeave())
            return filter(List.of("confirm"), args[1]);
        if (args.length == 3 && args[0].equalsIgnoreCase("leave") && !plugin.runtime().config().keepLevelOnLeave())
            return filter(List.of("confirm"), args[2]);
        return List.of();
    }

    private static List<String> filter(List<String> values, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        return values.stream().distinct().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(p)).toList();
    }
}
