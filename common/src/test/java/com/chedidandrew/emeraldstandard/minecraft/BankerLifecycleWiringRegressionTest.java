package com.chedidandrew.emeraldstandard.minecraft;

import java.nio.file.Files;
import java.nio.file.Path;

/** Guards the loader lifecycle wiring that turns an infected Banker's death into replacement authority. */
public final class BankerLifecycleWiringRegressionTest {
    private static final String ZOMBIE_BRANCH =
            "instanceof ZombieVillager zombieVillager) {";
    private static final String BANK_DEATH_CALL =
            "VillageBankManager.onZombieBankerDeath(zombieVillager, ECONOMY);";
    private static final String VILLAGE_DEATH_CALL =
            "VillageProsperityManager.onZombieVillagerDeath(";

    private BankerLifecycleWiringRegressionTest() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Repository root argument is required");
        }
        Path root = Path.of(args[0]);
        verifyLoader(root.resolve(
                "fabric/src/main/java/com/chedidandrew/emeraldstandard/fabric/"
                        + "EmeraldStandardFabric.java"));
        Path neoForge = root.resolve(
                "neoforge/src/main/java/com/chedidandrew/emeraldstandard/neoforge/"
                        + "EmeraldStandardNeoForge.java");
        verifyLoader(neoForge);
        verifyNeoForgeDeathOrdering(neoForge);
        verifyConversionWiring(root, neoForge);
        verifyInteractionAuthority(root, neoForge);
        System.out.println("PASS BankerLifecycleWiringRegressionTest");
    }

    private static void verifyLoader(Path source) throws Exception {
        String java = Files.readString(source);
        int zombieBranch = java.indexOf(ZOMBIE_BRANCH);
        require(zombieBranch >= 0,
                source.getFileName() + " does not expose the ZombieVillager death branch");
        int bankDeath = java.indexOf(BANK_DEATH_CALL);
        require(bankDeath > zombieBranch,
                source.getFileName() + " does not route Zombie Banker death to Bank lifecycle state");
        require(java.indexOf(BANK_DEATH_CALL, bankDeath + BANK_DEATH_CALL.length()) < 0,
                source.getFileName() + " routes Zombie Banker death more than once");
        int villageDeath = java.indexOf(VILLAGE_DEATH_CALL, bankDeath + BANK_DEATH_CALL.length());
        require(villageDeath > bankDeath,
                source.getFileName() + " does not record Bank death before village casualty state");
    }

    private static void verifyNeoForgeDeathOrdering(Path source) throws Exception {
        String java = Files.readString(source);
        int method = java.indexOf("public void onLivingDeath(LivingDeathEvent event)");
        require(method >= 0, "NeoForge does not expose the living-death handler");
        int annotation = java.lastIndexOf("@SubscribeEvent", method);
        require(annotation >= 0,
                "NeoForge living-death handler has no event subscription annotation");
        String subscription = java.substring(annotation, method);
        require(subscription.contains("priority = EventPriority.LOWEST"),
                "NeoForge must observe cancelable deaths at the latest supported priority");
        require(subscription.contains("receiveCanceled = false"),
                "NeoForge must not receive deaths that an earlier listener canceled");

        int canceledGuard = java.indexOf("if (event.isCanceled())", method);
        int entityDispatch = java.indexOf(
                "if (event.getEntity() instanceof Villager villager)", method);
        require(canceledGuard > method && canceledGuard < entityDispatch,
                "NeoForge must reject a canceled death before recording lifecycle state");
    }

    private static void verifyConversionWiring(Path root, Path neoForge) throws Exception {
        Path fabric = root.resolve(
                "fabric/src/main/java/com/chedidandrew/emeraldstandard/fabric/"
                        + "EmeraldStandardFabric.java");
        String fabricJava = Files.readString(fabric);
        require(fabricJava.contains("ServerLivingEntityEvents.MOB_CONVERSION.register("),
                "Fabric does not subscribe to mob conversions");
        require(fabricJava.contains("ServerEntityEvents.ENTITY_LOAD.register("),
                "Fabric does not resume saved conversion lineage when an entity loads");
        require(fabricJava.contains(
                        "VillageBankManager.onBankerConversion(original, converted, ECONOMY)"),
                "Fabric does not route old and converted UUIDs to the Bank lifecycle");
        require(fabricJava.contains(
                        "VillageBankManager.onConvertedBankerDeath(entity, ECONOMY);"),
                "Fabric does not route terminal converted-form deaths");
        require(fabricJava.contains(
                        "VillageBankManager.flushPendingLifecycleForShutdown(server, ECONOMY)"),
                "Fabric discards final-tick Banker lifecycle work during shutdown");

        String neoForgeJava = Files.readString(neoForge);
        require(neoForgeJava.contains(
                        "public void onLivingConversion(LivingConversionEvent.Post event)"),
                "NeoForge does not subscribe to post-conversion events");
        require(neoForgeJava.contains(
                        "public void onEntityJoinLevel(EntityJoinLevelEvent event)"),
                "NeoForge does not resume saved conversion lineage when an entity loads");
        require(neoForgeJava.contains(
                        "event.getEntity(), event.getOutcome(), ECONOMY"),
                "NeoForge does not route source and outcome UUIDs to the Bank lifecycle");
        require(neoForgeJava.contains(
                        "VillageBankManager.onConvertedBankerDeath(event.getEntity(), ECONOMY);"),
                "NeoForge does not route terminal converted-form deaths");
        require(neoForgeJava.contains(
                        "VillageBankManager.flushPendingLifecycleForShutdown(server, ECONOMY)"),
                "NeoForge discards final-tick Banker lifecycle work during shutdown");

        Path manager = root.resolve(
                "common/src/minecraft/java/com/chedidandrew/emeraldstandard/minecraft/"
                        + "VillageBankManager.java");
        String managerJava = Files.readString(manager);
        require(managerJava.contains("prepareGeneratedBankerConversion("),
                "Conversion does not durably prepare its UUID transaction before world changes");
        require(managerJava.contains("pendingGeneratedBankerConversionByTarget("),
                "Restart recovery does not recognize the exact durable target UUID");
        require(managerJava.contains("pendingGeneratedBankerConversionBySource("),
                "A durable immediate source cannot rebase a repeated conversion");
        require(managerJava.contains(
                        "queueNonLiveBankerLifecycleParticipant(entity, economy)"),
                "Startup and entity-load recovery do not restage dead canonical participants");
        require(managerJava.contains(
                        "entity, preparedSource.regionKey(), preparedSource.rootCanonicalId()"),
                "A dead durable immediate source can reload without a removal-save barrier");
        require(managerJava.contains(
                        "CALLBACK_BANKER_CONVERSION_OUTCOMES.remove("
                                + "superseded.convertedId())"),
                "A chained conversion retains stale callback proof for its intermediate target");
        require(managerJava.contains("bankerConversionPredecessor(bankerEntity)"),
                "Death before restart discovery cannot recover exact predecessor lineage");
        require(managerJava.contains("pendingGeneratedBankerConversionByTarget(\n"
                        + "                        entity.getUUID()) != null"),
                "A converted target death requires a marker instead of durable UUID authority");
        require(managerJava.contains("STARTUP_BANKER_CONVERSION_SNAPSHOT"),
                "Restart recovery does not use a stable entity snapshot");
        require(managerJava.contains(
                        "startupBankerConversionCursor + BANKER_CONVERSION_DISCOVERIES_PER_SCAN"),
                "Startup lineage snapshot processing does not bound work per tick");
        require(managerJava.contains("if (!initialBankerConversionScanComplete)"),
                "Bank generation can run before startup lineage recovery finishes");
        require(managerJava.contains("DISCOVERED_AMBIGUOUS_BANKER_CONVERSIONS"),
                "Restart discovery does not quarantine duplicate exact successors");
        require(managerJava.contains(
                        "if (AMBIGUOUS_BANKER_CONVERSION_REGIONS.contains(regionKey))"),
                "An ambiguous conversion can still commit a canonical UUID transfer");
        require(managerJava.contains(
                        "if (!callbackOutcome) {\n"
                                + "                // PREPARED only says the new UUID was allocated."),
                "A restart sighting can promote PREPARED state without source-removal proof");
        require(managerJava.contains("BankerAccess.isBankerConversionSource("),
                "UUID handoff does not verify its exact immediate-source marker");
        require(managerJava.contains("flushAllBankEntitiesAndChunks(server)"),
                "Banker UUID commits do not save every active level before durable resolution");
        require(managerJava.contains("level.save(null, true, false);"),
                "The entity-inclusive save barrier does not use ServerLevel's flush path");
        require(managerJava.contains("markGeneratedBankerConversionEntityDurable("),
                "Conversion does not record its target entity-save barrier durably");
        require(managerJava.contains("commitGeneratedBankerConversion("),
                "Conversion does not use the atomic economy identity commit");
        require(managerJava.contains("revalidateBankerConversionAfterEntitySave("),
                "Entity-save reentrancy can commit a newly contradicted conversion decision");
        require(managerJava.contains("if (prepared.continuingBanker() && !targetLive)"),
                "A delayed target commit can canonicalize a successor that died after its save");
        require(managerJava.contains("if (!sourceLive)"),
                "A delayed source commit can canonicalize a predecessor that is no longer live");
        require(managerJava.contains("if (!rootLive)"),
                "A delayed root rollback can retain a canonical entity that is no longer live");
        require(managerJava.contains("match != null && match != entity"),
                "Duplicate exact UUIDs across dimensions are not quarantined");
        require(managerJava.contains(
                        "level.getServer(), canonicalBankerId, regionKey"),
                "Bank replacement checks the canonical UUID only in the Overworld");
        require(managerJava.contains("canonicalEntity.level() != level"),
                "A live canonical Banker in another dimension can authorize a replacement");
        require(managerJava.contains("queueBankerDeathSaveBarrier(previous, regionKey"),
                "A failed durable prepare can strand a source removed by vanilla conversion");
        require(managerJava.contains("if (!observed.isRemoved())"),
                "A revivable zero-health Banker can authorize replacement before removal");
        require(managerJava.contains(
                        "canonicalEntity != null && !canonicalEntity.isRemoved()"),
                "A loaded canonical death animation can be outranked by a stale tombstone");
        require(managerJava.contains(
                        "BankerAccess.neutralizeFailedBankerConversion(entity)"),
                "A stale conversion can retain an unscoped interactive Banker profession");
        require(managerJava.contains("isPendingBankerDeathAnimation(root)")
                        && managerJava.contains("isPendingBankerDeathAnimation(source)")
                        && managerJava.contains("isPendingBankerDeathAnimation(converted)")
                        && managerJava.contains("isPendingBankerDeathAnimation(target)"),
                "Conversion resolution treats a revivable participant corpse as absent");
        require(countOccurrences(
                        managerJava,
                        "BankerAccess.neutralizeFailedBankerConversion(converted)") >= 4,
                "A rollback loser can retain an unscoped interactive Banker profession");
        require(managerJava.contains(
                        "canonicalRegion = economy.generatedBankerRegion(entity.getUUID())"),
                "A zero-health entity saved during shutdown cannot restage on reload");
        require(managerJava.contains("PENDING_BANKER_CONVERSIONS.size() == 0"),
                "Shutdown reports lifecycle drain success with a conversion still unresolved");
        require(managerJava.contains("economy.hasPendingGeneratedBankerConversion(regionKey)"),
                "Bank replacement is not gated by durable conversion state");
    }

    private static void verifyInteractionAuthority(Path root, Path neoForge) throws Exception {
        Path fabric = root.resolve(
                "fabric/src/main/java/com/chedidandrew/emeraldstandard/fabric/"
                        + "EmeraldStandardFabric.java");
        String fabricJava = Files.readString(fabric);
        require(fabricJava.contains(
                        "return opened ? InteractionResult.SUCCESS_SERVER : InteractionResult.FAIL;"),
                "Fabric consumes a failed server-authoritative Banker menu open as success");

        String neoForgeJava = Files.readString(neoForge);
        require(neoForgeJava.contains(
                        "opened ? InteractionResult.SUCCESS_SERVER : InteractionResult.FAIL"),
                "NeoForge consumes a failed server-authoritative Banker menu open as success");

        Path access = root.resolve(
                "common/src/minecraft/java/com/chedidandrew/emeraldstandard/minecraft/"
                        + "BankerAccess.java");
        String accessJava = Files.readString(access);
        require(accessJava.contains(
                        "bankerLevel != bankerLevel.getServer().overworld()"),
                "A scoped generated Banker remains interactive outside the Overworld");
        require(accessJava.contains("boolean bankerAdded = villager.addTag(BANKER_TAG)"),
                "Managed Banker assignment ignores the vanilla scoreboard-tag cap");
        require(accessJava.contains("previousScopeTags"),
                "A partial managed-identity assignment cannot restore its previous scope");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static int countOccurrences(String source, String needle) {
        int count = 0;
        for (int index = 0; (index = source.indexOf(needle, index)) >= 0;
                index += needle.length()) {
            count++;
        }
        return count;
    }
}
