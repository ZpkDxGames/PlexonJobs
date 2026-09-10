package com.plexon.jobs.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JobsDatabaseTest {
    @TempDir Path temp;

    @Test void initializesAndReportsCurrentSchema() {
        JobsDatabase database = new JobsDatabase(temp.resolve("jobs.db"));
        database.initialize();
        assertEquals(JobsDatabase.SCHEMA_VERSION, database.schemaVersion());
    }

    @Test void futureSchemaFailsClosed() throws Exception {
        Path path = temp.resolve("future.db");
        JobsDatabase database = new JobsDatabase(path);
        database.initialize();
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + path.toAbsolutePath());
             var statement = connection.prepareStatement("UPDATE migration_meta SET value='999' WHERE key='schema_version'")) {
            statement.executeUpdate();
        }
        IllegalStateException failure = assertThrows(IllegalStateException.class, database::initialize);
        assertTrue(failure.getMessage().contains("newer than supported"));
    }

    @Test void shadowBatchPreservesExactEventCount() {
        JobsDatabase database = new JobsDatabase(temp.resolve("shadow.db"));
        database.initialize();
        UUID id = UUID.randomUUID();
        database.addShadow(id, "miner", 200, 10, 10);
        database.addShadow(id, "miner", 140, 7, 7);
        var row = database.shadow(id, "miner");
        assertEquals(340, row.moneyMinor());
        assertEquals(17, row.xp());
        assertEquals(17, row.events());
    }
}
