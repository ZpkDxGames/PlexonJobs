package com.plexon.jobs.command;

import com.plexon.jobs.PlexonJobs;
import com.plexon.jobs.migration.LegacyJobsMigration;
import com.plexon.jobs.model.ActivityType;
import com.plexon.jobs.util.Money;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class JobsAdminCommand implements TabExecutor {
    private static final long MAX_SIMULATION_ACTIONS = 100_000L;
    private final PlexonJobs plugin;

    public JobsAdminCommand(PlexonJobs plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("plexonjobs.admin")) {
            sender.sendMessage(plugin.messages().render("no-permission"));
            return true;
        }
        if (args.length == 0) return usage(sender);
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "diagnostics" -> diagnostics(sender);
            case "reload" -> {
                try {
                    plugin.reloadJobs();
                    sender.sendMessage(Component.text("PlexonJobs runtime configuration reloaded transactionally.", NamedTextColor.GREEN));
                } catch (RuntimeException ex) {
                    sender.sendMessage(Component.text("Reload rejected; previous runtime remains active: " + ex.getMessage(), NamedTextColor.RED));
                }
            }
            case "payout" -> {
                if (args.length >= 2 && args[1].equalsIgnoreCase("retry")) {
                    plugin.payouts().retryAll();
                    int committed = plugin.payouts().flush(plugin.runtime().config().maxCommitsPerTick());
                    sender.sendMessage(Component.text("Retry gate reset; committed " + committed + " payout batch(es) now.", NamedTextColor.YELLOW));
                } else sender.sendMessage(Component.text("/jobsadmin payout retry", NamedTextColor.YELLOW));
            }
            case "migration" -> migration(sender, args);
            case "simulate" -> simulate(sender, args);
            case "backup" -> sender.sendMessage(Component.text(
                    "Stop/flush PlexonJobs and copy plugins/PlexonJobs/jobs.db plus YAML configuration. See docs/RECOVERY.md.",
                    NamedTextColor.YELLOW));
            default -> usage(sender);
        }
        return true;
    }

    private void diagnostics(CommandSender sender) {
        var runtime = plugin.runtime();
        var metrics = plugin.metrics().snapshot();
        sender.sendMessage(Component.text("PlexonJobs 2.5 diagnostics", NamedTextColor.GOLD));
        sender.sendMessage(gray("Mode: " + runtime.config().mode() + " | Core: " + plugin.core().version().pluginVersion()
                + " API " + plugin.core().version().apiVersion()));
        sender.sendMessage(gray("Jobs: " + runtime.registry().definitions().size()
                + " | compiled break materials=" + plugin.compiledRoutes().allBreakMaterials().size()
                + " | global joined mask=0x" + Long.toHexString(plugin.interest().globalJoinedJobMask())));
        sender.sendMessage(gray("Profiles: cached=" + runtime.profiles().onlineCached() + ", loading=" + runtime.profiles().loadingCount()
                + ", dirty=" + runtime.profiles().dirtyCount() + ", saving=" + runtime.profiles().savingCount()));
        sender.sendMessage(gray("Daily state: loaded=" + plugin.dailyPersistence().loadedCount() + ", loading="
                + plugin.dailyPersistence().loadingCount() + ", saving=" + plugin.dailyPersistence().savingCount()
                + ", dirty=" + plugin.limits().dirtyPlayers().size()));
        sender.sendMessage(gray("Economy: available=" + plugin.payouts().economyAvailable() + ", pendingPlayers=" + plugin.payouts().pendingPlayers()
                + ", pending=" + Money.format(plugin.payouts().totalPending(), runtime.config().moneyScale()) + ", oldestMs="
                + plugin.payouts().oldestPendingAgeMillis() + ", retryBlocked=" + plugin.payouts().blockedPlayers()));
        sender.sendMessage(gray("Payouts: flushes=" + metrics.payoutFlushes() + ", commits=" + metrics.payoutCommits()
                + ", failed=" + metrics.failedDeposits()));
        sender.sendMessage(gray("Activity routing: seen=" + metrics.eventsSeen() + ", noGlobal=" + metrics.rejectedNoGlobalInterest()
                + ", noPlayer=" + metrics.rejectedNoPlayerInterest() + ", notReady=" + metrics.rejectedNotReady()
                + ", origin=" + metrics.originRejects() + ", matches=" + metrics.routeMatches()
                + ", grants=" + metrics.grantsCommitted()));
        sender.sendMessage(gray("Listeners: FARM=" + listener(metrics, ActivityType.FARM)
                + " KILL=" + listener(metrics, ActivityType.KILL)
                + " FISH=" + listener(metrics, ActivityType.FISH)
                + " PLACE=" + listener(metrics, ActivityType.PLACE)
                + " CRAFT=" + listener(metrics, ActivityType.CRAFT)
                + " SMELT=" + listener(metrics, ActivityType.SMELT)
                + " REPAIR=" + listener(metrics, ActivityType.REPAIR)
                + " BREW=" + listener(metrics, ActivityType.BREW)
                + " ENCHANT=" + listener(metrics, ActivityType.ENCHANT)));
        sender.sendMessage(gray("Core block subscription: active=" + (metrics.blockSubscriptionActive() == 1)
                + " | materials=" + metrics.blockSubscriptionMaterialCount()));
        sender.sendMessage(gray("Public events: skippedNoListeners=" + metrics.customEventsSkippedNoListeners()
                + " | dispatched=" + metrics.customEventsDispatched()));
        sender.sendMessage(gray("Feedback: activeBars=" + (plugin.feedback() == null ? 0 : plugin.feedback().activeBars())
                + " | accumulations=" + metrics.feedbackAccumulations()
                + " | visualFlushes=" + metrics.feedbackVisualFlushes()
                + " | dirtyPlayers=" + metrics.feedbackDirtyPlayers()
                + " | flushTicks=" + runtime.config().performance().feedbackFlushTicks()));
        sender.sendMessage(gray("Explorer: participants=" + plugin.interest().participantCount(ActivityType.EXPLORE)
                + " | taskActive=" + (plugin.interest().participantCount(ActivityType.EXPLORE) > 0)
                + " | sampleTicks=" + runtime.config().activity().explorerSampleTicks()));
        sender.sendMessage(gray("Hunter: originTracking=" + runtime.config().activity().hunterOriginTracking()));
        sender.sendMessage(gray("Shadow: moneyMinor=" + metrics.shadowMoneyMinor() + ", xp=" + metrics.shadowXp()
                + " | shadowBuffer=" + plugin.shadow().size()));
        sender.sendMessage(gray("Persistence: schema=" + plugin.database().schemaVersion() + " | Core IO queue="
                + plugin.core().scheduler().ioQueueSize() + " | day=" + plugin.limits().day()));
        sender.sendMessage(gray("Core gateway routes=" + plugin.core().events().compiledBlockRoutes() + " | metrics=" + plugin.core().events().metrics()));
    }

    private static int listener(com.plexon.jobs.runtime.JobsMetrics.Snapshot metrics, ActivityType type) {
        return metrics.listenerActive().getOrDefault(type, 0);
    }

    private void migration(CommandSender sender, String[] args) {
        LegacyJobsMigration migration = plugin.migration();
        String action = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "status";
        switch (action) {
            case "scan" -> {
                var scan = migration.scan();
                sender.sendMessage(Component.text("Source: " + scan.sourcePath() + " | exists=" + scan.exists()
                        + " | candidates=" + scan.candidates().size(), NamedTextColor.YELLOW));
                scan.candidates().forEach(path -> sender.sendMessage(gray("- " + path)));
            }
            case "plan", "status" -> sender.sendMessage(Component.text(migration.plan(), NamedTextColor.YELLOW));
            case "execute" -> sender.sendMessage(Component.text(
                    "Migration execute remains fail-closed until the administrator's actual Jobs schema is inspected and a staging backup dry-run passes.",
                    NamedTextColor.RED));
            default -> sender.sendMessage(Component.text("/jobsadmin migration <scan|plan|status|execute>", NamedTextColor.YELLOW));
        }
    }

    private void simulate(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(Component.text("/jobsadmin simulate <job> <activity> <key> <count>", NamedTextColor.YELLOW));
            sender.sendMessage(Component.text("Legacy shortcut: /jobsadmin simulate <job> <material> <count>", NamedTextColor.GRAY));
            return;
        }
        var job = plugin.runtime().registry().find(args[1]).orElse(null);
        if (job == null) {
            sender.sendMessage(Component.text("Unknown job.", NamedTextColor.RED));
            return;
        }

        ActivityType activity;
        String key;
        String rawCount;
        if (args.length == 4) {
            activity = ActivityType.BREAK;
            Material material = Material.matchMaterial(args[2]);
            if (material == null) {
                sender.sendMessage(Component.text("Unknown material.", NamedTextColor.RED));
                return;
            }
            key = material.name();
            rawCount = args[3];
        } else {
            try { activity = ActivityType.valueOf(args[2].toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException ex) {
                sender.sendMessage(Component.text("Unknown activity. Use BREAK, PLACE, KILL, FISH, FARM, CRAFT, SMELT, BREW, ENCHANT, REPAIR, or EXPLORE.", NamedTextColor.RED));
                return;
            }
            if (activity == ActivityType.DAMAGE) {
                sender.sendMessage(Component.text("DAMAGE is reserved and not a grantable activity.", NamedTextColor.RED));
                return;
            }
            key = args[3];
            rawCount = args[4];
        }

        long count;
        try { count = Long.parseLong(rawCount); }
        catch (NumberFormatException ex) {
            sender.sendMessage(Component.text("Count must be an integer.", NamedTextColor.RED));
            return;
        }
        if (count < 0 || count > MAX_SIMULATION_ACTIONS) {
            sender.sendMessage(Component.text("Count must be between 0 and " + MAX_SIMULATION_ACTIONS + ".", NamedTextColor.RED));
            return;
        }

        var reward = job.reward(activity, key);
        try {
            long money = Math.multiplyExact(reward.moneyMinorUnits(), count);
            long xp = Math.multiplyExact(reward.jobXpUnits(), count);
            sender.sendMessage(Component.text(job.id() + " / " + activity + " / " + key.toUpperCase(Locale.ROOT) + " x" + count + ": ", NamedTextColor.GOLD)
                    .append(Component.text(Money.format(money, plugin.runtime().config().moneyScale()), NamedTextColor.GREEN))
                    .append(Component.text(" money, " + xp + " job XP before daily caps. No state was granted.", NamedTextColor.GRAY)));
        } catch (ArithmeticException overflow) {
            sender.sendMessage(Component.text("Simulation total overflowed the supported numeric range.", NamedTextColor.RED));
        }
    }

    private static Component gray(String text) { return Component.text(text, NamedTextColor.GRAY); }

    private static boolean usage(CommandSender sender) {
        sender.sendMessage(Component.text("/jobsadmin <diagnostics|reload|payout retry|migration|simulate|backup>", NamedTextColor.YELLOW));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return List.of("diagnostics", "reload", "payout", "migration", "simulate", "backup").stream()
                .filter(v -> v.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        if (args.length == 2 && args[0].equalsIgnoreCase("migration")) return List.of("scan", "plan", "status", "execute");
        if (args.length == 2 && args[0].equalsIgnoreCase("payout")) return List.of("retry");
        if (args.length == 2 && args[0].equalsIgnoreCase("simulate")) return plugin.runtime().registry().definitions().stream().map(d -> d.id()).toList();
        if (args.length == 3 && args[0].equalsIgnoreCase("simulate")) return Arrays.stream(ActivityType.values())
                .filter(type -> type != ActivityType.DAMAGE).map(type -> type.name().toLowerCase(Locale.ROOT))
                .filter(value -> value.startsWith(args[2].toLowerCase(Locale.ROOT))).toList();
        if (args.length == 4 && args[0].equalsIgnoreCase("simulate")) {
            var job = plugin.runtime().registry().find(args[1]).orElse(null);
            if (job == null) return List.of();
            try {
                ActivityType type = ActivityType.valueOf(args[2].toUpperCase(Locale.ROOT));
                return job.activityKeys(type).stream().sorted().toList();
            } catch (IllegalArgumentException ignored) { return List.of(); }
        }
        return List.of();
    }
}
