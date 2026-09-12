package com.plexon.jobs.runtime;

import com.plexon.jobs.model.ActivityReward;
import com.plexon.jobs.model.ActivityType;
import com.plexon.jobs.model.JobDefinition;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JobRegistryActivityTest {
    @Test void exactAndWildcardRoutesAreCombinedWithoutDuplicates() {
        JobDefinition exactAndWildcard = new JobDefinition("hunter", "Hunter", Material.IRON_SWORD, true, 200,
                Map.of(), Map.of(ActivityType.KILL, Map.of(
                        "ZOMBIE", new ActivityReward(4, 30),
                        "*", new ActivityReward(1, 10))));
        JobDefinition wildcardOnly = new JobDefinition("generic", "Generic", Material.PAPER, true, 200,
                Map.of(), Map.of(ActivityType.KILL, Map.of("*", new ActivityReward(1, 5))));
        JobRegistry registry = new JobRegistry(List.of(exactAndWildcard, wildcardOnly));

        assertEquals(List.of(exactAndWildcard, wildcardOnly), registry.activityJobs(ActivityType.KILL, "ZOMBIE"));
        assertEquals(List.of(exactAndWildcard, wildcardOnly), registry.activityJobs(ActivityType.KILL, "SKELETON"));
        assertTrue(registry.handles(ActivityType.KILL));
    }
}
