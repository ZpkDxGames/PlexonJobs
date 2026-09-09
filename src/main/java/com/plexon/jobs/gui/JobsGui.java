package com.plexon.jobs.gui;

import com.plexon.jobs.api.PlexonJobsAPI;
import com.plexon.jobs.model.JobDefinition;
import com.plexon.jobs.model.JobProgress;
import com.plexon.jobs.runtime.DailyLimitService;
import com.plexon.jobs.runtime.JobsRuntime;
import com.plexon.jobs.util.Money;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public final class JobsGui {
    private final JobsRuntime runtime;
    private final DailyLimitService limits;

    public JobsGui(JobsRuntime runtime, DailyLimitService limits) {
        this.runtime = runtime;
        this.limits = limits;
    }

    public void open(Player player) {
        Inventory inventory = Bukkit.createInventory(null, 54, "PlexonJobs");
        var profile = runtime.profiles().ensure(player.getUniqueId());
        int slot = 10;
        for (JobDefinition job : runtime.registry().definitions()) {
            if (slot >= 44) break;
            ItemStack item = new ItemStack(job.icon());
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName((job.enabled() ? ChatColor.GOLD : ChatColor.DARK_GRAY) + stripMini(job.displayName()));
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "ID: " + job.id());
            lore.add(ChatColor.GRAY + "Status: " + (job.enabled() ? ChatColor.GREEN + "Available" : ChatColor.RED + "Disabled"));
            if (profile.state() == com.plexon.jobs.model.PlayerJobsProfile.State.READY) {
                JobProgress progress = profile.jobs().get(job.id());
                int level = progress == null ? 1 : progress.level();
                long xp = progress == null ? 0 : progress.totalXp();
                boolean joined = progress != null && progress.joined();
                lore.add(ChatColor.GRAY + "Joined: " + (joined ? ChatColor.GREEN + "Yes" : ChatColor.RED + "No"));
                lore.add(ChatColor.GRAY + "Level: " + ChatColor.WHITE + level + "/" + job.maxLevel());
                lore.add(ChatColor.GRAY + "Total XP: " + ChatColor.WHITE + xp);
                lore.add(ChatColor.GRAY + "XP to next: " + ChatColor.WHITE + runtime.registry().curve(job.id()).xpToNextLevel(xp));
                DailyLimitService.CounterView earned = limits.view(player.getUniqueId(), job.id());
                lore.add(ChatColor.GRAY + "Earned today: " + ChatColor.GREEN + Money.format(earned.moneyMinor(), runtime.config().moneyScale()));
            } else {
                lore.add(ChatColor.YELLOW + "Profile loading...");
            }
            if (job.enabled()) lore.add(ChatColor.AQUA + "Use /jobs join " + job.id());
            meta.setLore(lore);
            item.setItemMeta(meta);
            inventory.setItem(slot, item);
            slot++;
            if ((slot + 1) % 9 == 0) slot += 2;
        }
        player.openInventory(inventory);
    }

    private static String stripMini(String input) {
        return input == null ? "Job" : input.replaceAll("<[^>]+>", "");
    }
}
