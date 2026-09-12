package com.plexon.jobs.runtime;

import com.plexon.jobs.PlexonJobs;
import com.zpkdxgames.plexoncore.event.CoreBlockSubscription;

import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;

/** Transition-driven PlexonCore block subscription. It is never rebuilt from a block callback. */
public final class CoreBlockSubscriptionCoordinator implements AutoCloseable {
    private final PlexonJobs plugin;
    private AutoCloseable subscription;
    private Set<org.bukkit.Material> materials = Set.of();
    private BlockActivityRouter subscribedRouter;

    public CoreBlockSubscriptionCoordinator(PlexonJobs plugin) {
        this.plugin = Objects.requireNonNull(plugin);
    }

    public void reconcile() {
        boolean dynamic = plugin.runtime().config().performance().dynamicCoreBlockSubscription();
        Set<org.bukkit.Material> desired = dynamic
                ? plugin.compiledRoutes().breakMaterialsForJobs(plugin.interest().globalJoinedJobMask())
                : plugin.compiledRoutes().allBreakMaterials();
        BlockActivityRouter router = plugin.blockRouter();
        if (desired.equals(materials) && router == subscribedRouter) return;

        if (desired.isEmpty()) {
            closeCurrent();
            materials = Set.of();
            subscribedRouter = router;
            plugin.metrics().blockSubscription(false, 0);
            return;
        }

        AutoCloseable next = plugin.core().events().subscribeBlockBreak("plexonjobs",
                CoreBlockSubscription.builder().materials(desired).requiresNaturalOrigin(true).build(), router::handle);
        AutoCloseable previous = subscription;
        subscription = next;
        materials = Set.copyOf(desired);
        subscribedRouter = router;
        close(previous);
        plugin.metrics().blockSubscription(true, materials.size());
    }

    public boolean active() { return subscription != null; }
    public int materialCount() { return materials.size(); }

    @Override
    public void close() {
        closeCurrent();
        materials = Set.of();
        subscribedRouter = null;
        plugin.metrics().blockSubscription(false, 0);
    }

    private void closeCurrent() {
        AutoCloseable previous = subscription;
        subscription = null;
        close(previous);
    }

    private void close(AutoCloseable value) {
        if (value == null) return;
        try { value.close(); }
        catch (Exception failure) { plugin.getLogger().log(Level.WARNING, "Failed to close Core block subscription", failure); }
    }
}
