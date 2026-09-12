package com.plexon.jobs.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Stable identity/state for every PlexonJobs inventory page. Titles and item presentation never control behavior. */
public final class JobsMenuHolder implements InventoryHolder {
    public enum View { DASHBOARD, BROWSER, PROFILE, DETAILS }
    public enum Filter { ALL, JOINED, AVAILABLE }
    public enum ActionType {
        BROWSE, PROFILE, OPEN_JOB, JOIN, LEAVE, BACK, DASHBOARD,
        PREVIOUS_PAGE, NEXT_PAGE, FILTER_CYCLE, CLOSE
    }

    public record MenuAction(ActionType type, String jobId, Integer page, Filter filter) {
        public MenuAction {
            Objects.requireNonNull(type, "type");
        }
        public MenuAction(ActionType type, String jobId) { this(type, jobId, null, null); }
        public MenuAction(ActionType type, int page, Filter filter) { this(type, null, page, filter); }
    }

    private final UUID viewerId;
    private final View view;
    private final String jobId;
    private final int page;
    private final Filter filter;
    private final Map<Integer, MenuAction> actions = new HashMap<>();
    private Inventory inventory;

    public JobsMenuHolder(UUID viewerId, View view, String jobId) {
        this(viewerId, view, jobId, 0, Filter.ALL);
    }

    public JobsMenuHolder(UUID viewerId, View view, String jobId, int page, Filter filter) {
        this.viewerId = Objects.requireNonNull(viewerId, "viewerId");
        this.view = Objects.requireNonNull(view, "view");
        this.jobId = jobId;
        this.page = Math.max(0, page);
        this.filter = filter == null ? Filter.ALL : filter;
    }

    public UUID viewerId() { return viewerId; }
    public View view() { return view; }
    public String jobId() { return jobId; }
    public int page() { return page; }
    public Filter filter() { return filter; }

    public void bind(Inventory inventory) {
        if (this.inventory != null) throw new IllegalStateException("Inventory already bound");
        this.inventory = Objects.requireNonNull(inventory, "inventory");
    }

    public void action(int slot, MenuAction action) {
        if (slot < 0) throw new IllegalArgumentException("slot must be >= 0");
        actions.put(slot, Objects.requireNonNull(action, "action"));
    }

    public MenuAction action(int slot) { return actions.get(slot); }
    public Map<Integer, MenuAction> actions() { return Map.copyOf(actions); }

    @Override
    public @NotNull Inventory getInventory() {
        if (inventory == null) throw new IllegalStateException("Inventory not bound yet");
        return inventory;
    }
}
