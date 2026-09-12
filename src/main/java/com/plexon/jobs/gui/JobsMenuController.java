package com.plexon.jobs.gui;

import com.plexon.jobs.PlexonJobs;
import com.plexon.jobs.api.PlexonJobsAPI;
import com.plexon.jobs.model.ActivityType;
import com.plexon.jobs.model.JobDefinition;
import com.plexon.jobs.model.JobProgress;
import com.plexon.jobs.model.PlayerJobsProfile;
import com.plexon.jobs.model.XpCurve;
import com.plexon.jobs.runtime.DailyLimitService;
import com.plexon.jobs.util.Money;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Complete 2.5 jobs product UI: dashboard, browser, profile, details and dialog confirmation. */
public final class JobsMenuController implements Listener {
    private static final int[] BROWSER_SLOTS = {
            10,11,12,13,14,15,16,
            19,20,21,22,23,24,25,
            28,29,30,31,32,33,34,
            37,38,39,40,41,42,43
    };
    private static final int[] PROFILE_JOB_SLOTS = {19,20,21,22,23,24,25,28,29,30,31,32,33,34};
    private static final int[] PROGRESS_SLOTS = {19,20,21,22,23,24,25};
    private final PlexonJobs plugin;

    public JobsMenuController(PlexonJobs plugin) { this.plugin = plugin; }

    public void openOverview(Player player) { openDashboard(player); }

    public void openDashboard(Player player) {
        ensureWarm(player);
        JobsMenuHolder holder = new JobsMenuHolder(player.getUniqueId(), JobsMenuHolder.View.DASHBOARD, null);
        Inventory inventory = Bukkit.createInventory(holder, 45, Component.text("PlexonJobs • Dashboard", NamedTextColor.GOLD));
        holder.bind(inventory);
        PlayerJobsProfile profile = plugin.runtime().profiles().get(player.getUniqueId());
        boolean profileReady = ready(profile);
        boolean dailyReady = plugin.dailyPersistence().ready(player.getUniqueId());

        List<Component> headLore = new ArrayList<>();
        headLore.add(JobsGuiComponents.line("Profile", profileReady ? "Ready" : stateName(profile)));
        headLore.add(JobsGuiComponents.line("Active jobs", profileReady
                ? profile.activeCount() + "/" + maxJobsText(player) : "Loading"));
        headLore.add(JobsGuiComponents.line("Total job XP", profileReady ? Long.toString(totalXp(profile)) : "Loading"));
        inventory.setItem(4, JobsGuiComponents.head(player, Component.text(player.getName(), NamedTextColor.AQUA), headLore));

        inventory.setItem(11, JobsGuiComponents.item(Material.COMPASS, Component.text("Browse Jobs", NamedTextColor.AQUA),
                List.of(Component.text("Explore every available job and your current status.", NamedTextColor.GRAY), clickHint())));
        holder.action(11, new JobsMenuHolder.MenuAction(JobsMenuHolder.ActionType.BROWSE, null));

        inventory.setItem(13, JobsGuiComponents.item(Material.PLAYER_HEAD, Component.text("Player Profile", NamedTextColor.YELLOW),
                List.of(Component.text("Progress, daily earnings and active jobs.", NamedTextColor.GRAY), clickHint())));
        holder.action(13, new JobsMenuHolder.MenuAction(JobsMenuHolder.ActionType.PROFILE, null));

        List<Component> activeLore = new ArrayList<>();
        if (!profileReady) activeLore.add(Component.text("Profile is still loading.", NamedTextColor.YELLOW));
        else if (profile.activeJobIds().isEmpty()) activeLore.add(Component.text("No active jobs", NamedTextColor.GRAY));
        else profile.activeJobIds().forEach(id -> activeLore.add(Component.text("• " + display(id), NamedTextColor.GRAY)));
        inventory.setItem(15, JobsGuiComponents.item(Material.WRITABLE_BOOK, Component.text("Active Jobs", NamedTextColor.GREEN), activeLore));

        if (dailyReady) {
            DailyLimitService.CounterView daily = plugin.limits().total(player.getUniqueId());
            inventory.setItem(29, JobsGuiComponents.item(Material.CLOCK, Component.text("Earned Today", NamedTextColor.GOLD), List.of(
                    JobsGuiComponents.line("Job XP", Long.toString(daily.xp())),
                    JobsGuiComponents.line("Money", Money.format(daily.moneyMinor(), plugin.runtime().config().moneyScale())))));
        } else inventory.setItem(29, loadingItem("Earned Today"));

        inventory.setItem(31, JobsGuiComponents.item(plugin.payouts().economyAvailable() ? Material.EMERALD : Material.REDSTONE,
                Component.text("Pending / Economy", plugin.payouts().economyAvailable() ? NamedTextColor.GREEN : NamedTextColor.RED), List.of(
                        JobsGuiComponents.line("Pending", Money.format(plugin.payouts().pending(player.getUniqueId()), plugin.runtime().config().moneyScale())),
                        JobsGuiComponents.line("Economy", plugin.payouts().economyAvailable() ? "Available" : "Unavailable"))));

        inventory.setItem(33, JobsGuiComponents.item(Material.KNOWLEDGE_BOOK, Component.text("Help / Plugin Info", NamedTextColor.AQUA), List.of(
                Component.text("Join jobs to earn job XP and configured payouts.", NamedTextColor.GRAY),
                Component.text("Rewards respect daily limits and activity safety rules.", NamedTextColor.GRAY),
                Component.text("PlexonJobs " + plugin.getPluginMeta().getVersion(), NamedTextColor.DARK_GRAY))));

        inventory.setItem(40, closeItem());
        holder.action(40, new JobsMenuHolder.MenuAction(JobsMenuHolder.ActionType.CLOSE, null));
        player.openInventory(inventory);
    }

    public void openBrowser(Player player) { openBrowser(player, 0, JobsMenuHolder.Filter.ALL); }

    public void openBrowser(Player player, int requestedPage, JobsMenuHolder.Filter filter) {
        ensureWarm(player);
        PlayerJobsProfile profile = plugin.runtime().profiles().get(player.getUniqueId());
        List<JobDefinition> jobs = filteredJobs(profile, filter);
        int pages = Math.max(1, (jobs.size() + BROWSER_SLOTS.length - 1) / BROWSER_SLOTS.length);
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        JobsMenuHolder holder = new JobsMenuHolder(player.getUniqueId(), JobsMenuHolder.View.BROWSER, null, page, filter);
        Inventory inventory = Bukkit.createInventory(holder, 54, Component.text("PlexonJobs • Job Browser", NamedTextColor.GOLD));
        holder.bind(inventory);

        int from = page * BROWSER_SLOTS.length;
        int to = Math.min(jobs.size(), from + BROWSER_SLOTS.length);
        int slotIndex = 0;
        for (int index = from; index < to; index++) {
            JobDefinition job = jobs.get(index);
            int slot = BROWSER_SLOTS[slotIndex++];
            inventory.setItem(slot, jobCard(player, profile, job));
            holder.action(slot, new JobsMenuHolder.MenuAction(JobsMenuHolder.ActionType.OPEN_JOB, job.id()));
        }
        if (jobs.isEmpty()) inventory.setItem(22, JobsGuiComponents.item(Material.GRAY_DYE, Component.text("No jobs match this filter", NamedTextColor.GRAY), List.of()));

        inventory.setItem(45, backItem());
        holder.action(45, new JobsMenuHolder.MenuAction(JobsMenuHolder.ActionType.BACK, null));
        JobsMenuHolder.Filter nextFilter = nextFilter(filter);
        inventory.setItem(46, JobsGuiComponents.item(Material.HOPPER, Component.text("Filter: " + filter.name(), NamedTextColor.AQUA),
                List.of(Component.text("Click for " + nextFilter.name(), NamedTextColor.GRAY))));
        holder.action(46, new JobsMenuHolder.MenuAction(JobsMenuHolder.ActionType.FILTER_CYCLE, page, nextFilter));
        if (page > 0) {
            inventory.setItem(47, JobsGuiComponents.item(Material.ARROW, Component.text("Previous Page", NamedTextColor.YELLOW), List.of()));
            holder.action(47, new JobsMenuHolder.MenuAction(JobsMenuHolder.ActionType.PREVIOUS_PAGE, page - 1, filter));
        }
        inventory.setItem(49, JobsGuiComponents.item(Material.PLAYER_HEAD, Component.text("Player Profile", NamedTextColor.YELLOW), List.of(clickHint())));
        holder.action(49, new JobsMenuHolder.MenuAction(JobsMenuHolder.ActionType.PROFILE, null));
        if (page + 1 < pages) {
            inventory.setItem(51, JobsGuiComponents.item(Material.ARROW, Component.text("Next Page", NamedTextColor.YELLOW), List.of()));
            holder.action(51, new JobsMenuHolder.MenuAction(JobsMenuHolder.ActionType.NEXT_PAGE, page + 1, filter));
        }
        inventory.setItem(53, closeItem());
        holder.action(53, new JobsMenuHolder.MenuAction(JobsMenuHolder.ActionType.CLOSE, null));
        player.openInventory(inventory);
    }

    public void openProfile(Player player) {
        ensureWarm(player);
        PlayerJobsProfile profile = plugin.runtime().profiles().get(player.getUniqueId());
        JobsMenuHolder holder = new JobsMenuHolder(player.getUniqueId(), JobsMenuHolder.View.PROFILE, null);
        Inventory inventory = Bukkit.createInventory(holder, 45, Component.text("PlexonJobs • Profile", NamedTextColor.GOLD));
        holder.bind(inventory);

        List<Component> headLore = new ArrayList<>();
        if (ready(profile)) {
            headLore.add(JobsGuiComponents.line("Active jobs", profile.activeCount() + "/" + maxJobsText(player)));
            headLore.add(JobsGuiComponents.line("Combined XP", Long.toString(totalXp(profile))));
        } else headLore.add(Component.text("Profile is " + stateName(profile).toLowerCase(Locale.ROOT) + ".", NamedTextColor.YELLOW));
        inventory.setItem(4, JobsGuiComponents.head(player, Component.text(player.getName(), NamedTextColor.AQUA), headLore));

        if (plugin.dailyPersistence().ready(player.getUniqueId())) {
            DailyLimitService.CounterView daily = plugin.limits().total(player.getUniqueId());
            inventory.setItem(11, JobsGuiComponents.item(Material.EXPERIENCE_BOTTLE, Component.text("Daily XP", NamedTextColor.AQUA),
                    List.of(JobsGuiComponents.line("Earned", Long.toString(daily.xp())))));
            inventory.setItem(13, JobsGuiComponents.item(Material.GOLD_INGOT, Component.text("Daily Money", NamedTextColor.GOLD),
                    List.of(JobsGuiComponents.line("Earned", Money.format(daily.moneyMinor(), plugin.runtime().config().moneyScale())))));
        } else {
            inventory.setItem(11, loadingItem("Daily XP"));
            inventory.setItem(13, loadingItem("Daily Money"));
        }
        inventory.setItem(15, JobsGuiComponents.item(plugin.payouts().economyAvailable() ? Material.EMERALD : Material.REDSTONE,
                Component.text("Pending Payout", NamedTextColor.GREEN), List.of(
                        JobsGuiComponents.line("Pending", Money.format(plugin.payouts().pending(player.getUniqueId()), plugin.runtime().config().moneyScale())),
                        JobsGuiComponents.line("Economy", plugin.payouts().economyAvailable() ? "Available" : "Unavailable"))));

        if (ready(profile)) {
            List<String> active = List.copyOf(profile.activeJobIds());
            if (active.isEmpty()) {
                inventory.setItem(22, JobsGuiComponents.item(Material.COMPASS, Component.text("No active jobs", NamedTextColor.GRAY),
                        List.of(Component.text("Browse jobs to get started.", NamedTextColor.GRAY), clickHint())));
                holder.action(22, new JobsMenuHolder.MenuAction(JobsMenuHolder.ActionType.BROWSE, null));
            } else {
                for (int i = 0; i < active.size() && i < PROFILE_JOB_SLOTS.length; i++) {
                    JobDefinition job = plugin.runtime().registry().find(active.get(i)).orElse(null);
                    if (job == null) continue;
                    int slot = PROFILE_JOB_SLOTS[i];
                    inventory.setItem(slot, jobCard(player, profile, job));
                    holder.action(slot, new JobsMenuHolder.MenuAction(JobsMenuHolder.ActionType.OPEN_JOB, job.id()));
                }
            }
        } else inventory.setItem(22, loadingItem("Active Jobs"));

        inventory.setItem(36, backItem());
        holder.action(36, new JobsMenuHolder.MenuAction(JobsMenuHolder.ActionType.BACK, null));
        inventory.setItem(40, JobsGuiComponents.item(Material.COMPASS, Component.text("Browse Jobs", NamedTextColor.AQUA), List.of(clickHint())));
        holder.action(40, new JobsMenuHolder.MenuAction(JobsMenuHolder.ActionType.BROWSE, null));
        inventory.setItem(44, closeItem());
        holder.action(44, new JobsMenuHolder.MenuAction(JobsMenuHolder.ActionType.CLOSE, null));
        player.openInventory(inventory);
    }

    public void openDetails(Player player, String jobId) {
        JobDefinition job = plugin.runtime().registry().find(jobId).orElse(null);
        if (job == null) {
            player.sendMessage(plugin.messages().render("unknown-job"));
            return;
        }
        ensureWarm(player);
        PlayerJobsProfile profile = plugin.runtime().profiles().get(player.getUniqueId());
        JobsMenuHolder holder = new JobsMenuHolder(player.getUniqueId(), JobsMenuHolder.View.DETAILS, job.id());
        Inventory inventory = Bukkit.createInventory(holder, 45, Component.text("PlexonJobs • " + plainName(job), NamedTextColor.GOLD));
        holder.bind(inventory);

        JobProgress progress = ready(profile) ? profile.jobs().get(job.id()) : null;
        boolean joined = progress != null && progress.joined();
        long xp = progress == null ? 0L : progress.totalXp();
        int level = progress == null ? 1 : progress.level();
        XpCurve curve = plugin.runtime().registry().curve(job.id());
        double ratio = progressRatio(curve, xp, level);

        List<Component> info = new ArrayList<>();
        info.add(JobsGuiComponents.line("Status", !job.enabled() ? "Disabled" : joined ? "Joined" : "Available"));
        if (ready(profile)) {
            info.add(JobsGuiComponents.line("Level", level + "/" + job.maxLevel()));
            info.add(JobsGuiComponents.line("Total XP", Long.toString(xp)));
            info.add(JobsGuiComponents.line("XP to next", Long.toString(curve.xpToNextLevel(xp))));
            info.add(JobsGuiComponents.line("Progress", String.format(Locale.ROOT, "%.1f%%", ratio * 100.0)));
        } else info.add(Component.text("Profile is still loading.", NamedTextColor.YELLOW));
        inventory.setItem(4, JobsGuiComponents.item(job.icon(), plugin.messages().parse(job.displayName()), info));

        int filled = (int) Math.floor(ratio * PROGRESS_SLOTS.length + 1.0e-9);
        if (level >= job.maxLevel()) filled = PROGRESS_SLOTS.length;
        for (int i = 0; i < PROGRESS_SLOTS.length; i++) {
            boolean complete = i < filled;
            inventory.setItem(PROGRESS_SLOTS[i], JobsGuiComponents.item(
                    complete ? Material.LIME_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE,
                    Component.text(complete ? "Progress" : "Remaining", complete ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY), List.of()));
        }

        if (plugin.dailyPersistence().ready(player.getUniqueId())) {
            DailyLimitService.CounterView daily = plugin.limits().view(player.getUniqueId(), job.id());
            List<Component> dailyLore = new ArrayList<>();
            dailyLore.add(JobsGuiComponents.line("Money", Money.format(daily.moneyMinor(), plugin.runtime().config().moneyScale())));
            dailyLore.add(JobsGuiComponents.line("XP", Long.toString(daily.xp())));
            if (plugin.runtime().config().defaultMoneyCapMinor() > 0) dailyLore.add(JobsGuiComponents.line("Money cap",
                    Money.format(plugin.runtime().config().defaultMoneyCapMinor(), plugin.runtime().config().moneyScale())));
            if (plugin.runtime().config().defaultXpCap() > 0) dailyLore.add(JobsGuiComponents.line("XP cap", Long.toString(plugin.runtime().config().defaultXpCap())));
            inventory.setItem(29, JobsGuiComponents.item(Material.CLOCK, Component.text("Daily Earnings", NamedTextColor.GOLD), dailyLore));
        } else inventory.setItem(29, loadingItem("Daily Earnings"));

        inventory.setItem(31, JobsGuiComponents.item(Material.PAPER, Component.text("Activities / Rewards", NamedTextColor.AQUA), activityLore(job)));
        inventory.setItem(33, JobsGuiComponents.item(job.enabled() ? Material.LIME_DYE : Material.RED_DYE,
                Component.text(job.enabled() ? "Job Available" : "Job Disabled", job.enabled() ? NamedTextColor.GREEN : NamedTextColor.RED),
                List.of(Component.text(job.enabled() ? "Use the control below to join or leave." : "Server configuration currently disables this job.", NamedTextColor.GRAY))));

        inventory.setItem(36, backItem());
        holder.action(36, new JobsMenuHolder.MenuAction(JobsMenuHolder.ActionType.BACK, null));
        if (job.enabled() && ready(profile)) {
            if (joined) {
                List<Component> lore = plugin.runtime().config().keepLevelOnLeave() ? List.of()
                        : List.of(Component.text("Leaving will reset this job's XP and level.", NamedTextColor.RED));
                inventory.setItem(40, JobsGuiComponents.item(Material.ORANGE_DYE, Component.text("Leave Job", NamedTextColor.YELLOW), lore));
                holder.action(40, new JobsMenuHolder.MenuAction(JobsMenuHolder.ActionType.LEAVE, job.id()));
            } else {
                inventory.setItem(40, JobsGuiComponents.item(Material.LIME_DYE, Component.text("Join Job", NamedTextColor.GREEN), List.of()));
                holder.action(40, new JobsMenuHolder.MenuAction(JobsMenuHolder.ActionType.JOIN, job.id()));
            }
        } else if (!ready(profile)) inventory.setItem(40, loadingItem("Membership"));
        inventory.setItem(44, closeItem());
        holder.action(44, new JobsMenuHolder.MenuAction(JobsMenuHolder.ActionType.CLOSE, null));
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof JobsMenuHolder holder)) return;
        event.setCancelled(true);
        HumanEntity actor = event.getWhoClicked();
        if (!(actor instanceof Player player) || !holder.viewerId().equals(player.getUniqueId())) return;
        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= top.getSize()) return;
        JobsMenuHolder.MenuAction action = holder.action(rawSlot);
        if (action == null) return;
        switch (action.type()) {
            case CLOSE -> defer(player::closeInventory);
            case DASHBOARD, BACK -> defer(() -> openDashboard(player));
            case BROWSE -> defer(() -> openBrowser(player));
            case PROFILE -> defer(() -> openProfile(player));
            case OPEN_JOB -> defer(() -> openDetails(player, action.jobId()));
            case PREVIOUS_PAGE, NEXT_PAGE, FILTER_CYCLE -> defer(() -> openBrowser(player,
                    action.page() == null ? holder.page() : action.page(),
                    action.filter() == null ? holder.filter() : action.filter()));
            case JOIN -> mutateMembership(player, action.jobId(), true);
            case LEAVE -> {
                if (plugin.runtime().config().keepLevelOnLeave()) mutateMembership(player, action.jobId(), false);
                else showLeaveConfirmation(player, action.jobId());
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof JobsMenuHolder)) return;
        if (event.getRawSlots().stream().anyMatch(slot -> slot < top.getSize())) event.setCancelled(true);
    }

    private void showLeaveConfirmation(Player player, String jobId) {
        JobDefinition job = plugin.runtime().registry().find(jobId).orElse(null);
        if (job == null) return;
        UUID viewerId = player.getUniqueId();
        Component warning = Component.text("Leaving ", NamedTextColor.GRAY)
                .append(plugin.messages().parse(job.displayName()))
                .append(Component.text(" will permanently reset its stored XP and level.", NamedTextColor.RED));
        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text("Confirm Job Leave", NamedTextColor.RED))
                        .body(List.of(DialogBody.plainMessage(warning)))
                        .canCloseWithEscape(true)
                        .build())
                .type(DialogType.confirmation(
                        ActionButton.create(Component.text("Leave and Reset", NamedTextColor.RED),
                                Component.text("This cannot be undone."), 150,
                                DialogAction.customClick((view, audience) -> {
                                    if (!(audience instanceof Player clicked) || !clicked.getUniqueId().equals(viewerId)) return;
                                    runPrimary(() -> mutateMembership(clicked, jobId, false));
                                }, ClickCallback.Options.builder().uses(1).build())),
                        ActionButton.create(Component.text("Cancel", NamedTextColor.GRAY),
                                Component.text("Keep this job."), 150, null)
                )));
        player.closeInventory();
        player.showDialog(dialog);
    }

    private void mutateMembership(Player player, String jobId, boolean joining) {
        JobDefinition job = plugin.runtime().registry().find(jobId).orElse(null);
        if (job == null) {
            player.sendMessage(plugin.messages().render("unknown-job"));
            return;
        }
        PlexonJobsAPI.Result result = joining
                ? plugin.runtime().joinJob(player.getUniqueId(), job.id())
                : plugin.runtime().leaveJob(player.getUniqueId(), job.id());
        if (!result.success()) player.sendMessage(Component.text(result.message(), NamedTextColor.RED));
        defer(() -> openDetails(player, job.id()));
    }

    private List<JobDefinition> filteredJobs(PlayerJobsProfile profile, JobsMenuHolder.Filter filter) {
        return plugin.runtime().registry().definitions().stream().filter(job -> switch (filter) {
            case ALL -> true;
            case JOINED -> ready(profile) && joined(profile, job.id());
            case AVAILABLE -> job.enabled() && (!ready(profile) || !joined(profile, job.id()));
        }).toList();
    }

    private ItemStack jobCard(Player player, PlayerJobsProfile profile, JobDefinition job) {
        List<Component> lore = new ArrayList<>();
        if (!job.enabled()) lore.add(Component.text("Disabled", NamedTextColor.RED));
        else if (ready(profile) && joined(profile, job.id())) lore.add(Component.text("Joined", NamedTextColor.GREEN));
        else lore.add(Component.text("Available", NamedTextColor.AQUA));
        if (ready(profile)) {
            JobProgress progress = profile.jobs().get(job.id());
            long xp = progress == null ? 0L : progress.totalXp();
            int level = progress == null ? 1 : progress.level();
            if (progress != null && progress.joined()) {
                lore.add(JobsGuiComponents.line("Level", level + "/" + job.maxLevel()));
                lore.add(JobsGuiComponents.line("XP to next", Long.toString(plugin.runtime().registry().curve(job.id()).xpToNextLevel(xp))));
            }
            if (plugin.dailyPersistence().ready(player.getUniqueId())) {
                DailyLimitService.CounterView daily = plugin.limits().view(player.getUniqueId(), job.id());
                lore.add(JobsGuiComponents.line("Today", Money.format(daily.moneyMinor(), plugin.runtime().config().moneyScale()) + " / " + daily.xp() + " XP"));
            } else lore.add(Component.text("Daily state loading…", NamedTextColor.YELLOW));
        } else lore.add(Component.text("Profile loading…", NamedTextColor.YELLOW));
        lore.add(activitySummary(job));
        lore.add(Component.empty());
        lore.add(clickHint());
        return JobsGuiComponents.item(job.icon(), plugin.messages().parse(job.displayName()), lore);
    }

    private List<Component> activityLore(JobDefinition job) {
        List<Component> lore = new ArrayList<>();
        if (!job.breakRewards().isEmpty()) lore.add(Component.text("BREAK • " + job.breakRewards().size() + " configured material routes", NamedTextColor.GRAY));
        for (var entry : job.activityRewards().entrySet()) {
            lore.add(Component.text(entry.getKey().name() + " • " + entry.getValue().size() + " configured route(s)", NamedTextColor.GRAY));
        }
        if (lore.isEmpty()) lore.add(Component.text("No reward routes configured.", NamedTextColor.DARK_GRAY));
        return lore;
    }

    private Component activitySummary(JobDefinition job) {
        List<String> names = new ArrayList<>();
        if (!job.breakRewards().isEmpty()) names.add("BREAK");
        for (ActivityType type : job.activityRewards().keySet()) names.add(type.name());
        return Component.text(names.isEmpty() ? "No activities" : String.join(" • ", names), NamedTextColor.DARK_GRAY);
    }

    private void ensureWarm(Player player) {
        plugin.runtime().profiles().ensure(player.getUniqueId());
        plugin.dailyPersistence().ensure(player.getUniqueId());
    }

    private String maxJobsText(Player player) {
        int max = plugin.runtime().maxJobs(player.getUniqueId());
        return max == Integer.MAX_VALUE ? "∞" : Integer.toString(max);
    }

    private String display(String jobId) {
        JobDefinition job = plugin.runtime().registry().find(jobId).orElse(null);
        return job == null ? jobId : plainName(job);
    }

    private static String plainName(JobDefinition job) {
        return job.id().substring(0, 1).toUpperCase(Locale.ROOT) + job.id().substring(1);
    }

    private static boolean ready(PlayerJobsProfile profile) {
        return profile != null && profile.state() == PlayerJobsProfile.State.READY;
    }

    private static String stateName(PlayerJobsProfile profile) {
        return profile == null ? "Loading" : switch (profile.state()) {
            case READY -> "Ready";
            case LOADING -> "Loading";
            case FAILED -> "Error";
        };
    }

    private static boolean joined(PlayerJobsProfile profile, String jobId) {
        JobProgress progress = profile.jobs().get(jobId);
        return progress != null && progress.joined();
    }

    private static long totalXp(PlayerJobsProfile profile) {
        long total = 0L;
        for (JobProgress progress : profile.jobs().values()) {
            try { total = Math.addExact(total, progress.totalXp()); }
            catch (ArithmeticException overflow) { return Long.MAX_VALUE; }
        }
        return total;
    }

    private static double progressRatio(XpCurve curve, long totalXp, int level) {
        if (level >= curve.maxLevel()) return 1.0;
        long start = curve.totalXpForLevel(level);
        long end = curve.totalXpForLevel(level + 1);
        if (end <= start) return 1.0;
        return Math.max(0.0, Math.min(1.0, (double) Math.max(0L, totalXp - start) / (double) (end - start)));
    }

    private static JobsMenuHolder.Filter nextFilter(JobsMenuHolder.Filter filter) {
        return switch (filter) {
            case ALL -> JobsMenuHolder.Filter.JOINED;
            case JOINED -> JobsMenuHolder.Filter.AVAILABLE;
            case AVAILABLE -> JobsMenuHolder.Filter.ALL;
        };
    }

    private static ItemStack loadingItem(String label) {
        return JobsGuiComponents.item(Material.CLOCK, Component.text(label, NamedTextColor.YELLOW),
                List.of(Component.text("Loading authoritative state…", NamedTextColor.GRAY)));
    }

    private static ItemStack backItem() {
        return JobsGuiComponents.item(Material.ARROW, Component.text("Back", NamedTextColor.YELLOW), List.of());
    }

    private static ItemStack closeItem() {
        return JobsGuiComponents.item(Material.BARRIER, Component.text("Close", NamedTextColor.RED), List.of());
    }

    private static Component clickHint() { return Component.text("Click to open", NamedTextColor.AQUA); }

    private void defer(Runnable action) { Bukkit.getScheduler().runTask(plugin, action); }

    private void runPrimary(Runnable action) {
        if (Bukkit.isPrimaryThread()) action.run();
        else Bukkit.getScheduler().runTask(plugin, action);
    }
}
