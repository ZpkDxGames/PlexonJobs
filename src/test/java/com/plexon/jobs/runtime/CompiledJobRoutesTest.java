package com.plexon.jobs.runtime;

import com.plexon.jobs.model.ActivityReward;
import com.plexon.jobs.model.ActivityType;
import com.plexon.jobs.model.JobDefinition;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CompiledJobRoutesTest {
    @Test
    void typedBreakRoutesAndJobMasksAreCompiledOnce() {
        JobDefinition miner = new JobDefinition("miner", "Miner", Material.IRON_PICKAXE, true, 100,
                Map.of(Material.DIAMOND_ORE, new ActivityReward(5, 25)));
        JobDefinition woodcutter = new JobDefinition("woodcutter", "Woodcutter", Material.IRON_AXE, true, 100,
                Map.of(Material.OAK_LOG, new ActivityReward(2, 10)));
        CompiledJobRoutes routes = new CompiledJobRoutes(java.util.List.of(miner, woodcutter));

        long minerMask = routes.jobMask("miner");
        long woodMask = routes.jobMask("woodcutter");
        assertNotEquals(0L, minerMask);
        assertNotEquals(0L, woodMask);
        assertEquals(minerMask, routes.breakRoute(Material.DIAMOND_ORE).jobMask());
        assertEquals(5, routes.breakRoute(Material.DIAMOND_ORE).reward(Long.numberOfTrailingZeros(minerMask)).jobXpUnits());
        assertEquals(java.util.Set.of(Material.DIAMOND_ORE), routes.breakMaterialsForJobs(minerMask));
        assertEquals(java.util.Set.of(Material.DIAMOND_ORE, Material.OAK_LOG), routes.breakMaterialsForJobs(minerMask | woodMask));
    }

    @Test
    void wildcardAndExactGenericRoutesArePrecombinedWithoutPerEventSets() {
        JobDefinition hunter = new JobDefinition("hunter", "Hunter", Material.IRON_SWORD, true, 100, Map.of(),
                Map.of(ActivityType.KILL, Map.of("*", new ActivityReward(1, 2), "ZOMBIE", new ActivityReward(4, 8))));
        CompiledJobRoutes routes = new CompiledJobRoutes(java.util.List.of(hunter));
        int bit = Long.numberOfTrailingZeros(routes.jobMask("hunter"));

        assertEquals(4, routes.route(ActivityType.KILL, "ZOMBIE").reward(bit).jobXpUnits());
        assertEquals(1, routes.route(ActivityType.KILL, "SKELETON").reward(bit).jobXpUnits());
        assertTrue(routes.hasActivity(routes.activityMaskForJobs(routes.jobMask("hunter")), ActivityType.KILL));
    }

    @Test
    void catalogRejectsMoreThanSixtyFourJobs() {
        var definitions = new java.util.ArrayList<JobDefinition>();
        for (int i = 0; i < 65; i++) definitions.add(new JobDefinition("job" + i, "Job " + i, Material.PAPER, true, 10, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new CompiledJobRoutes(definitions));
    }
}
