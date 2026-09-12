package com.plexon.jobs.runtime;

import com.plexon.jobs.PlexonJobs;
import com.plexon.jobs.model.ActivityType;
import io.papermc.paper.event.inventory.ItemCraftedEvent;
import org.bukkit.NamespacedKey;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.SmithItemEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerHarvestBlockEvent;
import org.bukkit.event.player.PlayerItemMendEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.Locale;
import java.util.Objects;

/** Native Paper activity adapters for job families not currently exposed through PlexonCore. */
public final class NativeActivityListener implements Listener {
    private static final int ANVIL_SECOND_INPUT_SLOT = 1;
    private static final int ANVIL_RESULT_SLOT = 2;

    private final PlexonJobs plugin;
    private final PlacementCreditCache placementCredits;
    private final BrewerAttributionTracker brewerAttribution;
    private final NamespacedKey hunterSpawnReason;

    public NativeActivityListener(PlexonJobs plugin) {
        this.plugin = Objects.requireNonNull(plugin);
        this.placementCredits = new PlacementCreditCache(
                plugin.runtime().config().activity().builderRepeatWindowSeconds(),
                plugin.runtime().config().activity().builderMaxTrackedPositions());
        this.brewerAttribution = new BrewerAttributionTracker(
                plugin.runtime().config().activity().brewerAttributionSeconds());
        this.hunterSpawnReason = new NamespacedKey(plugin, "hunter_spawn_reason");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMatureCropBreak(BlockBreakEvent event) {
        if (!plugin.runtime().registry().handles(ActivityType.FARM)) return;
        if (!(event.getBlock().getBlockData() instanceof Ageable ageable) || ageable.getAge() < ageable.getMaximumAge()) return;
        plugin.grants().handle(event.getPlayer(), ActivityType.FARM, event.getBlock().getType().name(), 1,
                "paper:farm-break:" + blockSource(event.getBlock().getX(), event.getBlock().getY(), event.getBlock().getZ()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHarvest(PlayerHarvestBlockEvent event) {
        if (!plugin.runtime().registry().handles(ActivityType.FARM)) return;
        plugin.grants().handle(event.getPlayer(), ActivityType.FARM, event.getHarvestedBlock().getType().name(), 1,
                "paper:harvest:" + blockSource(event.getHarvestedBlock().getX(), event.getHarvestedBlock().getY(), event.getHarvestedBlock().getZ()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        event.getEntity().getPersistentDataContainer().set(hunterSpawnReason, PersistentDataType.STRING,
                event.getSpawnReason().name());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(EntityDeathEvent event) {
        if (!plugin.runtime().registry().handles(ActivityType.KILL)) return;
        LivingEntity dead = event.getEntity();
        Player killer = dead.getKiller();
        if (killer == null) return;
        String reason = dead.getPersistentDataContainer().get(hunterSpawnReason, PersistentDataType.STRING);
        if (reason == null || !plugin.runtime().config().activity().hunterAllowedSpawnReasons().contains(reason.toUpperCase(Locale.ROOT))) return;
        plugin.grants().handle(killer, ActivityType.KILL, dead.getType().name(), 1,
                "paper:kill:" + dead.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (!plugin.runtime().registry().handles(ActivityType.FISH) || event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        Entity caught = event.getCaught();
        if (caught == null) return;
        String key;
        long units = 1;
        if (caught instanceof Item item) {
            ItemStack stack = item.getItemStack();
            key = stack.getType().name();
            units = Math.max(1, stack.getAmount());
        } else {
            key = caught.getType().name();
        }
        plugin.grants().handle(event.getPlayer(), ActivityType.FISH, key, units,
                "paper:fish:" + caught.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!plugin.runtime().registry().handles(ActivityType.PLACE) || !event.canBuild()) return;
        var block = event.getBlockPlaced();
        if (!placementCredits.credit(event.getPlayer().getUniqueId(), block.getWorld().getUID(),
                block.getX(), block.getY(), block.getZ(), System.nanoTime())) return;
        plugin.grants().handle(event.getPlayer(), ActivityType.PLACE, block.getType().name(), 1,
                "paper:place:" + blockSource(block.getX(), block.getY(), block.getZ()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCraft(ItemCraftedEvent event) {
        if (!plugin.runtime().registry().handles(ActivityType.CRAFT)) return;
        ItemStack crafted = event.getCraftedItem();
        if (crafted == null || crafted.getType().isAir()) return;
        plugin.grants().handle(event.getPlayer(), ActivityType.CRAFT, crafted.getType().name(), Math.max(1, crafted.getAmount()),
                "paper:craft:" + crafted.getType().name());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onFurnaceExtract(FurnaceExtractEvent event) {
        if (!plugin.runtime().registry().handles(ActivityType.SMELT)) return;
        plugin.grants().handle(event.getPlayer(), ActivityType.SMELT, event.getItemType().name(), Math.max(1, event.getItemAmount()),
                "paper:smelt:" + event.getBlock().getWorld().getUID() + ":" + blockSource(event.getBlock().getX(), event.getBlock().getY(), event.getBlock().getZ()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSmith(SmithItemEvent event) {
        if (!plugin.runtime().registry().handles(ActivityType.REPAIR) || !(event.getWhoClicked() instanceof Player player)) return;
        ItemStack result = event.getCurrentItem();
        if (result == null || result.getType().isAir()) return;
        plugin.grants().handle(player, ActivityType.REPAIR, result.getType().name(), 1,
                "paper:smith:" + result.getType().name());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAnvilResult(InventoryClickEvent event) {
        if (!plugin.runtime().registry().handles(ActivityType.REPAIR) || !(event.getWhoClicked() instanceof Player player)) return;
        if (event.getView().getTopInventory().getType() != InventoryType.ANVIL || event.getRawSlot() != ANVIL_RESULT_SLOT) return;
        ItemStack secondInput = event.getView().getTopInventory().getItem(ANVIL_SECOND_INPUT_SLOT);
        if (secondInput == null || secondInput.getType().isAir()) return;
        ItemStack result = event.getCurrentItem();
        if (result == null || result.getType().isAir()) return;
        plugin.grants().handle(player, ActivityType.REPAIR, result.getType().name(), 1,
                "paper:anvil:" + result.getType().name());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMend(PlayerItemMendEvent event) {
        if (!plugin.runtime().registry().handles(ActivityType.REPAIR) || event.getRepairAmount() <= 0) return;
        plugin.grants().handle(event.getPlayer(), ActivityType.REPAIR, event.getItem().getType().name(),
                Math.max(1, event.getRepairAmount()), "paper:mend");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBrewerInteraction(InventoryClickEvent event) {
        if (!plugin.runtime().registry().handles(ActivityType.BREW) || !(event.getWhoClicked() instanceof Player player)) return;
        if (event.getView().getTopInventory().getType() != InventoryType.BREWING) return;
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;
        brewerAttribution.touch(event.getView().getTopInventory().getLocation(), player.getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBrew(BrewEvent event) {
        if (!plugin.runtime().registry().handles(ActivityType.BREW)) return;
        var playerId = brewerAttribution.consume(event.getBlock().getLocation(), System.currentTimeMillis()).orElse(null);
        if (playerId == null) return;
        Player player = plugin.getServer().getPlayer(playerId);
        if (player == null) return;
        ItemStack ingredient = event.getContents().getIngredient();
        String key = ingredient == null || ingredient.getType().isAir() ? "*" : ingredient.getType().name();
        long resultCount = event.getResults().stream().filter(Objects::nonNull).filter(stack -> !stack.getType().isAir()).count();
        if (resultCount <= 0) return;
        plugin.grants().handle(player, ActivityType.BREW, key, resultCount,
                "paper:brew:" + event.getBlock().getWorld().getUID() + ":" + blockSource(event.getBlock().getX(), event.getBlock().getY(), event.getBlock().getZ()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent event) {
        if (!plugin.runtime().registry().handles(ActivityType.ENCHANT)) return;
        plugin.grants().handle(event.getEnchanter(), ActivityType.ENCHANT, event.getItem().getType().name(),
                Math.max(1, event.getExpLevelCost()), "paper:enchant:" + event.getItem().getType().name());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        placementCredits.remove(event.getPlayer().getUniqueId());
    }

    private static String blockSource(int x, int y, int z) {
        return x + ":" + y + ":" + z;
    }
}
