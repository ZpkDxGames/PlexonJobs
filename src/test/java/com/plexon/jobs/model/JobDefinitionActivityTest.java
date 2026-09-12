package com.plexon.jobs.model;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JobDefinitionActivityTest {
    @Test void exactActivityRewardOverridesWildcard() {
        JobDefinition job = new JobDefinition("fisher", "Fisher", Material.FISHING_ROD, true, 200,
                Map.of(), Map.of(ActivityType.FISH, Map.of(
                        "COD", new ActivityReward(3, 20),
                        "*", new ActivityReward(1, 10))));

        assertEquals(new ActivityReward(3, 20), job.reward(ActivityType.FISH, "cod"));
        assertEquals(new ActivityReward(1, 10), job.reward(ActivityType.FISH, "SALMON"));
        assertTrue(job.handles(ActivityType.FISH));
    }

    @Test void legacyBreakConstructorRemainsCompatible() {
        JobDefinition job = new JobDefinition("miner", "Miner", Material.IRON_PICKAXE, true, 200,
                Map.of(Material.STONE, new ActivityReward(1, 5)));

        assertEquals(new ActivityReward(1, 5), job.reward(ActivityType.BREAK, "STONE"));
        assertTrue(job.handlesBreaks());
        assertFalse(job.handles(ActivityType.KILL));
    }
}
