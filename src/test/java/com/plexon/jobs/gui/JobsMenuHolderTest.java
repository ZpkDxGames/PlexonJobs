package com.plexon.jobs.gui;

import org.bukkit.inventory.Inventory;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JobsMenuHolderTest {
    @Test
    void holderCarriesViewerViewAndExplicitSlotActions() {
        UUID viewer = UUID.randomUUID();
        JobsMenuHolder holder = new JobsMenuHolder(viewer, JobsMenuHolder.View.DETAILS, "miner");
        JobsMenuHolder.MenuAction join = new JobsMenuHolder.MenuAction(JobsMenuHolder.ActionType.JOIN, "miner");

        holder.action(22, join);

        assertEquals(viewer, holder.viewerId());
        assertEquals(JobsMenuHolder.View.DETAILS, holder.view());
        assertEquals("miner", holder.jobId());
        assertSame(join, holder.action(22));
        assertNull(holder.action(13));
    }

    @Test
    void actionSnapshotsAreImmutableAndPresentationIndependent() {
        JobsMenuHolder holder = new JobsMenuHolder(UUID.randomUUID(), JobsMenuHolder.View.OVERVIEW, null);
        holder.action(10, new JobsMenuHolder.MenuAction(JobsMenuHolder.ActionType.OPEN_JOB, "miner"));
        Map<Integer, JobsMenuHolder.MenuAction> snapshot = holder.actions();

        holder.action(11, new JobsMenuHolder.MenuAction(JobsMenuHolder.ActionType.OPEN_JOB, "digger"));

        assertEquals("miner", snapshot.get(10).jobId());
        assertFalse(snapshot.containsKey(11), "Published action snapshots must not change behind a caller");
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.put(12, new JobsMenuHolder.MenuAction(JobsMenuHolder.ActionType.CLOSE, null)));
    }

    @Test
    void inventoryBindingIsOneShot() {
        JobsMenuHolder holder = new JobsMenuHolder(UUID.randomUUID(), JobsMenuHolder.View.OVERVIEW, null);
        assertThrows(IllegalStateException.class, holder::getInventory);

        Inventory inventory = (Inventory) Proxy.newProxyInstance(
                Inventory.class.getClassLoader(),
                new Class<?>[]{Inventory.class},
                (proxy, method, args) -> defaultValue(method.getReturnType()));
        holder.bind(inventory);

        assertSame(inventory, holder.getInventory());
        assertThrows(IllegalStateException.class, () -> holder.bind(inventory));
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        if (type == double.class) return 0D;
        if (type == char.class) return '\0';
        return null;
    }
}
