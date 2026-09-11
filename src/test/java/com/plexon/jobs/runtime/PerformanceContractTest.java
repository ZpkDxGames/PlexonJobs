package com.plexon.jobs.runtime;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PerformanceContractTest {
    @Test void blockHotPathContainsNoVaultFlushDatabaseSchedulerOrYamlWork() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/plexon/jobs/runtime/BlockActivityRouter.java"));
        assertFalse(source.contains("payouts.flush("), "Vault flushing is forbidden in the work-event path");
        assertFalse(source.contains("database."), "database work is forbidden in the work-event path");
        assertFalse(source.contains("runTask"), "one scheduler task per work event is forbidden");
        assertFalse(source.contains("YamlConfiguration"), "configuration parsing is forbidden in the work-event path");
        assertFalse(source.contains("registry.definitions()"), "global job scans are forbidden in indexed work-event routing");
    }

    @Test void placeholderPathContainsNoDatabaseVaultOrGlobalDefinitionScan() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/plexon/jobs/integration/PlexonJobsExpansion.java"));
        assertFalse(source.contains("database."));
        assertFalse(source.contains("Vault"));
        assertFalse(source.contains("registry().definitions()"));
    }

    @Test void shutdownBarriersPrecedeFinalAuthoritativePersistence() throws Exception {
        String plugin = Files.readString(Path.of("src/main/java/com/plexon/jobs/PlexonJobs.java"));
        int stopProducers = plugin.indexOf("cancelTasks();");
        int awaitShadow = plugin.indexOf("awaitShadowWrites();", stopProducers);
        int finalShadow = plugin.indexOf("flushShadowBlocking();", awaitShadow);
        int finalProfiles = plugin.indexOf("profiles.saveAllBlocking();", finalShadow);
        assertTrue(stopProducers >= 0 && awaitShadow > stopProducers && finalShadow > awaitShadow && finalProfiles > finalShadow,
                "Shutdown must stop producers, settle shadow IO, flush final shadow state, then save final profiles");

        String profiles = Files.readString(Path.of("src/main/java/com/plexon/jobs/runtime/ProfileManager.java"));
        int saveAll = profiles.indexOf("public void saveAllBlocking()");
        int awaitProfiles = profiles.indexOf("awaitInFlightSaves();", saveAll);
        int finalDatabaseSave = profiles.indexOf("database.save(snapshot", awaitProfiles);
        assertTrue(saveAll >= 0 && awaitProfiles > saveAll && finalDatabaseSave > awaitProfiles,
                "Final profile snapshots must be written only after older async saves settle");
    }

    @Test void shadowAsyncPathUsesAtomicBatchPersistence() throws Exception {
        String plugin = Files.readString(Path.of("src/main/java/com/plexon/jobs/PlexonJobs.java"));
        assertTrue(plugin.contains("database.addShadowBatch(deltas)"));
        assertFalse(plugin.contains("batch.forEach((key, total) -> database.addShadow"),
                "A drained shadow batch must never be persisted as independently committed rows");
    }
}
