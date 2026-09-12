package com.plexon.jobs.storage;

import com.plexon.jobs.model.JobProgress;
import com.plexon.jobs.model.PlayerJobsProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.List;
import java.util.Map;
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

    @Test void profileSaveReplacesRemovedRowsExactly() {
        Path path = temp.resolve("profiles.db");
        UUID id = UUID.randomUUID();
        JobsDatabase database = new JobsDatabase(path);
        database.initialize();

        PlayerJobsProfile initial = new PlayerJobsProfile(id, PlayerJobsProfile.State.READY);
        initial.put("miner", new JobProgress(250, 2, true));
        initial.put("retired_job", new JobProgress(100, 1, true));
        database.save(initial.snapshot(), "Player");

        PlayerJobsProfile current = new PlayerJobsProfile(id, PlayerJobsProfile.State.READY);
        current.put("miner", new JobProgress(500, 3, true));
        database.save(current.snapshot(), "Player");

        JobsDatabase reopened = new JobsDatabase(path);
        reopened.initialize();
        PlayerJobsProfile restored = reopened.load(id);
        assertEquals(1, restored.jobs().size());
        assertFalse(restored.jobs().containsKey("retired_job"), "Removed job rows must not resurrect after restart");
        assertEquals(500, restored.jobs().get("miner").totalXp());
        assertEquals(3, restored.jobs().get("miner").level());
        assertTrue(restored.jobs().get("miner").joined());
    }

    @Test void dailySnapshotsSurviveRestartAndOverwriteAbsolutely() {
        Path path = temp.resolve("daily.db");
        UUID id = UUID.randomUUID();
        long dayId = 20_000L;
        JobsDatabase first = new JobsDatabase(path);
        first.initialize();
        first.saveDaily(id, dayId, Map.of(
                "miner", new JobsDatabase.DailyRow(125, 25),
                "digger", new JobsDatabase.DailyRow(40, 9)));
        first.saveDaily(id, dayId, Map.of(
                "miner", new JobsDatabase.DailyRow(150, 30),
                "digger", new JobsDatabase.DailyRow(40, 9)));

        JobsDatabase reopened = new JobsDatabase(path);
        reopened.initialize();
        Map<String, JobsDatabase.DailyRow> restored = reopened.loadDaily(id, dayId);
        assertEquals(new JobsDatabase.DailyRow(150, 30), restored.get("miner"));
        assertEquals(new JobsDatabase.DailyRow(40, 9), restored.get("digger"));
        assertEquals(2, restored.size());
        assertTrue(reopened.loadDaily(id, dayId + 1).isEmpty());
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

    @Test void failedShadowBatchCannotCommitSuccessfulPrefix() {
        JobsDatabase database = new JobsDatabase(temp.resolve("shadow-atomic.db"));
        database.initialize();
        UUID id = UUID.randomUUID();
        database.addShadow(id, "miner", 50, 5, 1);

        List<JobsDatabase.ShadowDelta> batch = List.of(
                new JobsDatabase.ShadowDelta(id, "miner", 100, 10, 2),
                new JobsDatabase.ShadowDelta(id, null, 999, 999, 9));

        assertThrows(IllegalStateException.class, () -> database.addShadowBatch(batch));
        var row = database.shadow(id, "miner");
        assertEquals(50, row.moneyMinor(), "Failed atomic batch must not retain an earlier successful prefix");
        assertEquals(5, row.xp());
        assertEquals(1, row.events());
    }
}
