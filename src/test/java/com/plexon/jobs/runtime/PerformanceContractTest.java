package com.plexon.jobs.runtime;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PerformanceContractTest {
    @Test
    void breakHotPathRejectsByCompiledMembershipBeforePlayerLookup() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/plexon/jobs/runtime/BlockActivityRouter.java"));
        int route = source.indexOf("routes.breakRoute(context.material())");
        int matched = source.indexOf("state.joinedJobMask() & route.jobMask()", route);
        int playerLookup = source.indexOf("Bukkit.getPlayer(context.playerId())", matched);
        assertTrue(route >= 0 && matched > route && playerLookup > matched,
                "BREAK must use the compiled route and player mask before Bukkit player lookup");
        assertFalse(source.contains("Material.matchMaterial"));
        assertFalse(source.contains("profiles.ensure"));
        assertFalse(source.contains("dailyPersistence.ensure"));
        assertFalse(source.contains("registry.breakJobs"));
        assertFalse(source.contains("grants.handle(player, ActivityType.BREAK"),
                "BREAK must use its specialized typed grant path");
    }

    @Test
    void gameplayGrantPathContainsNoHydrationIoVaultFlushYamlOrPerEventScheduling() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/plexon/jobs/runtime/ActivityGrantService.java"));
        assertFalse(source.contains("profiles.ensure("));
        assertFalse(source.contains("dailyPersistence.ensure("));
        assertFalse(source.contains("database."));
        assertFalse(source.contains("payouts.flush("));
        assertFalse(source.contains("runTask("));
        assertFalse(source.contains("runTaskAsynchronously"));
        assertFalse(source.contains("YamlConfiguration"));
        assertFalse(source.contains("Material.matchMaterial"));
        assertTrue(source.contains("state.joinedJobMask() & route.jobMask()"));
        assertTrue(source.contains("handleBreak(Player player, Material material"));
    }

    @Test
    void compiledRoutesAndInterestIndexAreAuthoritativeRuntimePrimitives() throws Exception {
        String routes = Files.readString(Path.of("src/main/java/com/plexon/jobs/runtime/CompiledJobRoutes.java"));
        String interest = Files.readString(Path.of("src/main/java/com/plexon/jobs/runtime/ActivityInterestIndex.java"));
        assertTrue(routes.contains("at most 64 compiled job definitions"));
        assertTrue(routes.contains("Map<Material, CompiledRoute> breakRoutes"));
        assertTrue(routes.contains("activityMaskForJobs"));
        assertTrue(interest.contains("PlayerExecutionState"));
        assertTrue(interest.contains("joinedJobMask"));
        assertTrue(interest.contains("participantCount"));
        assertTrue(interest.contains("topologyListener.run()"));
    }

    @Test
    void dynamicListenersAndCoreSubscriptionAreTransitionDriven() throws Exception {
        String listeners = Files.readString(Path.of("src/main/java/com/plexon/jobs/runtime/ActivityListenerCoordinator.java"));
        assertTrue(listeners.contains("HandlerList.unregisterAll"));
        assertTrue(listeners.contains("Bukkit.getPluginManager().registerEvents"));
        assertTrue(listeners.contains("dynamicListeners()"));

        String core = Files.readString(Path.of("src/main/java/com/plexon/jobs/runtime/CoreBlockSubscriptionCoordinator.java"));
        assertTrue(core.contains("breakMaterialsForJobs(plugin.interest().globalJoinedJobMask())"));
        assertTrue(core.contains("subscribeBlockBreak"));
        assertFalse(core.contains("BlockBreakEvent"));
    }

    @Test
    void monolithicNativeListenerIsRemovedAndFamiliesAreIndependent() throws Exception {
        assertFalse(Files.exists(Path.of("src/main/java/com/plexon/jobs/runtime/NativeActivityListener.java")));
        for (String listener : java.util.List.of(
                "FarmerActivityListener", "HunterActivityListener", "FisherActivityListener", "BuilderActivityListener",
                "CrafterActivityListener", "BlacksmithActivityListener", "BrewerActivityListener", "EnchanterActivityListener")) {
            assertTrue(Files.exists(Path.of("src/main/java/com/plexon/jobs/runtime/listener/" + listener + ".java")), listener);
        }
    }

    @Test
    void hunterMemoryModeAvoidsSpawnPdcWrites() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/plexon/jobs/runtime/HunterOriginTracker.java"));
        int memory = source.indexOf("HunterOriginTracking.MEMORY");
        int memoryReturn = source.indexOf("return;", memory);
        int pdcRemove = source.indexOf("getPersistentDataContainer().remove", memoryReturn);
        assertTrue(memory >= 0 && memoryReturn > memory && pdcRemove > memoryReturn,
                "MEMORY branch must return before PDC mutation");
        assertTrue(source.contains("memoryEligible.remove(id)"));
    }

    @Test
    void explorerTaskExistsOnlyForCompiledParticipantsAndNeverUsesMoveEvents() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/plexon/jobs/runtime/ExplorerDiscoveryService.java"));
        assertTrue(source.contains("participantCount(ActivityType.EXPLORE) > 0"));
        assertTrue(source.contains("participants(ActivityType.EXPLORE)"));
        assertTrue(source.contains("runTaskTimer"));
        assertFalse(source.contains("PlayerMoveEvent"));
        assertFalse(source.contains("getOnlinePlayers()"));
    }

    @Test
    void publicRewardEventsAreDemandGatedAndFeedbackIsDirect() throws Exception {
        String grant = Files.readString(Path.of("src/main/java/com/plexon/jobs/runtime/ActivityGrantService.java"));
        assertTrue(grant.contains("getHandlerList().getRegisteredListeners().length != 0"));
        assertTrue(grant.contains("feedback.onReward"));
        assertTrue(grant.contains("feedback.onLevelUp"));

        String feedback = Files.readString(Path.of("src/main/java/com/plexon/jobs/runtime/PlayerFeedbackService.java"));
        assertTrue(feedback.contains("implements RewardFeedbackSink"));
        assertFalse(feedback.contains("implements Listener"));
        assertFalse(feedback.contains("@EventHandler"));
    }

    @Test
    void feedbackRenderingIsGloballyBatched() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/plexon/jobs/runtime/PlayerFeedbackService.java"));
        int accumulate = source.indexOf("public void onReward");
        int flush = source.indexOf("public void flush()", accumulate);
        assertTrue(accumulate >= 0 && flush > accumulate);
        String hotSection = source.substring(accumulate, flush);
        assertFalse(hotSection.contains("BossBar.bossBar"));
        assertFalse(hotSection.contains("renderBare"));
        assertFalse(hotSection.contains("runTask"));
        assertTrue(source.substring(flush).contains("BossBar.bossBar"));

        String plugin = Files.readString(Path.of("src/main/java/com/plexon/jobs/PlexonJobs.java"));
        assertTrue(plugin.contains("feedback::flush"));
        assertTrue(plugin.contains("cfg.performance().feedbackFlushTicks()"));
    }

    @Test
    void guiUsesHolderActionsAndPaperDialogRatherThanPresentationIdentity() throws Exception {
        String holder = Files.readString(Path.of("src/main/java/com/plexon/jobs/gui/JobsMenuHolder.java"));
        String controller = Files.readString(Path.of("src/main/java/com/plexon/jobs/gui/JobsMenuController.java"));
        assertTrue(holder.contains("implements InventoryHolder"));
        assertTrue(controller.contains("InventoryDragEvent"));
        assertTrue(controller.contains("Dialog.create"));
        assertTrue(controller.contains("DialogType.confirmation"));
        assertFalse(controller.contains("getTitle()"));
        assertFalse(controller.contains("getDisplayName()"));
        assertFalse(controller.contains("createInventory(null"));
    }

    @Test
    void placeholderPathContainsNoDatabaseVaultOrGlobalDefinitionScan() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/plexon/jobs/integration/PlexonJobsExpansion.java"));
        assertFalse(source.contains("database."));
        assertFalse(source.contains("Vault"));
        assertFalse(source.contains("registry().definitions()"));
    }

    @Test
    void shutdownBarriersPrecedeFinalAuthoritativePersistence() throws Exception {
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

    @Test
    void stableRecoveryAndPersistenceContractsRemainPresent() throws Exception {
        String payouts = Files.readString(Path.of("src/main/java/com/plexon/jobs/economy/PayoutService.java"));
        assertTrue(payouts.contains("refreshEconomyAvailability()"));

        String profiles = Files.readString(Path.of("src/main/java/com/plexon/jobs/runtime/ProfileManager.java"));
        assertTrue(profiles.contains("LOAD_RETRY_NANOS"));
        assertTrue(profiles.contains("loadRetryAfter"));
        assertTrue(profiles.contains("PlayerJobsProfile.State.FAILED"));

        String database = Files.readString(Path.of("src/main/java/com/plexon/jobs/storage/JobsDatabase.java"));
        assertTrue(database.contains("DELETE FROM player_jobs WHERE player_uuid=?"));

        String runtime = Files.readString(Path.of("src/main/java/com/plexon/jobs/runtime/JobsRuntime.java"));
        assertTrue(runtime.contains("if (amount < 0) return Result.fail"));
        assertTrue(runtime.contains("if (amount == 0) return Result.ok"));
    }

    @Test
    void shadowAsyncPathUsesAtomicBatchPersistence() throws Exception {
        String plugin = Files.readString(Path.of("src/main/java/com/plexon/jobs/PlexonJobs.java"));
        assertTrue(plugin.contains("database.addShadowBatch(deltas)"));
        assertFalse(plugin.contains("batch.forEach((key, total) -> database.addShadow"));
    }

    @Test
    void yamlReloadValidationFailsClosedBeforeBukkitRuntimeMutation() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/plexon/jobs/config/ConfigLoader.java"));
        int configFile = source.indexOf("File configFile = new File(plugin.getDataFolder(), \"config.yml\")");
        int validateConfig = source.indexOf("validateYaml(configFile, \"config.yml\")", configFile);
        int reloadConfig = source.indexOf("plugin.reloadConfig();", validateConfig);
        assertTrue(configFile >= 0 && validateConfig > configFile && reloadConfig > validateConfig);

        int jobsFile = source.indexOf("File jobsFile = new File(plugin.getDataFolder(), \"jobs.yml\")");
        int validateJobs = source.indexOf("validateYaml(jobsFile, \"jobs.yml\")", jobsFile);
        int loadJobs = source.indexOf("YamlConfiguration.loadConfiguration(jobsFile)", validateJobs);
        assertTrue(jobsFile >= 0 && validateJobs > jobsFile && loadJobs > validateJobs);

        int helper = source.indexOf("static void validateYaml(File file, String label)");
        int strictLoad = source.indexOf("yaml.load(file);", helper);
        int invalidYamlCatch = source.indexOf("InvalidConfigurationException", helper);
        assertTrue(helper >= 0 && strictLoad > helper && invalidYamlCatch > helper);
        assertTrue(source.contains("current runtime remains unchanged"));
    }
}
