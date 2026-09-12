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
        int finalDaily = plugin.indexOf("dailyPersistence.saveAllBlocking();", finalShadow);
        int finalProfiles = plugin.indexOf("profiles.saveAllBlocking();", finalDaily);
        assertTrue(stopProducers >= 0 && awaitShadow > stopProducers && finalShadow > awaitShadow &&
                        finalDaily > finalShadow && finalProfiles > finalDaily,
                "Shutdown must stop producers, settle shadow IO, flush shadow/daily state, then save final profiles");

        String profiles = Files.readString(Path.of("src/main/java/com/plexon/jobs/runtime/ProfileManager.java"));
        int saveAll = profiles.indexOf("public void saveAllBlocking()");
        int awaitProfiles = profiles.indexOf("awaitInFlightSaves();", saveAll);
        int finalDatabaseSave = profiles.indexOf("database.save(snapshot", awaitProfiles);
        assertTrue(saveAll >= 0 && awaitProfiles > saveAll && finalDatabaseSave > awaitProfiles,
                "Final profile snapshots must be written only after older async saves settle");
    }

    @Test void stableRecoveryAndPersistenceContractsRemainPresent() throws Exception {
        String payouts = Files.readString(Path.of("src/main/java/com/plexon/jobs/economy/PayoutService.java"));
        assertTrue(payouts.contains("refreshEconomyAvailability()"),
                "Payout flushes must refresh Vault provider discovery so transient outages can recover");

        String profiles = Files.readString(Path.of("src/main/java/com/plexon/jobs/runtime/ProfileManager.java"));
        assertTrue(profiles.contains("LOAD_RETRY_NANOS"));
        assertTrue(profiles.contains("loadRetryAfter"));
        assertTrue(profiles.contains("PlayerJobsProfile.State.FAILED"));

        String database = Files.readString(Path.of("src/main/java/com/plexon/jobs/storage/JobsDatabase.java"));
        assertTrue(database.contains("DELETE FROM player_jobs WHERE player_uuid=?"),
                "Profile persistence must replace player rows exactly rather than retain removed jobs");

        String runtime = Files.readString(Path.of("src/main/java/com/plexon/jobs/runtime/JobsRuntime.java"));
        assertTrue(runtime.contains("if (amount < 0) return Result.fail"));
        assertTrue(runtime.contains("if (amount == 0) return Result.ok"));
    }

    @Test void shadowAsyncPathUsesAtomicBatchPersistence() throws Exception {
        String plugin = Files.readString(Path.of("src/main/java/com/plexon/jobs/PlexonJobs.java"));
        assertTrue(plugin.contains("database.addShadowBatch(deltas)"));
        assertFalse(plugin.contains("batch.forEach((key, total) -> database.addShadow"),
                "A drained shadow batch must never be persisted as independently committed rows");
    }

    @Test void yamlReloadValidationFailsClosedBeforeBukkitRuntimeMutation() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/plexon/jobs/config/ConfigLoader.java"));

        int configFile = source.indexOf("File configFile = new File(plugin.getDataFolder(), \"config.yml\")");
        int validateConfig = source.indexOf("validateYaml(configFile, \"config.yml\")", configFile);
        int reloadConfig = source.indexOf("plugin.reloadConfig();", validateConfig);
        assertTrue(configFile >= 0 && validateConfig > configFile && reloadConfig > validateConfig,
                "config.yml must be strictly parsed before Bukkit reloadConfig can mutate live configuration");

        int jobsFile = source.indexOf("File jobsFile = new File(plugin.getDataFolder(), \"jobs.yml\")");
        int validateJobs = source.indexOf("validateYaml(jobsFile, \"jobs.yml\")", jobsFile);
        int loadJobs = source.indexOf("YamlConfiguration.loadConfiguration(jobsFile)", validateJobs);
        assertTrue(jobsFile >= 0 && validateJobs > jobsFile && loadJobs > validateJobs,
                "jobs.yml must be strictly parsed before normal configuration loading");

        int helper = source.indexOf("static void validateYaml(File file, String label)");
        int strictLoad = source.indexOf("yaml.load(file);", helper);
        int invalidYamlCatch = source.indexOf("InvalidConfigurationException", helper);
        assertTrue(helper >= 0 && strictLoad > helper && invalidYamlCatch > helper,
                "strict YAML validation must perform a throwing parse and handle invalid YAML explicitly");
        assertTrue(source.contains("current runtime remains unchanged"),
                "reload rejection must document that the accepted runtime remains authoritative");
    }
}
