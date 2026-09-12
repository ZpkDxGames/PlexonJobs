package com.plexon.jobs.model;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PlayerJobsProfileTest {
    @Test void retainJobsPrunesObsoleteDefinitionsAndAdvancesRevision() {
        PlayerJobsProfile profile = new PlayerJobsProfile(UUID.randomUUID(), PlayerJobsProfile.State.READY);
        profile.put("miner", new JobProgress(250, 2, true));
        profile.put("retired", new JobProgress(100, 1, true));
        long before = profile.revision();

        assertTrue(profile.retainJobs(Set.of("miner", "digger")));
        assertEquals(before + 1, profile.revision());
        assertEquals(Set.of("miner"), profile.jobs().keySet());
        assertEquals(1, profile.activeCount());

        long stable = profile.revision();
        assertFalse(profile.retainJobs(Set.of("miner", "digger")));
        assertEquals(stable, profile.revision(), "No-op reconciliation must not create a false dirty revision");
    }
}
