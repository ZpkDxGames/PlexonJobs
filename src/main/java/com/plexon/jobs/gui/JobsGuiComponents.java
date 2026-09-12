package com.plexon.jobs.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.List;

/** Shared immutable-looking GUI component factory. Returned ItemStacks are always fresh instances. */
public final class JobsGuiComponents {
    private JobsGuiComponents() { }

    public static ItemStack item(Material material, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(noItalic(name));
        if (lore != null && !lore.isEmpty()) meta.lore(lore.stream().map(JobsGuiComponents::noItalic).toList());
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack head(Player player, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.setOwningPlayer(player);
        meta.displayName(noItalic(name));
        if (lore != null && !lore.isEmpty()) meta.lore(lore.stream().map(JobsGuiComponents::noItalic).toList());
        item.setItemMeta(meta);
        return item;
    }

    public static Component line(String label, String value) {
        return Component.text(label + ": ", NamedTextColor.GRAY).append(Component.text(value, NamedTextColor.WHITE));
    }

    public static Component noItalic(Component component) {
        return component.decoration(TextDecoration.ITALIC, false);
    }
}
