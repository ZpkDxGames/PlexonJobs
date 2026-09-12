package com.plexon.jobs.gui;

import com.plexon.jobs.PlexonJobs;
import com.plexon.jobs.api.PlexonJobsAPI;
import com.plexon.jobs.config.Messages;
import com.plexon.jobs.model.JobDefinition;
import com.plexon.jobs.model.JobProgress;
import com.plexon.jobs.model.PlayerJobsProfile;
import com.plexon.jobs.runtime.DailyLimitService;
import com.plexon.jobs.util.Money;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
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
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** One menu router for the complete PlexonJobs inventory family. */
public final class JobsMenuController implements Listener {
    private static final int[] OVERVIEW_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23};
    private final PlexonJobs plugin;

    public JobsMenuController(PlexonJobs plugin) {
        this.plugin = plugin;
    }

    public void openOverview(Player player) {
        Messages messages = plugin.messages();
        plugin.dailyPersistence().ensure(player.getUniqueId());
        JobsMenuHolder holder = new JobsMenuHolder(player.getUniqueId(), JobsMenuHolder.View.OVERVIEW, null);
        Inventory inventory = Bukkit.createInventory(holder, 36, messages.renderBare("menu.title"));
        holder.bind(inventory);

        PlayerJobsProfile profile = plugin.runtime().profiles().ensure(player.getUniqueId());
        int index = 0;
        for (JobDefinition job : plugin.runtime().registry().definitions()) {
            if (index >= OVERVIEW_SLOTS.length) break;
            int slot = OVERVIEW_SLOTS[index++];
            inventory.setItem(slot, overviewItem(player, profile, job));
            holder.action(slot, new JobsMenuHolder.MenuAction(JobsMenuHolder.ActionType.OPEN_JOB, job.id()));
        }

        long active = profile.state() == PlayerJobsProfile.State.READY ? profile.activeCount() : 0;
        int max = plugin.runtime().maxJobs(player.getUniqueId());
        String maxText = max == Integer.MAX_VALUE ? "∞" : Integer.toString(max);
        inventory.setItem(31, item(Material.BOOK,
                messages.renderBare("menu.active-summary",
                        Placeholder.unparsed("active", Long.toString(active)),
                        Placeholder.unparsed("max", maxText)), List.of()));
        inventory.setItem(35, item(Material.BARRIER, messages.renderBare("menu.close"), List.of()));
        holder.action(35, new JobsMenuHolder.MenuAction(JobsMenuHolder.ActionType.CLOSE, null));
        player.openInventory(inventory);
    }

    public void openDetails(Player player, String jobId) {
        JobDefinition job = plugin.runtime().registry().find(jobId).orElse(null);
        if (job == null) {
            player.sendMessage(plugin.messages().render("unknown-job"));
            return;
        }
        plugin.dailyPersistence().ensure(player.getUniqueId());
        Messages messages = plugin.messages();
        JobsMenuHolder holder = new JobsMenuHolder(player.getUniqueId(), JobsMenuHolder.View.DETAILS, job.id());
        Inventory inventory = Bukkit.createInventory(holder, 27,
                messages.renderBare("menu.details-title", Placeholder.component("job", messages.parse(job.displayName()))));
        holder.bind(inventory);

        PlayerJobsProfile profile = plugin.runtime().profiles().ensure(player.getUniqueId());
        inventory.setItem(13, detailsItem(player, profile, job));
        inventory.setItem(18, item(Material.ARROW, messages.renderBare("menu.back"), List.of()));
        holder.action(18, new JobsMenuHolder.MenuAction(JobsMenuHolder.ActionType.BACK, null));
        inventory.setItem(26, item(Material.BARRIER, messages.renderBare("menu.close"), List.of()));
        holder.action(26, new JobsMenuHolder.MenuAction(JobsMenuHolder.ActionType.CLOSE, null));

        if (job.enabled() && profile.state() == PlayerJobsProfile.State.READY) {
            JobProgress progress = profile.jobs().get(job.id());
            boolean joined = progress != null && progress.joined();
            if (joined) {
                List<Component> lore = plugin.runtime().config().keepLevelOnLeave()
                        ? List.of()
                        : List.of(messages.renderBare("menu.reset-warning"));
                inventory.setItem(22, item(Material.ORANGE_DYE, messages.renderBare("menu.leave"), lore));
                holder.action(22, new JobsMenuHolder.MenuAction(JobsMenuHolder.ActionType.LEAVE, job.id()));
            } else {
                inventory.setItem(22, item(Material.LIME_DYE, messages.renderBare("menu.join"), List.of()));
                holder.action(22, new JobsMenuHolder.MenuAction(JobsMenuHolder.ActionType.JOIN, job.id()));
            }
        }
        player.openInventory(inventory);
    }

    private void openLeaveConfirmation(Player player, JobDefinition job) {
        Messages messages = plugin.messages();
        JobsMenuHolder holder = new JobsMenuHolder(player.getUniqueId(), JobsMenuHolder.View.CONFIRM_LEAVE, job.id());
        Inventory inventory = Bukkit.createInventory(holder, 27,
                messages.renderBare("menu.confirm-leave-title", Placeholder.component("job", messages.parse(job.displayName()))));
        holder.bind(inventory);

        inventory.setItem(11, item(Material.GRAY_DYE, messages.renderBare("menu.cancel"), List.of()));
        holder.action(11, new JobsMenuHolder.MenuAction(JobsMenuHolder.ActionType.BACK, job.id()));
        inventory.setItem(13, item(job.icon(), messages.parse(job.displayName()),
                List.of(messages.renderBare("menu.reset-warning"))));
        inventory.setItem(15, item(Material.RED_CONCRETE, messages.renderBare("menu.confirm-leave"),
                List.of(messages.renderBare("menu.reset-warning"))));
        holder.action(15, new JobsMenuHolder.MenuAction(JobsMenuHolder.ActionType.CONFIRM_LEAVE, job.id()));
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
            case BACK -> {
                if (holder.view() == JobsMenuHolder.View.CONFIRM_LEAVE && action.jobId() != null) {
                    defer(() -> openDetails(player, action.jobId()));
                } else {
                    defer(() -> openOverview(player));
                }
            }
            case OPEN_JOB -> defer(() -> openDetails(player, action.jobId()));
            case JOIN -> mutateMembership(player, action.jobId(), true, false);
            case LEAVE -> {
                JobDefinition job = plugin.runtime().registry().find(action.jobId()).orElse(null);
                if (job == null) {
                    player.sendMessage(plugin.messages().render("unknown-job"));
                } else if (plugin.runtime().config().keepLevelOnLeave()) {
                    mutateMembership(player, job.id(), false, false);
                } else {
                    defer(() -> openLeaveConfirmation(player, job));
                }
            }
            case CONFIRM_LEAVE -> mutateMembership(player, action.jobId(), false, true);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof JobsMenuHolder)) return;
        if (event.getRawSlots().stream().anyMatch(slot -> slot < top.getSize())) event.setCancelled(true);
    }

    private void mutateMembership(Player player, String jobId, boolean joining, boolean confirmed) {
        JobDefinition job = plugin.runtime().registry().find(jobId).orElse(null);
        if (job == null) {
            player.sendMessage(plugin.messages().render("unknown-job"));
            return;
        }
        if (!job.enabled()) {
            player.sendMessage(plugin.messages().render("job-disabled"));
            return;
        }
        if (!joining && !plugin.runtime().config().keepLevelOnLeave() && !confirmed) {
            defer(() -> openLeaveConfirmation(player, job));
            return;
        }

        PlexonJobsAPI.Result result = joining
                ? plugin.runtime().joinJob(player.getUniqueId(), job.id())
                : plugin.runtime().leaveJob(player.getUniqueId(), job.id());
        if (result.success()) {
            player.sendActionBar(plugin.messages().render(joining ? "joined" : "left",
                    Placeholder.component("job", plugin.messages().parse(job.displayName()))));
            defer(() -> openDetails(player, job.id()));
        } else {
            player.sendMessage(Component.text(result.message(), NamedTextColor.RED));
            defer(() -> openDetails(player, job.id()));
        }
    }

    private ItemStack overviewItem(Player player, PlayerJobsProfile profile, JobDefinition job) {
        Messages messages = plugin.messages();
        List<Component> lore = new ArrayList<>();
        lore.add(job.enabled() ? messages.renderBare("menu.available") : messages.renderBare("menu.disabled"));
        if (profile.state() == PlayerJobsProfile.State.READY) {
            JobProgress progress = profile.jobs().get(job.id());
            int level = progress == null ? 1 : progress.level();
            long xp = progress == null ? 0 : progress.totalXp();
            boolean joined = progress != null && progress.joined();
            lore.add(joined ? messages.renderBare("menu.joined") : messages.renderBare("menu.inactive"));
            lore.add(Component.text("Level: " + level + "/" + job.maxLevel(), NamedTextColor.GRAY));
            lore.add(Component.text("Total XP: " + xp, NamedTextColor.GRAY));
            lore.add(Component.text("XP to next: " + plugin.runtime().registry().curve(job.id()).xpToNextLevel(xp), NamedTextColor.GRAY));
            appendDailyEarnings(player, job, lore);
        } else {
            lore.add(messages.renderBare("menu.loading"));
        }
        if (!job.enabled()) lore.add(messages.renderBare("menu.disabled-detail"));
        lore.add(Component.empty());
        lore.add(messages.renderBare("menu.open-details"));
        return item(job.icon(), messages.parse(job.displayName()), lore);
    }

    private ItemStack detailsItem(Player player, PlayerJobsProfile profile, JobDefinition job) {
        Messages messages = plugin.messages();
        List<Component> lore = new ArrayList<>();
        lore.add(job.enabled() ? messages.renderBare("menu.available") : messages.renderBare("menu.disabled"));
        if (profile.state() == PlayerJobsProfile.State.READY) {
            JobProgress progress = profile.jobs().get(job.id());
            int level = progress == null ? 1 : progress.level();
            long xp = progress == null ? 0 : progress.totalXp();
            boolean joined = progress != null && progress.joined();
            lore.add(joined ? messages.renderBare("menu.joined") : messages.renderBare("menu.inactive"));
            lore.add(Component.text("Level: " + level + "/" + job.maxLevel(), NamedTextColor.GRAY));
            lore.add(Component.text("Total XP: " + xp, NamedTextColor.GRAY));
            lore.add(Component.text("XP to next: " + plugin.runtime().registry().curve(job.id()).xpToNextLevel(xp), NamedTextColor.GRAY));
            appendDailyEarnings(player, job, lore);
        } else {
            lore.add(messages.renderBare("menu.loading"));
        }
        if (!job.enabled()) {
            lore.add(Component.empty());
            lore.add(messages.renderBare("menu.disabled-detail"));
        }
        return item(job.icon(), messages.parse(job.displayName()), lore);
    }

    private void appendDailyEarnings(Player player, JobDefinition job, List<Component> lore) {
        if (!plugin.dailyPersistence().ready(player.getUniqueId())) {
            lore.add(plugin.messages().renderBare("daily-state-loading"));
            return;
        }
        DailyLimitService.CounterView earned = plugin.limits().view(player.getUniqueId(), job.id());
        lore.add(Component.text("Earned today: " + Money.format(earned.moneyMinor(),
                plugin.runtime().config().moneyScale()), NamedTextColor.GRAY));
    }

    private ItemStack item(Material material, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(noItalic(name));
        if (!lore.isEmpty()) meta.lore(lore.stream().map(this::noItalic).toList());
        item.setItemMeta(meta);
        return item;
    }

    private Component noItalic(Component component) {
        return component.decoration(TextDecoration.ITALIC, false);
    }

    private void defer(Runnable action) {
        Bukkit.getScheduler().runTask(plugin, action);
    }
}
