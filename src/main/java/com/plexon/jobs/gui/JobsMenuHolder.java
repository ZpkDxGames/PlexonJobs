package com.plexon.jobs.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Stable identity for every PlexonJobs custom inventory. Presentation is never parsed to determine
 * behavior; the holder owns the viewer, view state and slot-to-action map.
 */
public final class JobsMenuHolder implements InventoryHolder {
    public enum View { OVERVIEW, DETAILS, CONFIRM_LEAVE }
    public enum ActionType { OPEN_JOB, JOIN, LEAVE, CONFIRM_LEAVE, BACK, CLOSE }

    public record MenuAction(ActionType type, String jobId) {
        public MenuAction {
            Objects.requireNonNull(type, "type");
        }
    }

    private final UUID viewerId;
    private final View view;
    private final String jobId;
    private final Map<Integer, MenuAction> actions = new HashMap<>();
    private Inventory inventory;

    public JobsMenuHolder(UUID viewerId, View view, String jobId) {
        this.viewerId = Objects.requireNonNull(viewerId, "viewerId");
        this.view = Objects.requireNonNull(view, "view");
        this.jobId = jobId;
    }

    public UUID viewerId() { return viewerId; }
    public View view() { return view; }
    public String jobId() { return jobId; }

    public void bind(Inventory inventory) {
        if (this.inventory != null) throw new IllegalStateException("Inventory already bound");
        this.inventory = Objects.requireNonNull(inventory, "inventory");
    }

    public void action(int slot, MenuAction action) {
        if (slot < 0) throw new IllegalArgumentException("slot must be >= 0");
        actions.put(slot, Objects.requireNonNull(action, "action"));
    }

    public MenuAction action(int slot) {
        return actions.get(slot);
    }

    public Map<Integer, MenuAction> actions() {
        return Map.copyOf(actions);
    }

    @Override
    public @NotNull Inventory getInventory() {
        if (inventory == null) throw new IllegalStateException("Inventory not bound yet");
        return inventory;
    }
}
