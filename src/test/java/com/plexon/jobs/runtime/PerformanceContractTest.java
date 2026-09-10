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
}
