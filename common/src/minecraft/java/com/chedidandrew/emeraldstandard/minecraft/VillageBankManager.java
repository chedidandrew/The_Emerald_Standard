package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.EconomyService;
import com.chedidandrew.emeraldstandard.core.BankConstruction;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.registries.Registries;
import com.chedidandrew.emeraldstandard.core.EconomyState;
import com.chedidandrew.emeraldstandard.core.VillageArchitecture;
import com.chedidandrew.emeraldstandard.core.WholeBuildingBlueprint.Voxel;
import com.chedidandrew.emeraldstandard.core.WholeBuildingLightingValidator;
import com.chedidandrew.emeraldstandard.core.WholeBuildingLightingValidator.Bounds;
import com.chedidandrew.emeraldstandard.core.WholeBuildingLightingValidator.LightEmitter;
import com.chedidandrew.emeraldstandard.core.WholeBuildingLightingValidator.LightingSnapshot;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.PoiTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

/**
 * Discovers loaded villages and adds one detailed bank with a persistent Banker nearby.
 *
 * <p>Generation happens when a player first loads an Overworld village region, so it works in
 * existing worlds without replacing vanilla village pools or requiring a new world.</p>
 */
public final class VillageBankManager {
    private static final Map<Long, ParsedBankConstruction> CONSTRUCTION_CACHE = new HashMap<>();
    private static final Map<Long, Long> CONSTRUCTION_RETRY = new HashMap<>();
    private record ParsedBankConstruction(BankConstruction plan, List<BankPlacement> before,
            List<BankPlacement> after, List<Integer> order, Map<BlockPos,List<SupportedConstructionOrder.Cell>> supports,
            Set<Integer> disconnected) { }
    private static final int BANK_WIDTH = 13;
    private static final int BANK_DEPTH = 11;
    private static final int BANK_HEIGHT = 11;
    private static final int BANK_PLOT_MIN_X = -1;
    private static final int BANK_PLOT_MAX_X = BANK_WIDTH;
    private static final int BANK_PLOT_MIN_Z = -4;
    private static final int BANK_PLOT_MAX_Z = BANK_DEPTH;
    private static final int BANK_APPROACH_ARRIVAL_Z = -5;
    private static final int BANK_ENTRANCE_HALF_WIDTH = 1;
    private static final int LEGACY_BANK_STRUCTURE_VERSION = 2;
    private static final int PREVIOUS_BANK_STRUCTURE_VERSION = 3;
    private static final int PREVIOUS_BANK_STRUCTURE_VERSION_V4 = 4;
    private static final int PREVIOUS_BANK_STRUCTURE_VERSION_V5 = 5;
    private static final int PREVIOUS_BANK_STRUCTURE_VERSION_V6 = 6;
    private static final int PREVIOUS_BANK_STRUCTURE_VERSION_V7 = 7;
    private static final int PREVIOUS_BANK_STRUCTURE_VERSION_V8 = 8;
    private static final int PREVIOUS_BANK_STRUCTURE_VERSION_V9 = 9;
    private static final int PREVIOUS_BANK_STRUCTURE_VERSION_V10 = 10;
    private static final int BANK_STRUCTURE_VERSION = 11;
    private static final long FALLBACK_BANK_RETRY_INTERVAL_TICKS = 2_400L;
    private static final long BANK_UPGRADE_RETRY_INTERVAL_TICKS = 2_400L;
    private static final int FALLBACK_BANK_RECOVERY_RADIUS = 192;
    private static final int MAX_FALLBACK_BANK_RECOVERIES_PER_SCAN = 4;
    private static final int BANKER_DEATH_RETRIES_PER_PASS = 4;
    private static final int BANKER_CONVERSION_RETRIES_PER_TICK = 1;
    private static final int BANKER_CONVERSION_DISCOVERIES_PER_SCAN = 16;
    private static final Map<Long, Long> LAST_FALLBACK_BANK_RETRY_TICK = new HashMap<>();
    private static final Map<Long, Long> LAST_BANK_UPGRADE_RETRY_TICK = new HashMap<>();
    private static final BankerDeathRetryQueue PENDING_BANKER_DEATHS =
            new BankerDeathRetryQueue(BANKER_DEATH_RETRIES_PER_PASS);
    private static final BankerConversionRetryQueue PENDING_BANKER_CONVERSIONS =
            new BankerConversionRetryQueue(BANKER_CONVERSION_RETRIES_PER_TICK);
    private static final Set<Long> AMBIGUOUS_BANKER_CONVERSION_REGIONS = new HashSet<>();
    private static final Set<UUID> CALLBACK_BANKER_CONVERSION_OUTCOMES = new HashSet<>();
    private static final Set<UUID> DURABLY_REMOVED_BANKER_CONVERSION_ENTITIES =
            new HashSet<>();
    private static final Map<UUID, BankerDeathSaveBarrier> PENDING_BANKER_DEATH_SAVE_BARRIERS =
            new LinkedHashMap<>();
    private static final Map<Long, List<BankerConversionRetryQueue.Handoff>>
            DISCOVERED_BANKER_CONVERSIONS = new HashMap<>();
    private static final Set<Long> DISCOVERED_AMBIGUOUS_BANKER_CONVERSIONS = new HashSet<>();
    private static final List<Entity> STARTUP_BANKER_CONVERSION_SNAPSHOT = new ArrayList<>();
    private static int startupBankerConversionCursor;
    private static MinecraftServer bankerConversionServer;
    private static boolean bankerConversionRecoveryReady;
    private static boolean initialBankerConversionScanComplete;

    private VillageBankManager() {
    }

    /** Clears world-session-only retry pacing between integrated or dedicated server instances. */
    public static void resetRuntimeState() {
        CONSTRUCTION_CACHE.clear();
        CONSTRUCTION_RETRY.clear();
        LAST_FALLBACK_BANK_RETRY_TICK.clear();
        LAST_BANK_UPGRADE_RETRY_TICK.clear();
        PENDING_BANKER_DEATHS.clear();
        PENDING_BANKER_CONVERSIONS.clear();
        AMBIGUOUS_BANKER_CONVERSION_REGIONS.clear();
        CALLBACK_BANKER_CONVERSION_OUTCOMES.clear();
        DURABLY_REMOVED_BANKER_CONVERSION_ENTITIES.clear();
        PENDING_BANKER_DEATH_SAVE_BARRIERS.clear();
        DISCOVERED_BANKER_CONVERSIONS.clear();
        DISCOVERED_AMBIGUOUS_BANKER_CONVERSIONS.clear();
        STARTUP_BANKER_CONVERSION_SNAPSHOT.clear();
        startupBankerConversionCursor = 0;
        bankerConversionServer = null;
        bankerConversionRecoveryReady = false;
        initialBankerConversionScanComplete = false;
    }

    /** Captures one stable startup snapshot after economy state is available. */
    public static void beginServerSession(MinecraftServer server, EconomyService economy) {
        STARTUP_BANKER_CONVERSION_SNAPSHOT.clear();
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (BankerAccess.bankerConversionPredecessor(entity) != null) {
                    STARTUP_BANKER_CONVERSION_SNAPSHOT.add(entity);
                }
                // Entity-load events may run before economy startup. Restage already-dead exact
                // lifecycle participants here so their eventual removal cannot strand ownership.
                queueNonLiveBankerLifecycleParticipant(entity, economy);
            }
        }
        startupBankerConversionCursor = 0;
        bankerConversionServer = server;
        DISCOVERED_BANKER_CONVERSIONS.clear();
        DISCOVERED_AMBIGUOUS_BANKER_CONVERSIONS.clear();
        initialBankerConversionScanComplete = STARTUP_BANKER_CONVERSION_SNAPSHOT.isEmpty();
        bankerConversionRecoveryReady = true;
        // Format 17 is global authority even when the exact target chunk is unloaded. Seed the
        // bounded runtime queue directly from durable state; entity markers are corroboration.
        for (EconomyService.GeneratedBankerConversion conversion
                : economy.pendingGeneratedBankerConversionsSnapshot().values()) {
            PENDING_BANKER_CONVERSIONS.offer(
                    conversion.regionKey(),
                    conversion.rootCanonicalId(),
                    conversion.immediateSourceId(),
                    conversion.targetId(),
                    conversion.continuingBanker());
        }
    }

    /**
     * Resolves save-ordered lifecycle work that arrived after the final ordinary server tick.
     * Unresolved durable PREPARED conversions remain fail-closed in format 17; transient death
     * observations are never discarded until their entity removal has reached Minecraft storage.
     */
    public static boolean flushPendingLifecycleForShutdown(
            MinecraftServer server, EconomyService economy) {
        boolean complete = true;
        boolean hasNonLiveEntity = false;
        for (BankerDeathSaveBarrier barrier
                : List.copyOf(PENDING_BANKER_DEATH_SAVE_BARRIERS.values())) {
            Entity observed = barrier.entity();
            if (observed.isAlive() && !observed.isRemoved()) {
                PENDING_BANKER_DEATH_SAVE_BARRIERS.remove(
                        barrier.observedEntityId(), barrier);
            } else if (barrier.level().getServer() == server) {
                hasNonLiveEntity = true;
            }
        }
        if (hasNonLiveEntity) {
            if (!flushAllBankEntitiesAndChunks(server)) {
                complete = false;
            } else {
                for (BankerDeathSaveBarrier barrier
                        : List.copyOf(PENDING_BANKER_DEATH_SAVE_BARRIERS.values())) {
                    Entity observed = barrier.entity();
                    if (barrier.level().getServer() != server) {
                        continue;
                    }
                    if (!observed.isRemoved()) {
                        // The full shutdown barrier persisted the zero-health entity, but it is
                        // still revivable and cannot authorize replacement. Its next entity-load
                        // callback restages the observation if vanilla has not removed it yet.
                        PENDING_BANKER_DEATH_SAVE_BARRIERS.remove(
                                barrier.observedEntityId(), barrier);
                        continue;
                    }
                    if (PENDING_BANKER_DEATH_SAVE_BARRIERS.remove(
                            barrier.observedEntityId(), barrier)
                            && !finishDurablySavedBankerRemoval(barrier, economy)) {
                        complete = false;
                    }
                }
            }
        }

        int conversionAttempts = PENDING_BANKER_CONVERSIONS.size();
        for (int attempt = 0; attempt < conversionAttempts; attempt++) {
            retryPendingBankerConversions(server, economy);
        }
        int deathAttempts = PENDING_BANKER_DEATHS.size();
        for (int attempt = 0; attempt < deathAttempts; attempt++) {
            retryPendingBankerDeaths(economy);
        }
        return complete
                && PENDING_BANKER_DEATH_SAVE_BARRIERS.isEmpty()
                && PENDING_BANKER_CONVERSIONS.size() == 0
                && PENDING_BANKER_DEATHS.size() == 0;
    }

    /** Receives entities loaded after the stable startup snapshot was taken. */
    public static void onEntityLoaded(Entity entity, EconomyService economy) {
        if (!bankerConversionRecoveryReady) {
            // Initial level loads are covered by beginServerSession after economy startup. Never
            // strip a lineage marker while the canonical economy state is unavailable.
            return;
        }
        queueNonLiveBankerLifecycleParticipant(entity, economy);
        EconomyService.GeneratedBankerConversion prepared =
                economy.pendingGeneratedBankerConversionByTarget(entity.getUUID());
        if (prepared != null) {
            BankerAccess.markBankerConversion(
                    entity,
                    prepared.regionKey(),
                    prepared.rootCanonicalId(),
                    prepared.immediateSourceId());
            queuePreparedBankerConversion(prepared, entity);
        } else if (!entity.isAlive()
                && (entity instanceof Villager || entity instanceof ZombieVillager)) {
            Long canonicalRegion = economy.generatedBankerRegion(entity.getUUID());
            if (canonicalRegion != null) {
                queueBankerDeathSaveBarrier(entity, canonicalRegion, entity.getUUID());
            }
        }
        EconomyService.GeneratedBankerConversion preparedSource =
                economy.pendingGeneratedBankerConversionBySource(entity.getUUID());
        if (preparedSource != null
                && !preparedSource.rootCanonicalId().equals(entity.getUUID())) {
            queuePreparedBankerConversion(preparedSource, entity);
            return;
        }
        if (!initialBankerConversionScanComplete) {
            if (BankerAccess.bankerConversionPredecessor(entity) != null) {
                STARTUP_BANKER_CONVERSION_SNAPSHOT.add(entity);
            }
            return;
        }
        queueLoadedBankerConversion(entity, economy);
    }

    private static void queueNonLiveBankerLifecycleParticipant(
            Entity entity, EconomyService economy) {
        if (entity.isAlive() || entity.isRemoved()) {
            return;
        }
        EconomyService.GeneratedBankerConversion preparedTarget =
                economy.pendingGeneratedBankerConversionByTarget(entity.getUUID());
        if (preparedTarget != null) {
            queueBankerDeathSaveBarrier(
                    entity, preparedTarget.regionKey(), preparedTarget.rootCanonicalId());
            return;
        }
        EconomyService.GeneratedBankerConversion preparedSource =
                economy.pendingGeneratedBankerConversionBySource(entity.getUUID());
        if (preparedSource != null
                && !preparedSource.rootCanonicalId().equals(entity.getUUID())) {
            queueBankerDeathSaveBarrier(
                    entity, preparedSource.regionKey(), preparedSource.rootCanonicalId());
            return;
        }
        Long canonicalRegion = economy.generatedBankerRegion(entity.getUUID());
        if (canonicalRegion != null) {
            queueBankerDeathSaveBarrier(entity, canonicalRegion, entity.getUUID());
        }
    }

    private static void queuePreparedBankerConversion(
            EconomyService.GeneratedBankerConversion prepared, Entity entity) {
        BankerConversionRetryQueue.Handoff superseded =
                PENDING_BANKER_CONVERSIONS.removeRegion(prepared.regionKey());
        if (superseded != null
                && !superseded.convertedId().equals(prepared.targetId())) {
            CALLBACK_BANKER_CONVERSION_OUTCOMES.remove(superseded.convertedId());
        }
        if (!PENDING_BANKER_CONVERSIONS.offer(
                prepared.regionKey(),
                prepared.rootCanonicalId(),
                prepared.immediateSourceId(),
                prepared.targetId(),
                prepared.continuingBanker())) {
            AMBIGUOUS_BANKER_CONVERSION_REGIONS.add(prepared.regionKey());
        }
    }

    public static void tick(MinecraftServer server, EconomyService economy) {
        EmeraldConfig config = EmeraldConfig.current();
        ServerLevel level = server.overworld();
        ConstructionTimeRuntime.tick(server, config);
        long gameTime = level.getGameTime();
        retryPendingBankerDeathSaveBarriers(server, economy);
        // Conversion callbacks run immediately before Minecraft inserts the new entity. Restart
        // discovery examines a bounded slice each tick and completes a whole loaded-entity pass
        // before adopting any discovered successor, so two saved candidates cannot race.
        processStartupBankerConversions(server, economy);
        if (!initialBankerConversionScanComplete) {
            // A durable infection-time tombstone must not authorize replacement before every
            // entity in the stable startup snapshot has had a chance to reveal exact lineage.
            return;
        }
        retryPendingBankerConversions(server, economy);
        boolean forcedDevelopment = config.forcedVillageDevelopment();
        if (config.villageBanksEnabled() && !economy.isCatchingUp()
                && (forcedDevelopment ? gameTime % 2 == 0 : config.constructionAllowance(gameTime) > 0)) {
            var pendingSites = new ArrayList<>(economy.pendingBankConstructionsSnapshot().entrySet());
            pendingSites.sort(java.util.Map.Entry.comparingByKey());
            if (forcedDevelopment && !pendingSites.isEmpty())
                pendingSites = new ArrayList<>(List.of(pendingSites.get(Math.floorMod(gameTime / 2,pendingSites.size()))));
            for (var entry : pendingSites) {
                if (CONSTRUCTION_RETRY.getOrDefault(entry.getKey(), 0L) > gameTime) continue;
                BlockPos origin = BlockPos.of(entry.getValue().origin());
                if (level.players().stream().anyMatch(p -> {
                    double dx = p.getX() - origin.getX(), dz = p.getZ() - origin.getZ();
                    return dx * dx + dz * dz <= (double) config.villageDevelopmentRadius() * config.villageDevelopmentRadius();
                })) {
                    int changed = 0;
                    int allowance = forcedDevelopment ? ForcedDevelopmentRuntime.claim(server)
                            : ConstructionTimeRuntime.allowance("bank:" + entry.getKey() + ":" + entry.getValue().origin(),
                                    gameTime, config);
                    if (allowance == 0) continue;
                    for (; allowance > 0; allowance--) {
                        if (forcedDevelopment && changed > 0 && !ForcedDevelopmentRuntime.hasTime()) break;
                        if (!forcedDevelopment && changed >= config.constructionAllowance(gameTime)
                                && !ConstructionTimeRuntime.hasTime()) break;
                        int step = advanceBankConstruction(level, economy, entry.getKey(), entry.getValue());
                        changed += step;
                        if (step == 0) break;
                    }
                    if (changed == 0 && economy.pendingBankConstructionsSnapshot().containsKey(entry.getKey()))
                        CONSTRUCTION_RETRY.put(entry.getKey(), gameTime
                                + (ConstructionDiagnostics.waitingForEntities("bank:" + entry.getKey())
                                || ConstructionDiagnostics.preparingFence("bank:" + entry.getKey()) ? 10L : 200L));
                    else CONSTRUCTION_RETRY.remove(entry.getKey());
                }
            }
        }
        if (gameTime % config.villageScanIntervalTicks() != 0L) {
            return;
        }
        // A canonical death is an already-observed lifecycle fact, so finish persisting it even
        // if Bank generation was disabled after the event. Work and synchronous saves are bounded.
        retryPendingBankerDeaths(economy);
        reconcileBankBellVillages(level, economy, gameTime / config.villageScanIntervalTicks());
        if (!config.villageBanksEnabled()) {
            return;
        }

        Set<Long> processedBanks = new HashSet<>();
        VillageBankPlacementPolicy.UpgradeAttemptGate bankUpgradeGate =
                new VillageBankPlacementPolicy.UpgradeAttemptGate();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.level() != level) {
                continue;
            }
            BlockPos playerPosition = player.blockPosition();
            // POI village membership is height-sensitive. Project the discovery probe to the
            // terrain so a player flying over (or testing above) a village still discovers it,
            // and never persist an airborne fallback anchor.
            BlockPos villageProbe = surfaceVillageProbe(level, playerPosition);
            boolean insideVanillaVillage = level.isVillage(playerPosition)
                    || level.isVillage(villageProbe);
            var natural = NaturalVillageIdentity.near(level, villageProbe);
            UUID territoryOwner = economy.territoryVillageId("minecraft:overworld", villageProbe.asLong());
            EconomyService.VillageSnapshot village = natural != null ? economy.villageSnapshot(natural.id())
                    : territoryOwner != null ? economy.villageSnapshot(territoryOwner)
                    : economy.nearestVillageSnapshot("minecraft:overworld", villageProbe.asLong(),
                            insideVanillaVillage ? 72.0 : FALLBACK_BANK_RECOVERY_RADIUS);
            if (natural != null && village == null) continue; // Await natural-structure census, not a neighboring bank.
            if (!VillageBankPlacementPolicy.shouldProbeVillage(
                    insideVanillaVillage, village != null)) {
                continue;
            }
            if (village == null
                    && (config.villageProsperitySimulationEnabled()
                            || config.villageVisualProgressionEnabled())) {
                // Prosperity discovery owns the stable settlement center and identity. Its
                // default census is slower than the bank scan, so wait instead of permanently
                // keying a new bank from whichever player's position happened to be seen first.
                continue;
            }
            UUID villageId = village == null ? null : village.village().villageId;
            // The player's location is only a discovery probe. Once the prosperity system has a
            // stable settlement identity, its persisted center owns keying and site selection.
            BlockPos villagePosition = village == null
                    ? villageProbe
                    : BlockPos.of(village.village().centerPos);
            long bankKey = bankKeyForVillage(
                    economy,
                    "minecraft:overworld",
                    villagePosition,
                    villageId,
                    config.villageRegionSize());
            if (!processedBanks.add(bankKey)) {
                continue;
            }
            if (economy.pendingBankConstructionsSnapshot().containsKey(bankKey)) continue;
            if (economy.hasGeneratedBankRegion(bankKey)) {
                Long packedAnchor = economy.generatedBankAnchor(bankKey);
                BlockPos anchor = packedAnchor == null
                        ? villagePosition
                        : BlockPos.of(packedAnchor);
                if (packedAnchor == null) {
                    economy.markGeneratedBankRegion(bankKey, anchor.asLong());
                }
                if (villageId != null) {
                    economy.associateBankRegionWithVillage(bankKey, villageId, anchor.asLong());
                }
                List<Long> retiredAnchors = economy.retiredBankAnchors(bankKey);
                boolean persistedFallback = economy.isFallbackBankRegion(bankKey);
                if (persistedFallback) {
                    // All Banker-only recovery runs through the bounded outskirts pass below. It
                    // can inspect loaded outer lots even after this old center chunk unloads, and
                    // it shares one scan-level work cap across every nearby player.
                    continue;
                }
                boolean currentAnchorRetired = packedAnchor != null
                        && retiredAnchors.contains(packedAnchor);
                if (currentAnchorRetired) {
                    // Retirement is durable. A later retry follows the live village and must not
                    // depend on keeping the abandoned footprint loaded or structurally intact.
                    BankBuildAttempt replacement = attemptBankBuild(
                            level,
                            economy,
                            villagePosition,
                            villageId,
                            bankKey,
                            retiredAnchors,
                            true);
                    if (replacement.build().built()) {
                        persistBuiltBank(level, economy, villageId, bankKey, replacement);
                    }
                    continue;
                }
                if (packedAnchor != null
                        && (!isLoaded(level, anchor) || !isLoaded(level, anchor.north()))) {
                    continue;
                }
                int structureVersion = economy.generatedBankStructureVersion(bankKey);
                if (packedAnchor != null
                        && structureVersion >= LEGACY_BANK_STRUCTURE_VERSION) {
                    BankIntegrity integrity = inspectManagedBankIntegrity(
                            level, anchor, structureVersion);
                    if (integrity.complete()
                            && integrity.decision()
                                    == VillageMaterializationPolicy.IntegrityDecision.RELOCATE) {
                        if (economy.retireGeneratedBankAnchor(bankKey, packedAnchor)) {
                            BankBuildAttempt replacement = attemptBankBuild(
                                    level,
                                    economy,
                                    villagePosition,
                                    villageId,
                                    bankKey,
                                    economy.retiredBankAnchors(bankKey),
                                    true);
                            if (replacement.build().built()) {
                                persistBuiltBank(
                                        level, economy, villageId, bankKey, replacement);
                            }
                        }
                        continue;
                    }
                    if (integrity.complete()
                            && integrity.decision()
                                    == VillageMaterializationPolicy.IntegrityDecision.UNSAFE) {
                        // Player customization is authoritative. Do not repair it, do not create a
                        // duplicate, and do not let the damaged building act as a working Bank.
                        continue;
                    }
                }
                boolean intactCounter = packedAnchor != null
                        && BankerProfessionSupport.isBankWorkstation(
                                level.getBlockState(anchor.north()));
                boolean survivingCounterFrame = packedAnchor != null
                        && hasBankCounterFrame(level, anchor);
                if (intactCounter && survivingCounterFrame) {
                    maintainManagedBank(
                            level,
                            economy,
                            anchor,
                            villageId,
                            bankKey,
                            bankUpgradeGate);
                }
                boolean bankSignaturePresent = intactCounter || survivingCounterFrame;
                // Old saves do not retain per-block bank ownership. Without explicit fallback
                // provenance, a missing signature is never rebuilt: it could be a damaged or
                // crash-interrupted Bank rather than a Banker-only fallback.
                ensureBanker(
                        level,
                        bankSignaturePresent ? anchor : villagePosition,
                        bankSignaturePresent,
                        bankKey,
                        economy);
                continue;
            }

            BankBuildAttempt attempt = attemptBankBuild(
                    level,
                    economy,
                    villagePosition,
                    villageId,
                    bankKey,
                    economy.retiredBankAnchors(bankKey),
                    false);
            if (attempt.build().built()) {
                persistBuiltBank(level, economy, villageId, bankKey, attempt);
            } else {
                if (VillageBankPlacementPolicy.shouldPersistFallback(
                        attempt.searchComplete(), attempt.hadCandidates())) {
                    establishFallbackBanker(
                            level, villagePosition, bankKey, villageId, economy);
                } else {
                    // A proven candidate that failed its guarded write remains transient. Do not
                    // create an untracked Banker: the next normal scan retries from clean state.
                }
            }
        }
        retryNearbyFallbackBanks(level, economy, gameTime);
    }

    /**
     * Keeps an explicitly recorded Banker-only village moving toward a real Bank while players
     * explore its outskirts. This path does not require the player's feet to remain inside the
     * vanilla POI boundary, does not force-load chunks, and never applies to ambiguous old markers.
     */
    private static void retryNearbyFallbackBanks(
            ServerLevel level,
            EconomyService economy,
            long gameTime) {
        List<Map.Entry<Long, Long>> nearbyFallbacks = economy.generatedBankAnchorsSnapshot()
                .entrySet()
                .stream()
                .filter(entry -> VillageBankPlacementPolicy.shouldRetryPersistedFallback(
                        economy.isFallbackBankRegion(entry.getKey())))
                .filter(entry -> hasNearbyRecoveryPlayer(level, BlockPos.of(entry.getValue())))
                .sorted(Comparator.comparingLong(entry ->
                        nearestRecoveryPlayerDistanceSquared(
                                level, BlockPos.of(entry.getValue()))))
                .toList();
        int attempts = 0;
        for (Map.Entry<Long, Long> entry : nearbyFallbacks) {
            long bankKey = entry.getKey();
            Long previousRetry = LAST_FALLBACK_BANK_RETRY_TICK.get(bankKey);
            if (!VillageBankPlacementPolicy.retryDue(
                    gameTime, previousRetry, FALLBACK_BANK_RETRY_INTERVAL_TICKS)) {
                continue;
            }
            if (attempts++ >= MAX_FALLBACK_BANK_RECOVERIES_PER_SCAN) {
                break;
            }

            LAST_FALLBACK_BANK_RETRY_TICK.put(bankKey, gameTime);
            BlockPos villageAnchor = BlockPos.of(entry.getValue());
            UUID villageId = economy.villageIdForBankRegion(bankKey);
            BankBuildAttempt retry = attemptBankBuild(
                    level,
                    economy,
                    villageAnchor,
                    villageId,
                    bankKey,
                    economy.retiredBankAnchors(bankKey),
                    true);
            if (retry.build().built()
                    && persistBuiltBank(level, economy, villageId, bankKey, retry)) {
                continue;
            }
            if (isLoaded(level, villageAnchor)) {
                ensureBanker(level, villageAnchor, false, bankKey, economy);
            }
        }
    }

    private static boolean hasNearbyRecoveryPlayer(ServerLevel level, BlockPos anchor) {
        return nearestRecoveryPlayerDistanceSquared(level, anchor)
                <= (long) FALLBACK_BANK_RECOVERY_RADIUS * FALLBACK_BANK_RECOVERY_RADIUS;
    }

    private static long nearestRecoveryPlayerDistanceSquared(
            ServerLevel level, BlockPos anchor) {
        long nearest = Long.MAX_VALUE;
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            if (player.level() != level) {
                continue;
            }
            BlockPos position = player.blockPosition();
            if (!VillageBankPlacementPolicy.recoveryActive(
                    anchor.getX(),
                    anchor.getZ(),
                    position.getX(),
                    position.getZ(),
                    FALLBACK_BANK_RECOVERY_RADIUS)) {
                continue;
            }
            long dx = (long) position.getX() - anchor.getX();
            long dz = (long) position.getZ() - anchor.getZ();
            nearest = Math.min(nearest, dx * dx + dz * dz);
        }
        return nearest;
    }

    private static BankBuildAttempt attemptBankBuild(
            ServerLevel level,
            EconomyService economy,
            BlockPos villagePosition,
            UUID villageId,
            long bankKey,
            List<Long> excludedPackedAnchors,
            boolean recoverySearch) {
        if (economy.pendingBankConstructionsSnapshot().containsKey(bankKey))
            return new BankBuildAttempt(null, BankBuildResult.failed(), false, true);
        BankPlotSearch plotSearch = findBankPlots(
                level,
                economy,
                villagePosition,
                bankKey,
                excludedPackedAnchors,
                recoverySearch);
        for (BlockPos candidate : plotSearch.candidates()) {
            if (reserveProgressiveBank(level, economy, candidate, villageId, bankKey)) {
                return new BankBuildAttempt(null, BankBuildResult.failed(), false, true);
            }
        }
        return new BankBuildAttempt(
                null,
                BankBuildResult.failed(),
                plotSearch.complete(),
                !plotSearch.candidates().isEmpty());
    }

    static List<Long> pendingBankAnchors(EconomyService economy) {
        return economy.pendingBankConstructionsSnapshot().values().stream()
                .map(BankConstruction::bankerAnchor).toList();
    }

    static boolean reserveProgressiveBank(ServerLevel level, EconomyService economy,
            BlockPos origin, UUID villageId, long key) {
        ensureBankTemplateValidated();
        List<BankPlacement> authored = terrainSupportedBankPlan(level, origin, paletteFor(level, origin));
        if (authored == null) return false;
        var survey = new VillageSitePreparation.Survey(level);
        Set<BlockPos> volume = new HashSet<>();
        for (int x = BANK_PLOT_MIN_X; x <= BANK_PLOT_MAX_X; x++)
            for (int z = BANK_PLOT_MIN_Z; z <= BANK_PLOT_MAX_Z; z++)
                for (int y = 0; y <= BANK_HEIGHT; y++) volume.add(origin.offset(x, y, z));
        for (BankPlacement cell : authored) {
            if (!isLoaded(level, cell.position())) return false;
            if (!isNaturalBankGround(level.getBlockState(cell.position())) || cell.position().getY() >= origin.getY())
                volume.add(cell.position());
            if (cell.state().getBlock() instanceof StairBlock) {
                volume.add(cell.position().above()); volume.add(cell.position().above(2));
            }
        }
        var preparation = survey.freeze(volume, origin.getY(), villageId, key);
        if (DevelopmentLandProtection.excludes(level,origin.offset(BANK_PLOT_MIN_X,0,BANK_PLOT_MIN_Z),
                origin.offset(BANK_PLOT_MAX_X,0,BANK_PLOT_MAX_Z))) return false;
        if (preparation == null) return false;
        Set<BlockPos> occupied = authored.stream().map(BankPlacement::position)
                .collect(java.util.stream.Collectors.toSet());
        List<BlockPos> approach = new ArrayList<>();
        for (int z = BANK_PLOT_MIN_Z - 1; z >= BANK_PLOT_MIN_Z - 8; z--)
            approach.add(origin.offset(BANK_WIDTH / 2, 0, z));
        Integer arrival = survey.surface(approach.getFirst().getX(), approach.getFirst().getZ());
        preparation = VillageTerrainFinishing.finish(level, preparation, origin,
                BANK_PLOT_MIN_X, BANK_PLOT_MAX_X, BANK_PLOT_MIN_Z, BANK_PLOT_MAX_Z,
                occupied, approach, arrival == null ? origin.getY() : arrival,
                paletteFor(level, origin).foundation().defaultBlockState(),
                paletteFor(level, origin).stairs().defaultBlockState(), villageId, key);
        Map<BlockPos, BlockState> finalCells = new LinkedHashMap<>();
        for (var cell : preparation.cells()) finalCells.put(BlockPos.of(cell.position()),
                VillageTerrainFinishing.state(level, cell.after()));
        Map<Long, String> approvedTerrainStates = new HashMap<>();
        for (var cell : preparation.cells()) approvedTerrainStates.put(cell.position(), cell.after());
        for (BankPlacement cell : authored) finalCells.put(cell.position(), cell.state());
        List<BankConstruction.Cell> frozen = new ArrayList<>();
        for (var cell : finalCells.entrySet().stream()
                .sorted(Comparator.<Map.Entry<BlockPos, BlockState>>comparingInt(e -> e.getValue().isAir() ? 0 : 1)
                        .thenComparingInt(e -> e.getValue().isAir() ? -e.getKey().getY() : e.getKey().getY())).toList()) {
            BlockPos pos = cell.getKey();
            if (!isLoaded(level, pos)) return false;
            BlockState existing = level.getBlockState(pos);
            boolean approvedTerrain = BlockStateParser.serialize(cell.getValue()).equals(approvedTerrainStates.get(pos.asLong()));
            if ((!approvedTerrain && !existing.isAir() && !survey.clearable(pos) && !survey.excavatable(pos, origin.getY())) || existing.hasBlockEntity()
                    || !level.getFluidState(pos).isEmpty()
                    || !VillageDevelopmentProtection.mayPlace(level, villageId, key, pos, existing, cell.getValue()))
                return false;
            frozen.add(new BankConstruction.Cell(pos.asLong(), BlockStateParser.serialize(existing),
                    BlockStateParser.serialize(cell.getValue())));
        }
        return economy.reserveBankConstruction(key, new BankConstruction(origin.asLong(),
                origin.offset(BANK_WIDTH / 2, 1, BANK_DEPTH - 2).asLong(), villageId,
                BANK_STRUCTURE_VERSION, frozen));
    }

    /** One authored operation; callers supply the configured independent allowance per site. */
    static int advanceBankConstruction(ServerLevel level, EconomyService economy, long key, BankConstruction plan) {
        // Callers may retain a geometry snapshot; receipts must always come from live saved authority.
        BankConstruction authoritative = economy.pendingBankConstructionsSnapshot().get(key);
        if (authoritative == null || authoritative.origin() != plan.origin()
                || !authoritative.cells().equals(plan.cells())) return 0;
        plan = authoritative;
        BlockPos siteOrigin = BlockPos.of(plan.origin());
        if (DevelopmentLandProtection.excludes(level,siteOrigin.offset(BANK_PLOT_MIN_X,0,BANK_PLOT_MIN_Z),
                siteOrigin.offset(BANK_PLOT_MAX_X,0,BANK_PLOT_MAX_Z))) {
            ConstructionDiagnostics.record("bank:"+key,"protected",0,plan.cells().size(),level.getGameTime(),"No-build zone overlaps reserved Bank lot");
            return 0;
        }
        var village = plan.villageId() == null ? null : economy.developmentVillageSnapshot(plan.villageId());
        if (!economy.forcedVillageDevelopment() && village != null
                && !com.chedidandrew.emeraldstandard.core.VillageConstructionPolicy.villageEligible(village.village())) return 0;
        if (!economy.forcedVillageDevelopment() && EmeraldConfig.current().villageVisualProgressionEnabled()
                && !ConstructionSitePresentation.fenceReady(level, VillageConstructionActivity.bankTag(key, plan.origin()))) {
            ConstructionDiagnostics.record("bank:" + key, "preparing_fence", 0, plan.cells().size(),
                    level.getGameTime(), "Preparing safe perimeter before Bank construction");
            return 0;
        }
        ParsedBankConstruction parsed = CONSTRUCTION_CACHE.get(key);
        if (parsed == null || !parsed.plan().equals(plan)) {
            List<BankPlacement> before = new ArrayList<>(), after = new ArrayList<>();
            try {
                var blocks = level.registryAccess().lookupOrThrow(Registries.BLOCK);
                for (var cell : plan.cells()) {
                    BlockPos pos = BlockPos.of(cell.position());
                    before.add(new BankPlacement(pos, BlockStateParser.parseForBlock(blocks, cell.before(), false).blockState()));
                    after.add(new BankPlacement(pos, BlockStateParser.parseForBlock(blocks, cell.after(), false).blockState()));
                }
            } catch (com.mojang.brigadier.exceptions.CommandSyntaxException ex) { return 0; }
            var cells=after.stream().map(c->new SupportedConstructionOrder.Cell(c.position().subtract(siteOrigin),c.state(),-1)).toList();
            var sequence=SupportedConstructionOrder.sequence(cells,List.of(0,cells.size()));
            var supports=SupportedConstructionOrder.supportPalette(cells);
            parsed = new ParsedBankConstruction(plan, before, after, sequence.indices(),Map.copyOf(supports),sequence.disconnected());
            CONSTRUCTION_CACHE.put(key, parsed);
        }
        String ownershipJob=ConstructionOwnership.bank(key,plan.origin());
        var ownership=ConstructionOwnership.get(level);
        ownership.begin(economy,ownershipJob,plan.villageId(),key,plan.origin(),true);
        for(var cell:parsed.after()) ownership.reserve(ownershipJob,cell.position(),cell.state());
        boolean unfinished = false;
        boolean occupied = false;
        int matched = 0;
        var ordered=new ArrayList<>(parsed.order());
        var currentParsed=parsed;
        ordered.sort(java.util.Comparator.comparingInt(i -> {
            var c=currentParsed.after().get(i);
            return ConstructionOwnership.owned(level,c.position(),c.state()) ? 1 : 0;
        }));
        for (int i : ordered) {
            BankPlacement cell = parsed.after().get(i);
            if (!isLoaded(level, cell.position())) { unfinished = true; continue; }
            BlockState current = level.getBlockState(cell.position());
            boolean storage = plan.cells().get(i).storage();
            boolean handled = plan.handledStorage().contains(cell.position().asLong());
            if (isOwnedBankPlacement(current, cell.state())) {
                // Adopted containers keep their contents and permanently close this loot opportunity.
                if (storage && !handled && !economy.markBankStorageHandled(key,plan,cell.position().asLong())) {
                    unfinished = true; continue;
                }
                matched++; continue;
            }
            unfinished = true;
            // Preserve player removal: no repeat loot and no infinite free replacement chest blocks.
            // Issued legacy storage can be rebuilt empty; new loot waits for handover.
            // Never overwrite player edits or containers. A blocked cell remains pending.
            if ((!VillageSitePreparation.matchesRemoval(current, plan.cells().get(i).before())
                        && !current.isAir())
                    || current.hasBlockEntity()
                    || !level.getFluidState(cell.position()).isEmpty()
                    || !VillageDevelopmentProtection.mayPlace(level, plan.villageId(), key,
                            cell.position(), current, cell.state())) continue;
            if (!cell.state().canSurvive(level, cell.position())) continue;
            var supportCell=new SupportedConstructionOrder.Cell(cell.position().subtract(siteOrigin),cell.state(),-1);
            if(!parsed.disconnected().contains(i)
                    && !SupportedConstructionOrder.supportedNow(level,siteOrigin,supportCell,parsed.supports())) continue;
            if (!VillageConstructionOccupancy.mayChange(level,cell.position(),current,cell.state())) {
                occupied = true; continue;
            }
            ownership.claim(ownershipJob,cell.position(),cell.state(),storage&&!handled&&!plan.legacyLootSuppressed());
            // Defer neighbor-shape updates so a door/bed's second half can arrive next pulse.
            if (level.setBlock(cell.position(), cell.state(), cell.state().isAir() ? Block.UPDATE_ALL
                    : Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE)) {
                // No loot or usable inventory is exposed during construction, including repairs.
                if (storage) economy.markBankStorageHandled(key,plan,cell.position().asLong());
                ConstructionDiagnostics.record("bank:" + key, "building", matched + 1, parsed.after().size(),
                        level.getGameTime(), "automatic progressive construction");
                return 1;
            }
        }
        if (unfinished) {
            ConstructionDiagnostics.record("bank:" + key, occupied ? "waiting_for_entities" : "retry_in_place", matched, parsed.after().size(),
                    level.getGameTime(), occupied ? "Living entity occupies a placement or its supporting floor; retry shortly"
                            : "unloaded, protected, changed or not-yet-supported cells; preserving saved work");
            return 0;
        }
        BlockPos anchor = BlockPos.of(plan.bankerAnchor());
        if (!ownership.prepareHandover(level,ownershipJob,VillageStructureLoot.key("bank"))
                || !flushBankChunks(level) || !economy.markGeneratedBankRegion(key, anchor.asLong(),
                plan.villageId(), plan.version())) return 0;
        ownership.grantHandoverLoot(level,ownershipJob,VillageStructureLoot.key("bank"));
        ownership.finish(ownershipJob);
        for (BankPlacement cell : parsed.after()) {
            level.updateNeighborsAt(cell.position(), cell.state().getBlock(), null);
        }
        CONSTRUCTION_CACHE.remove(key);
        CONSTRUCTION_RETRY.remove(key);
        ConstructionDiagnostics.record("bank:" + key, "complete", parsed.after().size(), parsed.after().size(),
                level.getGameTime(), "Bank saved and commissioned");
        ensureBanker(level, anchor, true, key, economy);
        LAST_FALLBACK_BANK_RETRY_TICK.remove(key);
        return 0;
    }

    private static boolean persistBuiltBank(
            ServerLevel level,
            EconomyService economy,
            UUID villageId,
            long bankKey,
            BankBuildAttempt attempt) {
        if (attempt.origin() == null || !attempt.build().built()) {
            return false;
        }
        BlockPos bankerPosition = attempt.origin().offset(
                BANK_WIDTH / 2, 1, BANK_DEPTH - 2);
        if (!flushBankChunks(level)) {
            rollbackBank(level, attempt.build().placements());
            flushBankChunks(level);
            return false;
        }
        if (!economy.markGeneratedBankRegion(
                bankKey,
                bankerPosition.asLong(),
                villageId,
                BANK_STRUCTURE_VERSION)) {
            rollbackBank(level, attempt.build().placements());
            // The first barrier may already have persisted part of the attempted Bank. Save the
            // matching rollback before returning so a marker failure is far less likely to leave
            // an orphaned structure after a crash.
            flushBankChunks(level);
            return false;
        }
        // Only after the durable bank authority succeeds: failed builds/rollbacks cannot drop loot.
        for (BankMutation mutation : attempt.build().placements()) {
            VillageStructureLoot.assignNewStorage(level, mutation.placement().position(),
                    VillageStructureLoot.key("bank"));
        }
        ensureBanker(level, bankerPosition, true, bankKey, economy);
        LAST_FALLBACK_BANK_RETRY_TICK.remove(bankKey);
        return true;
    }

    private static boolean establishFallbackBanker(
            ServerLevel level,
            BlockPos villagePosition,
            long bankKey,
            UUID villageId,
            EconomyService economy) {
        // Commit retry provenance before tagging or spawning an entity. A marker save failure must
        // not leave an untracked Banker that can be duplicated after its chunk unloads.
        if (!economy.markFallbackBankRegion(
                bankKey, villagePosition.asLong(), villageId)) {
            return false;
        }
        return ensureBanker(level, villagePosition, false, bankKey, economy);
    }

    private static boolean ensureBanker(
            ServerLevel level,
            BlockPos bankerAnchor,
            boolean generatedStructure,
            long regionKey,
            EconomyService economy) {
        BlockPos counter = bankerAnchor.north();
        BlockPos exchangeDeskPosition = generatedStructure
                        && isLoaded(level, counter)
                        && BankerProfessionSupport.isExchangeDesk(level.getBlockState(counter))
                ? counter
                : null;
        UUID canonicalBankerId = economy.generatedBankerId(regionKey);
        if (economy.hasPendingGeneratedBankerConversion(regionKey)
                || PENDING_BANKER_CONVERSIONS.containsRegion(regionKey)
                || AMBIGUOUS_BANKER_CONVERSION_REGIONS.contains(regionKey)) {
            // The converted entity must reach a Minecraft chunk save before its new UUID can
            // replace canonical ownership. Never consume a transient death tombstone first.
            return true;
        }
        boolean durableDeath = canonicalBankerId != null
                && economy.generatedBankerDeathPending(regionKey, canonicalBankerId);
        if (PENDING_BANKER_DEATHS.containsRegion(regionKey) && !durableDeath) {
            // Do not save a replacement entity until the old entity's observed death is durable.
            // Otherwise a crash between Minecraft's entity save and the economy save could leave
            // a live replacement that an old canonical UUID permanently blocks after restart.
            return true;
        }
        if (canonicalBankerId != null) {
            Entity canonicalEntity = findLoadedEntity(
                    level.getServer(), canonicalBankerId, regionKey);
            if (AMBIGUOUS_BANKER_CONVERSION_REGIONS.contains(regionKey)) {
                return true;
            }
            if (canonicalEntity != null && !canonicalEntity.isRemoved()) {
                if (!canonicalEntity.isAlive()) {
                    // Even a durable tombstone cannot outrank a loaded entity that is still in
                    // its revivable death animation. Wait for actual removal and a fresh entity
                    // save before allowing replacement.
                    queueBankerDeathSaveBarrier(
                            canonicalEntity, regionKey, canonicalBankerId);
                    return true;
                }
                if (durableDeath) {
                    // A persisted death tombstone can outlive an entity-save rollback. Seeing the
                    // exact canonical entity alive is stronger evidence than the stale marker.
                    if (!economy.confirmGeneratedBankerAlive(regionKey, canonicalBankerId)) {
                        return true;
                    }
                    PENDING_BANKER_DEATHS.remove(regionKey, canonicalBankerId);
                    durableDeath = false;
                }
            }
            if (canonicalEntity != null
                    && canonicalEntity.isAlive()
                    && canonicalEntity.level() != level) {
                // A generated Banker can be transported, or restored by rollback, in another
                // dimension. It remains exclusive ownership but never receives an Overworld
                // desk/home binding and never permits a replacement at the Bank.
                return true;
            }
            if (canonicalEntity instanceof Villager canonical && canonical.isAlive()) {
                // Canonical UUID ownership is stronger than an entity tag that may have been lost
                // during a save migration or cure. Repair it when safe; otherwise fail closed and
                // never create a duplicate beside a still-living canonical villager.
                if (!BankerAccess.markBanker(canonical, regionKey, exchangeDeskPosition)) {
                    return true;
                }
                Long assignedAnchor = economy.generatedBankerAssignedAnchor(regionKey);
                canonical.setHomeTo(
                        bankerAnchor, EmeraldConfig.current().bankerRestrictionRadius());
                if (generatedStructure
                        && assignedAnchor != null
                        && assignedAnchor.longValue() != bankerAnchor.asLong()) {
                    int relocationRadius = Math.max(
                            12, EmeraldConfig.current().bankerRestrictionRadius() + 8);
                    double relocationRadiusSquared =
                            (double) relocationRadius * relocationRadius;
                    boolean atCurrentSite = canonical.blockPosition().distSqr(bankerAnchor)
                            <= relocationRadiusSquared;
                    boolean atAssignedSite = canonical.blockPosition().distSqr(
                                    BlockPos.of(assignedAnchor))
                            <= relocationRadiusSquared;
                    if (!atCurrentSite && atAssignedSite) {
                        canonical.teleportTo(
                                bankerAnchor.getX() + 0.5,
                                bankerAnchor.getY(),
                                bankerAnchor.getZ() + 0.5);
                        atCurrentSite = true;
                    }
                    // Do not claim the move durably until Minecraft has saved the entity at the
                    // destination. If the Banker is near neither site, a player may have moved it;
                    // keep the old assignment so returning naturally to the new Bank can still be
                    // confirmed without forcibly teleporting it away from the player's location.
                    if (atCurrentSite && flushBankEntitiesAndChunks(level)) {
                        economy.confirmGeneratedBankerAssignment(
                                regionKey, canonicalBankerId, bankerAnchor.asLong());
                    }
                }
                return true;
            }
            if (canonicalEntity != null && canonicalEntity.isAlive()) {
                // An unexpected live entity with the canonical UUID remains exclusive ownership.
                // Normal infection/cure UUID changes are handled by the save-ordered conversion
                // lineage above before this method is allowed to create a replacement.
                return true;
            }
            if (canonicalEntity != null && !durableDeath) {
                queueBankerDeathSaveBarrier(
                        canonicalEntity, regionKey, canonicalBankerId);
                return true;
            }
            // A null lookup means the canonical entity is unloaded, not dead. Desk access remains
            // available, and no second Banker is created unless an exact, durable death tombstone
            // survived the restart. rememberGeneratedBanker atomically consumes that tombstone.
            if (canonicalEntity == null && !durableDeath) {
                return true;
            }
            if (!durableDeath) {
                return true;
            }
        }
        // Migration and save-retry states can leave a scoped Banker loaded far from the eventual
        // safe Bank. Look broadly for that exact entity before considering an ordinary villager.
        double scopedSearchRadius = generatedStructure
                ? FALLBACK_BANK_RECOVERY_RADIUS
                : 48.0;
        AABB scopedSearch = new AABB(bankerAnchor).inflate(scopedSearchRadius,
                FALLBACK_BANK_RECOVERY_RADIUS,
                scopedSearchRadius);
        Villager existing = level.getEntitiesOfClass(
                Villager.class,
                scopedSearch,
                villager -> villager.isAlive()
                        && !villager.isBaby()
                        && BankerAccess.isBankerForRegion(villager, regionKey))
                .stream()
                .min(Comparator.comparingDouble((Villager villager) ->
                        villager.distanceToSqr(
                                bankerAnchor.getX() + 0.5,
                                bankerAnchor.getY() + 0.5,
                                bankerAnchor.getZ() + 0.5))
                        .thenComparing(villager -> villager.getUUID().toString()))
                .orElse(null);
        if (existing != null) {
            BankerAccess.markBanker(existing, regionKey, exchangeDeskPosition);
            existing.setHomeTo(bankerAnchor, EmeraldConfig.current().bankerRestrictionRadius());
            rememberBankerAfterEntitySave(level, economy, regionKey, existing);
            return true;
        }

        AABB search = new AABB(bankerAnchor).inflate(48.0, 20.0, 48.0);
        List<Villager> villagers = level.getEntitiesOfClass(
                Villager.class,
                search,
                villager -> villager.isAlive() && !villager.isBaby());

        if (generatedStructure) {
            Villager retiredBanker = findRetiredBanker(
                    level, bankerAnchor, regionKey, economy.retiredBankAnchors(regionKey));
            if (retiredBanker != null
                    && BankerAccess.markBanker(
                            retiredBanker, regionKey, exchangeDeskPosition)) {
                retiredBanker.teleportTo(
                        bankerAnchor.getX() + 0.5,
                        bankerAnchor.getY(),
                        bankerAnchor.getZ() + 0.5);
                retiredBanker.setHomeTo(
                        bankerAnchor, EmeraldConfig.current().bankerRestrictionRadius());
                rememberBankerAfterEntitySave(level, economy, regionKey, retiredBanker);
                return true;
            }
        }

        // An extinct or deliberately abandoned village keeps account access through its bank
        // lectern, but it does not receive a free replacement Banker until recovery begins.
        if (!economy.allowBankerReplacementForRegion(regionKey, bankerAnchor.asLong())) {
            return true;
        }

        // Adopt one legacy unscoped Banker before creating a replacement. This migrates
        // alpha saves while still preventing two nearby banks from sharing the same villager.
        Villager legacyBanker = villagers.stream()
                .filter(BankerAccess::isLegacyUnscopedBanker)
                .min(Comparator.comparingDouble((Villager villager) ->
                        villager.distanceToSqr(
                                bankerAnchor.getX() + 0.5,
                                bankerAnchor.getY() + 0.5,
                                bankerAnchor.getZ() + 0.5))
                        .thenComparing(villager -> villager.getUUID().toString()))
                .orElse(null);
        if (legacyBanker != null
                && BankerAccess.markBanker(legacyBanker, regionKey, exchangeDeskPosition)) {
            legacyBanker.setHomeTo(
                    bankerAnchor, EmeraldConfig.current().bankerRestrictionRadius());
            rememberBankerAfterEntitySave(level, economy, regionKey, legacyBanker);
            return true;
        }

        // Every replacement first prefers an untouched unemployed adult. Established villagers,
        // including traded librarians, are never converted or reset.
        Villager candidate = villagers.stream()
                .filter(BankerAccess::isEligibleBankerCandidate)
                .min(Comparator.comparingDouble((Villager villager) ->
                        villager.distanceToSqr(
                                bankerAnchor.getX() + 0.5,
                                bankerAnchor.getY() + 0.5,
                                bankerAnchor.getZ() + 0.5))
                        .thenComparing(villager -> villager.getUUID().toString()))
                .orElse(null);
        if (candidate != null
                && BankerAccess.markBanker(candidate, regionKey, exchangeDeskPosition)) {
            candidate.setHomeTo(
                    bankerAnchor, EmeraldConfig.current().bankerRestrictionRadius());
            rememberBankerAfterEntitySave(level, economy, regionKey, candidate);
            return true;
        }

        BlockPos spawnPosition = bankerAnchor;
        if (!generatedStructure) {
            spawnPosition = findFallbackBankerSpawn(level, bankerAnchor);
            if (spawnPosition == null) {
                return false;
            }
        }
        return spawnBanker(
                level,
                spawnPosition,
                generatedStructure,
                regionKey,
                exchangeDeskPosition,
                economy);
    }

    /** Finds dry two-block headroom near a Banker-only fallback instead of spawning on water. */
    private static BlockPos findFallbackBankerSpawn(ServerLevel level, BlockPos anchor) {
        int[][] offsets = {
                {0, 0}, {2, 0}, {-2, 0}, {0, 2}, {0, -2},
                {3, 3}, {-3, 3}, {3, -3}, {-3, -3},
                {5, 0}, {-5, 0}, {0, 5}, {0, -5},
                {6, 4}, {-6, 4}, {6, -4}, {-6, -4}
        };
        for (int[] offset : offsets) {
            int x = anchor.getX() + offset[0];
            int z = anchor.getZ() + offset[1];
            if (!level.hasChunk(Math.floorDiv(x, 16), Math.floorDiv(z, 16))) {
                continue;
            }
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos feet = new BlockPos(x, y, z);
            BlockPos head = feet.above();
            BlockPos ground = feet.below();
            BlockState feetState = level.getBlockState(feet);
            BlockState headState = level.getBlockState(head);
            if (!level.getFluidState(feet).isEmpty()
                    || !level.getFluidState(head).isEmpty()
                    || !level.getFluidState(ground).isEmpty()
                    || level.getBlockEntity(feet) != null
                    || level.getBlockEntity(head) != null
                    || (!feetState.isAir() && !feetState.canBeReplaced())
                    || (!headState.isAir() && !headState.canBeReplaced())
                    || !level.getBlockState(ground).isFaceSturdy(level, ground, Direction.UP)) {
                continue;
            }
            return feet;
        }
        return null;
    }

    /**
     * Persists an adopted or moved entity before its UUID becomes canonical in the economy file.
     * A failed barrier leaves the tagged live entity discoverable for a later retry, while the old
     * canonical UUID/tombstone remains fail-closed across a crash.
     */
    private static void rememberBankerAfterEntitySave(
            ServerLevel level,
            EconomyService economy,
            long regionKey,
            Villager banker) {
        if (flushBankEntitiesAndChunks(level)) {
            economy.rememberGeneratedBanker(regionKey, banker.getUUID());
        }
    }

    /** Reuses the mod-owned Banker from any loaded retired site before creating another one. */
    private static Villager findRetiredBanker(
            ServerLevel level,
            BlockPos destination,
            long regionKey,
            List<Long> retiredPackedAnchors) {
        Villager closest = null;
        double closestDistance = Double.MAX_VALUE;
        for (Long packed : retiredPackedAnchors) {
            if (packed == null) {
                continue;
            }
            BlockPos retired = BlockPos.of(packed);
            if (!isLoaded(level, retired)) {
                continue;
            }
            AABB search = new AABB(retired).inflate(48.0, 20.0, 48.0);
            for (Villager villager : level.getEntitiesOfClass(
                    Villager.class,
                    search,
                    candidate -> candidate.isAlive()
                            && !candidate.isBaby()
                            && BankerAccess.isBankerForRegion(candidate, regionKey))) {
                double distance = villager.distanceToSqr(
                        destination.getX() + 0.5,
                        destination.getY() + 0.5,
                        destination.getZ() + 0.5);
                if (distance < closestDistance
                        || (distance == closestDistance
                                && closest != null
                                && villager.getUUID().toString().compareTo(
                                        closest.getUUID().toString()) < 0)) {
                    closest = villager;
                    closestDistance = distance;
                }
            }
        }
        return closest;
    }

    /**
     * Begins a save-ordered identity handoff when vanilla converts a generated Banker.
     *
     * <p>Minecraft creates a new UUID for infection and cure. The converted entity receives an
     * exact predecessor marker during the conversion callback, before it is added to the level.
     * A later server tick saves that entity first and only then changes canonical economy state.</p>
     */
    public static void onBankerConversion(
            Entity previous, Entity converted, EconomyService economy) {
        if (previous == null
                || converted == null
                || previous.getUUID().equals(converted.getUUID())
                || (!(previous instanceof Villager)
                        && !(previous instanceof ZombieVillager))) {
            return;
        }
        UUID predecessorId = previous.getUUID();
        Long regionKey = canonicalBankerRegion(previous, economy);
        BankerConversionRetryQueue.PendingHandoff inheritedRuntime = null;
        if (regionKey == null) {
            inheritedRuntime = PENDING_BANKER_CONVERSIONS.findByConvertedId(previous.getUUID());
            if (inheritedRuntime != null) {
                regionKey = inheritedRuntime.regionKey();
                predecessorId = inheritedRuntime.handoff().previousId();
            }
        }
        EconomyService.GeneratedBankerConversion inheritedDurable = null;
        if (regionKey == null) {
            inheritedDurable = economy.pendingGeneratedBankerConversionByTarget(
                    previous.getUUID());
            if (inheritedDurable != null) {
                regionKey = inheritedDurable.regionKey();
                predecessorId = inheritedDurable.rootCanonicalId();
            }
        }
        if (regionKey == null) {
            inheritedDurable = economy.pendingGeneratedBankerConversionBySource(
                    previous.getUUID());
            if (inheritedDurable != null
                    && !inheritedDurable.rootCanonicalId().equals(previous.getUUID())) {
                regionKey = inheritedDurable.regionKey();
                predecessorId = inheritedDurable.rootCanonicalId();
            }
        }
        if (regionKey == null) {
            Long taggedRegion = BankerAccess.bankRegionKey(previous);
            UUID taggedPredecessor = BankerAccess.bankerConversionPredecessor(previous);
            if (taggedRegion != null
                    && taggedPredecessor != null
                    && taggedPredecessor.equals(economy.generatedBankerId(taggedRegion))
                    && BankerAccess.isBankerConversionFrom(
                            previous, taggedRegion, taggedPredecessor)) {
                regionKey = taggedRegion;
                predecessorId = taggedPredecessor;
            }
        }
        if (regionKey == null) {
            return;
        }
        boolean continuingBanker = (previous instanceof Villager
                        && converted instanceof ZombieVillager)
                || (previous instanceof ZombieVillager && converted instanceof Villager);
        String targetDimension = converted.level().dimension().identifier().toString();
        EconomyService.BankerConversionResult preparedResult =
                economy.prepareGeneratedBankerConversion(
                        regionKey,
                        predecessorId,
                        previous.getUUID(),
                        converted.getUUID(),
                        targetDimension,
                        converted.blockPosition().asLong(),
                        continuingBanker);
        if (!preparedResult.accepted() || preparedResult.conversion() == null) {
            // A matching durable handoff must exist before vanilla can save the new UUID. If the
            // economy write fails, remove every managed/profession identity from the outcome so
            // an already-durable death tombstone can safely authorize a future replacement.
            BankerAccess.neutralizeFailedBankerConversion(converted);
            queueBankerDeathSaveBarrier(previous, regionKey, predecessorId);
            return;
        }
        EconomyService.GeneratedBankerConversion prepared = preparedResult.conversion();
        // Marker failure cannot invalidate durable format-17 authority. Entity-load recovery uses
        // the exact target UUID and will retry after freeing stale scoreboard tags.
        BankerAccess.markBankerConversion(
                converted,
                prepared.regionKey(),
                prepared.rootCanonicalId(),
                prepared.immediateSourceId());
        BankerConversionRetryQueue.Handoff superseded =
                PENDING_BANKER_CONVERSIONS.removeRegion(prepared.regionKey());
        if (superseded != null
                && !superseded.convertedId().equals(prepared.targetId())) {
            CALLBACK_BANKER_CONVERSION_OUTCOMES.remove(superseded.convertedId());
            DURABLY_REMOVED_BANKER_CONVERSION_ENTITIES.remove(superseded.convertedId());
        }
        boolean queued = PENDING_BANKER_CONVERSIONS.offer(
                prepared.regionKey(),
                prepared.rootCanonicalId(),
                prepared.immediateSourceId(),
                prepared.targetId(),
                continuingBanker);
        if (!queued) {
            AMBIGUOUS_BANKER_CONVERSION_REGIONS.add(prepared.regionKey());
        } else {
            CALLBACK_BANKER_CONVERSION_OUTCOMES.add(converted.getUUID());
        }
    }

    /** Explicit death transition for the canonical UUID; chunk unloads never clear it. */
    public static void onBankerDeath(Villager villager, EconomyService economy) {
        recordCanonicalBankerDeath(villager, economy);
    }

    /** A converted Banker is still canonical while infected; only its actual zombie death counts. */
    public static void onZombieBankerDeath(ZombieVillager zombie, EconomyService economy) {
        recordCanonicalBankerDeath(zombie, economy);
    }

    /** Records death of a terminal converted form, such as a lightning-created Witch. */
    public static void onConvertedBankerDeath(Entity entity, EconomyService economy) {
        if (BankerAccess.bankerConversionPredecessor(entity) != null
                || economy.pendingGeneratedBankerConversionByTarget(
                        entity.getUUID()) != null) {
            recordCanonicalBankerDeath(entity, economy);
        }
    }

    private static void recordCanonicalBankerDeath(Entity bankerEntity, EconomyService economy) {
        UUID bankerId = bankerEntity.getUUID();
        EconomyService.GeneratedBankerConversion durableConversion =
                economy.pendingGeneratedBankerConversionByTarget(bankerId);
        if (durableConversion == null) {
            durableConversion = economy.pendingGeneratedBankerConversionBySource(bankerId);
        }
        if (durableConversion != null) {
            queueBankerDeathSaveBarrier(
                    bankerEntity,
                    durableConversion.regionKey(),
                    durableConversion.rootCanonicalId());
            return;
        }
        BankerConversionRetryQueue.PendingHandoff pendingConversion =
                PENDING_BANKER_CONVERSIONS.findByConvertedId(bankerId);
        if (pendingConversion != null) {
            queueBankerDeathSaveBarrier(
                    bankerEntity,
                    pendingConversion.regionKey(),
                    pendingConversion.handoff().previousId());
            return;
        }
        Long taggedRegion = BankerAccess.bankRegionKey(bankerEntity);
        UUID taggedPredecessor = BankerAccess.bankerConversionPredecessor(bankerEntity);
        if (taggedRegion != null
                && taggedPredecessor != null
                && taggedPredecessor.equals(economy.generatedBankerId(taggedRegion))
                && BankerAccess.isBankerConversionFrom(
                        bankerEntity, taggedRegion, taggedPredecessor)) {
            if (AMBIGUOUS_BANKER_CONVERSION_REGIONS.contains(taggedRegion)) {
                // One marked successor dying does not prove a second exact successor is gone.
                return;
            }
            BankerConversionRetryQueue.Handoff queued =
                    PENDING_BANKER_CONVERSIONS.handoffForRegion(taggedRegion);
            if (queued != null && !bankerId.equals(queued.convertedId())) {
                // Two exact successors are live or unresolved. Neither one's death proves the
                // other is gone, so retain canonical ownership and fail closed.
                AMBIGUOUS_BANKER_CONVERSION_REGIONS.add(taggedRegion);
                return;
            }
            queueBankerDeathSaveBarrier(
                    bankerEntity, taggedRegion, taggedPredecessor);
            return;
        }
        Long regionKey = canonicalBankerRegion(bankerEntity, economy);
        if (regionKey == null) {
            // A retired legacy duplicate may still carry a stale region tag. Its death must not
            // clear or block the distinct canonical identity.
            return;
        }
        queueBankerDeathSaveBarrier(bankerEntity, regionKey, bankerId);
    }

    private static void queueBankerDeathSaveBarrier(
            Entity bankerEntity, long regionKey, UUID canonicalRootId) {
        if (!(bankerEntity.level() instanceof ServerLevel level)) {
            return;
        }
        PENDING_BANKER_DEATH_SAVE_BARRIERS.putIfAbsent(
                bankerEntity.getUUID(),
                new BankerDeathSaveBarrier(
                        regionKey, canonicalRootId, bankerEntity.getUUID(), level, bankerEntity));
    }

    private static Long canonicalBankerRegion(Entity entity, EconomyService economy) {
        UUID bankerId = entity.getUUID();
        Long taggedRegion = BankerAccess.bankRegionKey(entity);
        if (taggedRegion != null
                && bankerId.equals(economy.generatedBankerId(taggedRegion))) {
            return taggedRegion;
        }
        // Conversion mods are not required to preserve scoreboard tags. Resolve the durable UUID
        // in this rare lifecycle path without ever accepting a merely nearby entity.
        return economy.generatedBankAnchorsSnapshot().keySet().stream()
                .filter(candidate -> bankerId.equals(economy.generatedBankerId(candidate)))
                .min(Long::compareUnsigned)
                .orElse(null);
    }

    private static void processStartupBankerConversions(
            MinecraftServer server, EconomyService economy) {
        if (!bankerConversionRecoveryReady
                || bankerConversionServer != server
                || initialBankerConversionScanComplete) {
            return;
        }
        int stop = Math.min(
                STARTUP_BANKER_CONVERSION_SNAPSHOT.size(),
                startupBankerConversionCursor + BANKER_CONVERSION_DISCOVERIES_PER_SCAN);
        while (startupBankerConversionCursor < stop) {
            inspectBankerConversionCandidate(
                    STARTUP_BANKER_CONVERSION_SNAPSHOT.get(
                            startupBankerConversionCursor++),
                    economy);
        }
        if (startupBankerConversionCursor
                >= STARTUP_BANKER_CONVERSION_SNAPSHOT.size()) {
            finishBankerConversionScan(economy);
            initialBankerConversionScanComplete = true;
            STARTUP_BANKER_CONVERSION_SNAPSHOT.clear();
            startupBankerConversionCursor = 0;
        }
    }

    private static void queueLoadedBankerConversion(
            Entity entity, EconomyService economy) {
        EconomyService.GeneratedBankerConversion prepared =
                economy.pendingGeneratedBankerConversionByTarget(entity.getUUID());
        if (prepared != null) {
            BankerAccess.markBankerConversion(
                    entity,
                    prepared.regionKey(),
                    prepared.rootCanonicalId(),
                    prepared.immediateSourceId());
            queuePreparedBankerConversion(prepared, entity);
            return;
        }
        EconomyService.GeneratedBankerConversion preparedSource =
                economy.pendingGeneratedBankerConversionBySource(entity.getUUID());
        if (preparedSource != null
                && !preparedSource.rootCanonicalId().equals(entity.getUUID())) {
            queuePreparedBankerConversion(preparedSource, entity);
            return;
        }
        UUID previousId = BankerAccess.bankerConversionPredecessor(entity);
        if (previousId == null) {
            return;
        }
        Long regionKey = BankerAccess.bankRegionKey(entity);
        UUID canonical = regionKey == null ? null : economy.generatedBankerId(regionKey);
        if (regionKey == null || canonical == null) {
            BankerAccess.neutralizeFailedBankerConversion(entity);
            return;
        }
        if (canonical.equals(entity.getUUID())) {
            BankerAccess.clearBankerConversionMarker(entity, previousId);
            return;
        }
        if (!canonical.equals(previousId)) {
            BankerAccess.neutralizeFailedBankerConversion(entity);
            return;
        }
        UUID sourceId = Optional.ofNullable(BankerAccess.bankerConversionSource(entity))
                .orElse(previousId);
        if (AMBIGUOUS_BANKER_CONVERSION_REGIONS.contains(regionKey)) {
            return;
        }
        EconomyService.BankerConversionResult preparation =
                economy.prepareGeneratedBankerConversion(
                        regionKey,
                        previousId,
                        sourceId,
                        entity.getUUID(),
                        entity.level().dimension().identifier().toString(),
                        entity.blockPosition().asLong(),
                        entity instanceof Villager || entity instanceof ZombieVillager);
        if (!preparation.accepted() || preparation.conversion() == null) {
            PENDING_BANKER_CONVERSIONS.removeRegion(regionKey);
            AMBIGUOUS_BANKER_CONVERSION_REGIONS.add(regionKey);
            return;
        }
        queuePreparedBankerConversion(preparation.conversion(), entity);
    }

    private static void inspectBankerConversionCandidate(
            Entity entity, EconomyService economy) {
        EconomyService.GeneratedBankerConversion prepared =
                economy.pendingGeneratedBankerConversionByTarget(entity.getUUID());
        if (prepared != null) {
            queuePreparedBankerConversion(prepared, entity);
            return;
        }
        UUID previousId = BankerAccess.bankerConversionPredecessor(entity);
        if (previousId == null) {
            return;
        }
        Long regionKey = BankerAccess.bankRegionKey(entity);
        UUID canonical = regionKey == null ? null : economy.generatedBankerId(regionKey);
        if (regionKey == null || canonical == null) {
            BankerAccess.neutralizeFailedBankerConversion(entity);
            return;
        }
        if (canonical.equals(entity.getUUID())) {
            BankerAccess.clearBankerConversionMarker(entity, previousId);
            return;
        }
        if (!canonical.equals(previousId)) {
            // The region already committed a different lineage. Keep this stale converted entity
            // in-world, but strip its ability to masquerade as another Banker.
            BankerAccess.neutralizeFailedBankerConversion(entity);
            return;
        }
        BankerConversionRetryQueue.Handoff candidate = new BankerConversionRetryQueue.Handoff(
                previousId,
                Optional.ofNullable(BankerAccess.bankerConversionSource(entity))
                        .orElse(previousId),
                entity.getUUID(),
                entity instanceof Villager || entity instanceof ZombieVillager);
        List<BankerConversionRetryQueue.Handoff> candidates =
                DISCOVERED_BANKER_CONVERSIONS.computeIfAbsent(
                        regionKey, ignored -> new ArrayList<>(2));
        if (candidates.contains(candidate)) {
            return;
        }
        if (candidates.size() < 2) {
            candidates.add(candidate);
        } else {
            // Two identities are enough to prove ambiguity. Do not retain an attacker-controlled
            // number of forged scoreboard markers while completing the bounded scan.
            DISCOVERED_AMBIGUOUS_BANKER_CONVERSIONS.add(regionKey);
        }
    }

    private static void finishBankerConversionScan(EconomyService economy) {
        for (Map.Entry<Long, List<BankerConversionRetryQueue.Handoff>> entry
                : DISCOVERED_BANKER_CONVERSIONS.entrySet()) {
            long regionKey = entry.getKey();
            if (DISCOVERED_AMBIGUOUS_BANKER_CONVERSIONS.contains(regionKey)) {
                PENDING_BANKER_CONVERSIONS.removeRegion(regionKey);
                AMBIGUOUS_BANKER_CONVERSION_REGIONS.add(regionKey);
                continue;
            }
            List<BankerConversionRetryQueue.Handoff> valid = new ArrayList<>(2);
            for (BankerConversionRetryQueue.Handoff handoff : entry.getValue()) {
                if (!handoff.previousId().equals(economy.generatedBankerId(regionKey))) {
                    continue;
                }
                Entity candidate = findLoadedEntity(
                        bankerConversionServer, handoff.convertedId(), regionKey);
                if (AMBIGUOUS_BANKER_CONVERSION_REGIONS.contains(regionKey)) {
                    break;
                }
                if (candidate != null
                        && BankerAccess.isBankerConversionFrom(
                                candidate, regionKey, handoff.previousId())) {
                    valid.add(new BankerConversionRetryQueue.Handoff(
                            handoff.previousId(),
                            handoff.sourceId(),
                            handoff.convertedId(),
                            candidate instanceof Villager
                                    || candidate instanceof ZombieVillager));
                }
            }
            if (valid.size() > 1) {
                PENDING_BANKER_CONVERSIONS.removeRegion(regionKey);
                AMBIGUOUS_BANKER_CONVERSION_REGIONS.add(regionKey);
                continue;
            }
            if (valid.isEmpty()) {
                continue;
            }
            BankerConversionRetryQueue.Handoff handoff = valid.getFirst();
            // A completed loaded-entity pass found exactly one still-valid successor. This is the
            // only automatic way a prior in-session ambiguity lock may clear.
            Entity target = findLoadedEntity(
                    bankerConversionServer, handoff.convertedId(), regionKey);
            if (AMBIGUOUS_BANKER_CONVERSION_REGIONS.contains(regionKey)) {
                continue;
            }
            EconomyService.BankerConversionResult preparation = target == null
                    ? new EconomyService.BankerConversionResult(
                            EconomyService.BankerConversionStatus.STALE, null)
                    : economy.prepareGeneratedBankerConversion(
                            regionKey,
                            handoff.previousId(),
                            handoff.sourceId(),
                            handoff.convertedId(),
                            target.level().dimension().identifier().toString(),
                            target.blockPosition().asLong(),
                            handoff.continuingBanker());
            if (!preparation.accepted() || preparation.conversion() == null) {
                PENDING_BANKER_CONVERSIONS.removeRegion(regionKey);
                AMBIGUOUS_BANKER_CONVERSION_REGIONS.add(regionKey);
                continue;
            }
            AMBIGUOUS_BANKER_CONVERSION_REGIONS.remove(regionKey);
            queuePreparedBankerConversion(preparation.conversion(), target);
        }
        DISCOVERED_BANKER_CONVERSIONS.clear();
        DISCOVERED_AMBIGUOUS_BANKER_CONVERSIONS.clear();
    }

    private static void retryPendingBankerConversions(
            MinecraftServer server, EconomyService economy) {
        PENDING_BANKER_CONVERSIONS.retry((regionKey, handoff) -> {
            if (AMBIGUOUS_BANKER_CONVERSION_REGIONS.contains(regionKey)) {
                return false;
            }
            EconomyService.GeneratedBankerConversion prepared =
                    economy.pendingGeneratedBankerConversion(regionKey);
            Entity converted = findLoadedEntity(server, handoff.convertedId(), regionKey);
            UUID canonical = economy.generatedBankerId(regionKey);
            if (prepared == null) {
                if (handoff.convertedId().equals(canonical)) {
                    if (converted != null) {
                        BankerAccess.clearBankerConversionMarker(
                                converted, handoff.previousId());
                    }
                    PENDING_BANKER_DEATHS.remove(regionKey, handoff.previousId());
                } else if (converted != null) {
                    BankerAccess.neutralizeFailedBankerConversion(converted);
                }
                retireBankerConversionTracking(handoff);
                return true;
            }
            if (!prepared.rootCanonicalId().equals(handoff.previousId())
                    || !prepared.immediateSourceId().equals(handoff.sourceId())
                    || !prepared.targetId().equals(handoff.convertedId())) {
                // Durable state is authoritative. Keep replacement gated until its exact runtime
                // entry is restored by startup or entity-load recovery.
                return false;
            }
            Entity root = findLoadedEntity(
                    server, prepared.rootCanonicalId(), regionKey);
            Entity source = prepared.immediateSourceId().equals(
                            prepared.rootCanonicalId())
                    ? root
                    : findLoadedEntity(server, prepared.immediateSourceId(), regionKey);
            if (AMBIGUOUS_BANKER_CONVERSION_REGIONS.contains(regionKey)) {
                // The same UUID loaded in more than one dimension is not one provable entity.
                return false;
            }
            if (isPendingBankerDeathAnimation(root)
                    || isPendingBankerDeathAnimation(source)
                    || isPendingBankerDeathAnimation(converted)) {
                // Zero health is still revivable. The removal-save barrier owns this transition;
                // no conversion phase may treat an in-world corpse as an absent participant.
                return false;
            }
            boolean rootLive = root != null && !root.isRemoved() && root.isAlive();
            boolean sourceLive = source != null && !source.isRemoved() && source.isAlive();
            boolean targetLive = converted != null
                    && !converted.isRemoved()
                    && converted.isAlive();

            if (prepared.phase() != EconomyState.BankerConversionPhase.PREPARED) {
                EconomyService.BankerConversionResult result;
                switch (prepared.phase()) {
                    case TARGET_DURABLE -> {
                        if (rootLive || sourceLive) {
                            return false;
                        }
                        if (DURABLY_REMOVED_BANKER_CONVERSION_ENTITIES.contains(
                                prepared.targetId())) {
                            EconomyService.BankerConversionResult retired =
                                    economy.markGeneratedBankerConversionRetirementDurable(
                                            prepared);
                            if (!retired.accepted() || retired.conversion() == null) {
                                return false;
                            }
                            prepared = retired.conversion();
                            result = economy.retireGeneratedBankerConversion(prepared);
                        } else {
                            if (prepared.continuingBanker() && !targetLive) {
                                // A target saved before the durable phase may die before the
                                // economy commit. After a crash, absence is not proof that this
                                // did not happen, so only an exact live successor can be adopted.
                                return false;
                            }
                            result = prepared.continuingBanker()
                                    ? economy.commitGeneratedBankerConversion(prepared)
                                    : economy.retireGeneratedBankerConversion(prepared);
                        }
                    }
                    case SOURCE_DURABLE -> {
                        if (rootLive || targetLive) {
                            return false;
                        }
                        if (DURABLY_REMOVED_BANKER_CONVERSION_ENTITIES.contains(
                                prepared.immediateSourceId())) {
                            EconomyService.BankerConversionResult retired =
                                    economy.markGeneratedBankerConversionRetirementDurable(
                                            prepared);
                            if (!retired.accepted() || retired.conversion() == null) {
                                return false;
                            }
                            prepared = retired.conversion();
                            result = economy.retireGeneratedBankerConversion(prepared);
                        } else {
                            // SOURCE_DURABLE proves the source was saved, not that it remained
                            // alive until a later/restarted economy commit.
                            if (!sourceLive) {
                                return false;
                            }
                            result = economy.commitGeneratedBankerConversionSource(prepared);
                        }
                    }
                    case RETIRE_DURABLE -> {
                        if (rootLive || sourceLive || targetLive) {
                            return false;
                        }
                        result = economy.retireGeneratedBankerConversion(prepared);
                    }
                    case ROOT_DURABLE -> {
                        if (targetLive || (sourceLive && source != root)) {
                            return false;
                        }
                        if (DURABLY_REMOVED_BANKER_CONVERSION_ENTITIES.contains(
                                prepared.rootCanonicalId())) {
                            EconomyService.BankerConversionResult retired =
                                    economy.markGeneratedBankerConversionRetirementDurable(
                                            prepared);
                            if (!retired.accepted() || retired.conversion() == null) {
                                return false;
                            }
                            prepared = retired.conversion();
                            result = economy.retireGeneratedBankerConversion(prepared);
                        } else {
                            // ROOT_DURABLE proves rollback storage, not continued liveness.
                            if (!rootLive) {
                                return false;
                            }
                            result = economy.abortGeneratedBankerConversion(prepared);
                        }
                    }
                    default -> throw new IllegalStateException(
                            "Unhandled Banker conversion phase " + prepared.phase());
                }
                if (!result.accepted()) {
                    return false;
                }
                if (converted != null) {
                    if (prepared.continuingBanker()) {
                        BankerAccess.clearBankerConversionMarker(
                                converted, prepared.rootCanonicalId());
                    } else {
                        BankerAccess.clearBankerLineage(converted);
                    }
                }
                PENDING_BANKER_DEATHS.remove(regionKey, prepared.rootCanonicalId());
                retireBankerConversionTracking(handoff);
                return true;
            }

            boolean callbackOutcome = CALLBACK_BANKER_CONVERSION_OUTCOMES.contains(
                    prepared.targetId());
            boolean targetRemovalDurable = DURABLY_REMOVED_BANKER_CONVERSION_ENTITIES.contains(
                    prepared.targetId());

            if (rootLive) {
                if (targetLive || (sourceLive && source != root)) {
                    return false;
                }
                if (!callbackOutcome && !targetRemovalDurable) {
                    // After restart, a PREPARED target may exist in an unloaded chunk even while
                    // a rolled-back root is loaded. Keep the durable gate until the target loads.
                    return false;
                }
                if (!flushAllBankEntitiesAndChunks(server)) {
                    return false;
                }
                if (!revalidateBankerConversionAfterEntitySave(
                        server,
                        economy,
                        prepared,
                        rootLive,
                        sourceLive,
                        targetLive)) {
                    return false;
                }
                if (converted != null) {
                    BankerAccess.neutralizeFailedBankerConversion(converted);
                }
                EconomyService.BankerConversionResult durableRoot =
                        economy.markGeneratedBankerConversionRootDurable(prepared);
                if (!durableRoot.accepted()
                        || durableRoot.conversion() == null
                        || !economy.abortGeneratedBankerConversion(
                                durableRoot.conversion()).accepted()) {
                    return false;
                }
                retireBankerConversionTracking(handoff);
                return true;
            }

            if (sourceLive && source != root) {
                if (targetLive || (!callbackOutcome && !targetRemovalDurable)) {
                    return false;
                }
                BankerAccess.clearBankerConversionMarker(
                        source, prepared.rootCanonicalId());
                if (source instanceof Villager villager
                        && !prepareConvertedVillager(villager, regionKey, economy)) {
                    return false;
                }
                if (!flushAllBankEntitiesAndChunks(server)) {
                    return false;
                }
                if (!revalidateBankerConversionAfterEntitySave(
                        server,
                        economy,
                        prepared,
                        rootLive,
                        sourceLive,
                        targetLive)) {
                    return false;
                }
                EconomyService.BankerConversionResult durableSource =
                        economy.markGeneratedBankerConversionSourceDurable(prepared);
                if (!durableSource.accepted()
                        || durableSource.conversion() == null
                        || !economy.commitGeneratedBankerConversionSource(
                                durableSource.conversion()).accepted()) {
                    return false;
                }
                if (converted != null) {
                    BankerAccess.neutralizeFailedBankerConversion(converted);
                }
                retireBankerConversionTracking(handoff);
                return true;
            }

            if (!targetLive) {
                if (!callbackOutcome && !targetRemovalDurable) {
                    // A PREPARED target missing after restart is unloaded, not dead.
                    return false;
                }
                if (!flushAllBankEntitiesAndChunks(server)) {
                    return false;
                }
                if (!revalidateBankerConversionAfterEntitySave(
                        server,
                        economy,
                        prepared,
                        rootLive,
                        sourceLive,
                        targetLive)) {
                    return false;
                }
                EconomyService.BankerConversionResult durableRetirement =
                        economy.markGeneratedBankerConversionRetirementDurable(prepared);
                if (!durableRetirement.accepted()
                        || durableRetirement.conversion() == null
                        || !economy.retireGeneratedBankerConversion(
                                durableRetirement.conversion()).accepted()) {
                    return false;
                }
                if (converted != null) {
                    BankerAccess.neutralizeFailedBankerConversion(converted);
                }
                PENDING_BANKER_DEATHS.remove(
                        regionKey, prepared.rootCanonicalId());
                retireBankerConversionTracking(handoff);
                return true;
            }

            if (!callbackOutcome) {
                // PREPARED only says the new UUID was allocated. After restart, target presence
                // cannot prove a rolled-back root/source is absent from an unloaded chunk.
                return false;
            }

            if (!BankerAccess.isBankerConversionFrom(
                            converted, regionKey, prepared.rootCanonicalId())
                    || !BankerAccess.isBankerConversionSource(
                            converted, prepared.immediateSourceId())) {
                if (!BankerAccess.markBankerConversion(
                        converted,
                        regionKey,
                        prepared.rootCanonicalId(),
                        prepared.immediateSourceId())) {
                    return false;
                }
            }
            if (prepared.continuingBanker()
                    && converted instanceof Villager villager
                    && !prepareConvertedVillager(villager, regionKey, economy)) {
                return false;
            }
            if (prepared.continuingBanker()
                    && !(converted instanceof Villager)
                    && !(converted instanceof ZombieVillager)) {
                return false;
            }
            Long convertedOwner = economy.generatedBankerRegion(prepared.targetId());
            if (convertedOwner != null && convertedOwner.longValue() != regionKey) {
                return false;
            }
            if (!ensureConvertedBankerSaved(server, converted, handoff)) {
                return false;
            }
            if (!revalidateBankerConversionAfterEntitySave(
                    server,
                    economy,
                    prepared,
                    rootLive,
                    sourceLive,
                    targetLive)) {
                return false;
            }
            EconomyService.BankerConversionResult durable =
                    economy.markGeneratedBankerConversionEntityDurable(prepared);
            if (!durable.accepted() || durable.conversion() == null) {
                return false;
            }
            EconomyService.BankerConversionResult finished =
                    durable.conversion().continuingBanker()
                            ? economy.commitGeneratedBankerConversion(durable.conversion())
                            : economy.retireGeneratedBankerConversion(durable.conversion());
            if (!finished.accepted()) {
                return false;
            }
            PENDING_BANKER_DEATHS.remove(regionKey, prepared.rootCanonicalId());
            if (prepared.continuingBanker()) {
                BankerAccess.clearBankerConversionMarker(
                        converted, prepared.rootCanonicalId());
            } else {
                BankerAccess.clearBankerLineage(converted);
            }
            retireBankerConversionTracking(handoff);
            return true;
        });
    }

    private static boolean ensureConvertedBankerSaved(
            MinecraftServer server,
            Entity converted,
            BankerConversionRetryQueue.Handoff handoff) {
        return converted.level() instanceof ServerLevel
                && converted.getUUID().equals(handoff.convertedId())
                && flushAllBankEntitiesAndChunks(server);
    }

    /**
     * Entity saving can synchronously finish pending chunk loads and invoke entity-load hooks.
     * Re-read every exact participant and the durable CAS tuple before recording a world outcome.
     */
    private static boolean revalidateBankerConversionAfterEntitySave(
            MinecraftServer server,
            EconomyService economy,
            EconomyService.GeneratedBankerConversion expected,
            boolean expectedRootLive,
            boolean expectedSourceLive,
            boolean expectedTargetLive) {
        boolean valid = expected.equals(economy.pendingGeneratedBankerConversion(
                        expected.regionKey()))
                && !AMBIGUOUS_BANKER_CONVERSION_REGIONS.contains(expected.regionKey());
        Entity root = findLoadedEntity(
                server, expected.rootCanonicalId(), expected.regionKey());
        Entity source = expected.immediateSourceId().equals(expected.rootCanonicalId())
                ? root
                : findLoadedEntity(
                        server, expected.immediateSourceId(), expected.regionKey());
        Entity target = findLoadedEntity(
                server, expected.targetId(), expected.regionKey());
        valid = valid
                && !AMBIGUOUS_BANKER_CONVERSION_REGIONS.contains(expected.regionKey())
                && !isPendingBankerDeathAnimation(root)
                && !isPendingBankerDeathAnimation(source)
                && !isPendingBankerDeathAnimation(target)
                && isLiveBankerConversionEntity(root) == expectedRootLive
                && isLiveBankerConversionEntity(source) == expectedSourceLive
                && isLiveBankerConversionEntity(target) == expectedTargetLive;
        if (!valid) {
            // A world save can synchronously load a rollback participant. Never reuse the old
            // callback/save proof after that contradiction later unloads again.
            CALLBACK_BANKER_CONVERSION_OUTCOMES.remove(expected.targetId());
        }
        return valid;
    }

    private static boolean isLiveBankerConversionEntity(Entity entity) {
        return entity != null && !entity.isRemoved() && entity.isAlive();
    }

    private static boolean isPendingBankerDeathAnimation(Entity entity) {
        return entity != null && !entity.isRemoved() && !entity.isAlive();
    }

    private static void retireBankerConversionTracking(
            BankerConversionRetryQueue.Handoff handoff) {
        CALLBACK_BANKER_CONVERSION_OUTCOMES.remove(handoff.convertedId());
        DURABLY_REMOVED_BANKER_CONVERSION_ENTITIES.remove(handoff.convertedId());
        DURABLY_REMOVED_BANKER_CONVERSION_ENTITIES.remove(handoff.sourceId());
        DURABLY_REMOVED_BANKER_CONVERSION_ENTITIES.remove(handoff.previousId());
    }

    private static boolean prepareConvertedVillager(
            Villager villager, long regionKey, EconomyService economy) {
        Long packedAnchor = economy.generatedBankAnchor(regionKey);
        BlockPos exchangeDesk = null;
        ServerLevel bankerLevel = villager.level() instanceof ServerLevel serverLevel
                ? serverLevel : null;
        boolean inOverworld = bankerLevel != null
                && bankerLevel == bankerLevel.getServer().overworld();
        if (packedAnchor != null
                && !economy.isFallbackBankRegion(regionKey)
                && inOverworld) {
            BlockPos counter = BlockPos.of(packedAnchor).north();
            if (isLoaded(bankerLevel, counter)
                    && BankerProfessionSupport.isExchangeDesk(
                            bankerLevel.getBlockState(counter))) {
                exchangeDesk = counter;
            }
        }
        if (!BankerAccess.markBanker(villager, regionKey, exchangeDesk)) {
            return false;
        }
        if (packedAnchor != null && inOverworld) {
            villager.setHomeTo(
                    BlockPos.of(packedAnchor),
                    EmeraldConfig.current().bankerRestrictionRadius());
        }
        return true;
    }

    private static Entity findLoadedEntity(
            MinecraftServer server, UUID entityId, long regionKey) {
        Entity match = null;
        for (ServerLevel candidateLevel : server.getAllLevels()) {
            Entity entity = candidateLevel.getEntity(entityId);
            if (entity != null) {
                if (match != null && match != entity) {
                    AMBIGUOUS_BANKER_CONVERSION_REGIONS.add(regionKey);
                    return null;
                }
                match = entity;
            }
        }
        return match;
    }

    private static void retryPendingBankerDeathSaveBarriers(
            MinecraftServer server, EconomyService economy) {
        int attempts = Math.min(
                BANKER_CONVERSION_RETRIES_PER_TICK,
                PENDING_BANKER_DEATH_SAVE_BARRIERS.size());
        for (int index = 0; index < attempts; index++) {
            Map.Entry<UUID, BankerDeathSaveBarrier> entry =
                    PENDING_BANKER_DEATH_SAVE_BARRIERS.entrySet().iterator().next();
            BankerDeathSaveBarrier barrier = entry.getValue();
            Entity observed = barrier.entity();
            if (observed.isAlive() && !observed.isRemoved()) {
                // A mod revived the entity or a cancellable death never completed.
                PENDING_BANKER_DEATH_SAVE_BARRIERS.remove(
                        barrier.observedEntityId(), barrier);
                continue;
            }
            if (!observed.isRemoved()) {
                // A zero-health entity remains revivable during vanilla's death animation. It is
                // not replacement authority until Minecraft has actually removed it.
                if (PENDING_BANKER_DEATH_SAVE_BARRIERS.remove(
                        barrier.observedEntityId(), barrier)) {
                    PENDING_BANKER_DEATH_SAVE_BARRIERS.put(
                            barrier.observedEntityId(), barrier);
                }
                continue;
            }
            if (barrier.level().getServer() != server
                    || !flushBankEntitiesAndChunks(barrier.level())) {
                if (PENDING_BANKER_DEATH_SAVE_BARRIERS.remove(
                        barrier.observedEntityId(), barrier)) {
                    PENDING_BANKER_DEATH_SAVE_BARRIERS.put(
                            barrier.observedEntityId(), barrier);
                }
                continue;
            }
            PENDING_BANKER_DEATH_SAVE_BARRIERS.remove(
                    barrier.observedEntityId(), barrier);
            finishDurablySavedBankerRemoval(barrier, economy);
        }
    }

    private static boolean finishDurablySavedBankerRemoval(
            BankerDeathSaveBarrier barrier, EconomyService economy) {
        EconomyService.GeneratedBankerConversion conversion =
                economy.pendingGeneratedBankerConversion(barrier.regionKey());
        if (conversion != null
                && conversion.rootCanonicalId().equals(barrier.canonicalRootId())
                && (barrier.observedEntityId().equals(conversion.rootCanonicalId())
                        || barrier.observedEntityId().equals(
                                conversion.immediateSourceId())
                        || barrier.observedEntityId().equals(conversion.targetId()))) {
            DURABLY_REMOVED_BANKER_CONVERSION_ENTITIES.add(
                    barrier.observedEntityId());
            // Durable conversion intent globally blocks replacement. Its retry path chooses
            // target, immediate source, or retirement from the exact saved outcome.
            return true;
        }
        if (!economy.recordGeneratedBankerDeath(
                barrier.regionKey(), barrier.canonicalRootId())) {
            PENDING_BANKER_DEATHS.offer(
                    barrier.regionKey(), barrier.canonicalRootId());
            return false;
        }
        PENDING_BANKER_DEATHS.remove(
                barrier.regionKey(), barrier.canonicalRootId());
        return true;
    }

    private static void retryPendingBankerDeaths(EconomyService economy) {
        PENDING_BANKER_DEATHS.retry((regionKey, bankerId) -> {
            UUID current = economy.generatedBankerId(regionKey);
            return current == null
                    || !current.equals(bankerId)
                    || economy.recordGeneratedBankerDeath(regionKey, bankerId);
        });
    }

    /** Classifies generated counters and player-placed Exchange Desks without losing fallback access. */
    public static BankDeskAccess bankDeskAccess(
            ServerLevel level, BlockPos clicked, EconomyService economy) {
        BlockState clickedState = level.getBlockState(clicked);
        boolean workstation = BankerProfessionSupport.isBankWorkstation(clickedState);
        boolean exchangeDesk = BankerProfessionSupport.isExchangeDesk(clickedState);
        if (!workstation) {
            return new BankDeskAccess(
                    null, BankWorkstationAccessPolicy.Decision.IGNORE, null);
        }
        Long activeRegionKey = null;
        BlockPos activeAnchor = null;
        boolean retiredCounter = false;
        if (level == level.getServer().overworld()) {
            for (Map.Entry<Long, Long> entry
                    : economy.generatedBankAnchorsSnapshot().entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) {
                    continue;
                }
                BlockPos anchor = BlockPos.of(entry.getValue());
                if (anchor.north().equals(clicked)) {
                    activeRegionKey = entry.getKey();
                    activeAnchor = anchor;
                    break;
                }
            }
            for (List<Long> anchors : economy.retiredBankAnchorsSnapshot().values()) {
                if (anchors == null) {
                    continue;
                }
                for (Long packed : anchors) {
                    if (packed != null && BlockPos.of(packed).north().equals(clicked)) {
                        retiredCounter = true;
                        break;
                    }
                }
                if (retiredCounter) {
                    break;
                }
            }
        }

        boolean activeOperational = activeRegionKey != null
                && !retiredCounter
                && isManagedBankOperational(level, economy, activeRegionKey);
        BankWorkstationAccessPolicy.Decision decision =
                BankWorkstationAccessPolicy.decide(
                        workstation,
                        exchangeDesk,
                        activeRegionKey != null,
                        activeOperational,
                        retiredCounter);
        BlockPos accessPoint = switch (decision) {
            case MANAGED -> activeAnchor;
            case PERSONAL, PERSONAL_UNSAFE_BANK -> clicked;
            default -> null;
        };
        return new BankDeskAccess(accessPoint, decision, retiredCounter ? null : activeRegionKey);
    }

    /** Compatibility accessor for callers that only need the resolved menu position. */
    public static BlockPos bankAccessPoint(
            ServerLevel level, BlockPos clicked, EconomyService economy) {
        return bankDeskAccess(level, clicked, economy).accessPoint();
    }

    /** True when a scoped authored Bank is fully intact and its anchor has not been retired. */
    public static boolean isManagedBankOperational(
            ServerLevel level, EconomyService economy, long regionKey) {
        return isManagedBankOperational(level, economy, regionKey, null);
    }

    /** Also scopes a Banker entity to the currently active authored Bank after relocation. */
    public static boolean isManagedBankOperational(
            ServerLevel level,
            EconomyService economy,
            long regionKey,
            BlockPos accessPosition) {
        if (level != level.getServer().overworld()) {
            return false;
        }
        int accessRadius = Math.max(
                8, EmeraldConfig.current().bankerRestrictionRadius() + 3);
        Long packedAssignedAnchor = economy.generatedBankerAssignedAnchor(regionKey);
        if (accessPosition != null) {
            if (packedAssignedAnchor == null
                    || accessPosition.distSqr(BlockPos.of(packedAssignedAnchor))
                            > (double) accessRadius * accessRadius) {
                return false;
            }
        }
        int structureVersion = economy.generatedBankStructureVersion(regionKey);
        if (economy.isFallbackBankRegion(regionKey)
                || structureVersion < LEGACY_BANK_STRUCTURE_VERSION) {
            return packedAssignedAnchor != null;
        }
        Long packedAnchor = economy.generatedBankAnchor(regionKey);
        if (packedAnchor == null
                || economy.retiredBankAnchors(regionKey).contains(packedAnchor)) {
            return false;
        }
        BlockPos anchor = BlockPos.of(packedAnchor);
        if (accessPosition != null
                && accessPosition.distSqr(anchor) > (double) accessRadius * accessRadius) {
            return false;
        }
        BankIntegrity integrity = inspectManagedBankIntegrity(
                level, anchor, structureVersion);
        return integrity.complete()
                && integrity.decision()
                        == VillageMaterializationPolicy.IntegrityDecision.INTACT;
    }

    /**
     * Resolves a persistent bank key without assigning two known villages to one legacy grid bank.
     * Existing alpha-era associations always win. A second stable village in the same grid region
     * receives a deterministic UUID-derived key instead of generating a duplicate for the first.
     */
    public static long bankKeyForVillage(
            EconomyService economy,
            String dimensionKey,
            BlockPos center,
            UUID preferredVillageId,
            int regionSize) {
        long legacyKey = regionKey(center, regionSize);
        UUID villageId = preferredVillageId;
        if (villageId == null) {
            EconomyService.VillageSnapshot nearby = economy.nearestVillageSnapshot(
                    dimensionKey, center.asLong(), 48.0);
            villageId = nearby == null ? null : nearby.village().villageId;
        }
        if (villageId == null) {
            return legacyKey;
        }

        UUID stableVillageId = villageId;
        Long pending = economy.pendingBankConstructionsSnapshot().entrySet().stream()
                .filter(e -> stableVillageId.equals(e.getValue().villageId())).map(Map.Entry::getKey)
                .min(Long::compareUnsigned).orElse(null);
        if (pending != null) return pending;
        Long existing = economy.generatedBankAnchorsSnapshot().keySet().stream()
                .filter(key -> stableVillageId.equals(economy.villageIdForBankRegion(key)))
                .min(Long::compareUnsigned)
                .orElse(null);
        if (existing != null) {
            return existing;
        }

        if (!economy.hasGeneratedBankRegion(legacyKey)
                && !economy.pendingBankConstructionsSnapshot().containsKey(legacyKey)) {
            return legacyKey;
        }
        UUID legacyVillage = economy.villageIdForBankRegion(legacyKey);
        if (!economy.pendingBankConstructionsSnapshot().containsKey(legacyKey)
                && (legacyVillage == null || legacyVillage.equals(villageId))) {
            return legacyKey;
        }

        long identity = villageId.getMostSignificantBits()
                ^ Long.rotateLeft(villageId.getLeastSignificantBits(), 23)
                ^ 0x42414E4B5F4B4559L;
        // mix64 is a permutation and the odd increment walks the full long domain. The persisted
        // region set is finite, so this always reaches an unused key without falling back to a
        // legacy key that belongs to a different village.
        for (long attempt = 0L; ; attempt++) {
            long candidate = mix64(identity + attempt * 0x9E3779B97F4A7C15L);
            if (!economy.pendingBankConstructionsSnapshot().containsKey(candidate)
                    && (!economy.hasGeneratedBankRegion(candidate)
                    || villageId.equals(economy.villageIdForBankRegion(candidate)))) {
                return candidate;
            }
        }
    }

    private static BankPlotSearch findBankPlots(
            ServerLevel level,
            EconomyService economy,
            BlockPos villagePosition,
            long regionKey,
            List<Long> excludedPackedAnchors,
            boolean recoverySearch) {
        List<BankPlotCandidate> candidates = new ArrayList<>();
        List<EconomyService.VillageProjectLot> excludedProjectLots =
                economy.villageProjectLotExclusions("minecraft:overworld");
        List<Long> excludedBanks = new ArrayList<>();
        excludedBanks.addAll(economy.generatedBankAnchorsSnapshot().values());
        excludedBanks.addAll(pendingBankAnchors(economy));
        economy.retiredBankAnchorsSnapshot().values().forEach(excludedBanks::addAll);
        if (excludedPackedAnchors != null) {
            excludedBanks.addAll(excludedPackedAnchors);
        }
        boolean complete = true;
        for (VillageBankPlacementPolicy.SiteOffset offset :
                VillageBankPlacementPolicy.candidateSiteOffsets(recoverySearch)) {
            int centerX = villagePosition.getX() + offset.x();
            int centerZ = villagePosition.getZ() + offset.z();
            if (!isBankPlotAreaLoaded(level, centerX, centerZ)) {
                complete = false;
                continue;
            }
            BlockPos origin = safeOrigin(level, centerX, centerZ);
            BlockPos bankerAnchor = origin == null
                    ? null
                    : origin.offset(BANK_WIDTH / 2, 1, BANK_DEPTH - 2);
            if (origin != null
                    && farEnoughFromRetiredBanks(bankerAnchor, excludedBanks)
                    && !overlapsProjectLot(origin, excludedProjectLots)) {
                candidates.add(new BankPlotCandidate(
                        origin,
                        VillageBankPlacementPolicy.candidatePriority(
                                isOutsideVillageArea(level, origin),
                                centerX - villagePosition.getX(),
                                centerZ - villagePosition.getZ(),
                                regionKey)));
            }
        }
        candidates.sort(Comparator.comparingInt((BankPlotCandidate c) -> {
            var profiles = sampleNaturalEntranceProfiles(level, c.origin());
            return profiles == null ? Integer.MAX_VALUE : profiles.stream().flatMap(List::stream)
                    .mapToInt(h -> Math.abs(h)).sum();
        }).thenComparing(BankPlotCandidate::priority));
        return new BankPlotSearch(
                candidates.stream().map(BankPlotCandidate::origin).toList(), complete);
    }

    /** True only when the complete Bank lot and approach sample outside vanilla POI influence. */
    private static boolean isOutsideVillageArea(ServerLevel level, BlockPos origin) {
        int middleX = BANK_WIDTH / 2;
        int middleZ = BANK_DEPTH / 2;
        int[] xOffsets = {BANK_PLOT_MIN_X, middleX, BANK_PLOT_MAX_X};
        int[] zOffsets = {BANK_APPROACH_ARRIVAL_Z, middleZ, BANK_PLOT_MAX_Z};
        for (int x : xOffsets) {
            for (int z : zOffsets) {
                if (level.isVillage(origin.offset(x, 0, z))) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean overlapsProjectLot(
            BlockPos bankOrigin, List<EconomyService.VillageProjectLot> excludedLots) {
        int minimumX = bankOrigin.getX() + BANK_PLOT_MIN_X - 4;
        int maximumX = bankOrigin.getX() + BANK_PLOT_MAX_X + 4;
        int minimumZ = bankOrigin.getZ() + BANK_APPROACH_ARRIVAL_Z - 4;
        int maximumZ = bankOrigin.getZ() + BANK_PLOT_MAX_Z + 4;
        for (EconomyService.VillageProjectLot lot : excludedLots) {
            BlockPos lotMinimum = BlockPos.of(lot.boundsMinPos());
            BlockPos lotMaximum = BlockPos.of(lot.boundsMaxPos());
            if (minimumX <= lotMaximum.getX()
                    && maximumX >= lotMinimum.getX()
                    && minimumZ <= lotMaximum.getZ()
                    && maximumZ >= lotMinimum.getZ()) {
                return true;
            }
        }
        return false;
    }

    private static boolean farEnoughFromRetiredBanks(
            BlockPos candidate, List<Long> retiredPackedAnchors) {
        if (retiredPackedAnchors == null || retiredPackedAnchors.isEmpty()) {
            return true;
        }
        for (Long packed : retiredPackedAnchors) {
            if (packed == null) {
                return false;
            }
            BlockPos retired = BlockPos.of(packed);
            long dx = (long) candidate.getX() - retired.getX();
            long dz = (long) candidate.getZ() - retired.getZ();
            if (dx * dx + dz * dz < 24L * 24L) {
                return false;
            }
        }
        return true;
    }

    /** Horizontal reservation shared with village-project search, independent of terrain Y. */
    static boolean overlapsManagedBankLot(
            long packedBankerAnchor,
            int candidateMinimumX,
            int candidateMaximumX,
            int candidateMinimumZ,
            int candidateMaximumZ,
            int margin) {
        BlockPos bankerAnchor = BlockPos.of(packedBankerAnchor);
        BlockPos origin = bankerAnchor.offset(
                -BANK_WIDTH / 2, -1, -(BANK_DEPTH - 2));
        int bankMinimumX = origin.getX() + BANK_PLOT_MIN_X;
        int bankMaximumX = origin.getX() + BANK_PLOT_MAX_X;
        int bankMinimumZ = origin.getZ() + BANK_APPROACH_ARRIVAL_Z;
        int bankMaximumZ = origin.getZ() + BANK_PLOT_MAX_Z;
        return candidateMinimumX - margin <= bankMaximumX + margin
                && candidateMaximumX + margin >= bankMinimumX - margin
                && candidateMinimumZ - margin <= bankMaximumZ + margin
                && candidateMaximumZ + margin >= bankMinimumZ - margin;
    }

    private static boolean isBankPlotAreaLoaded(
            ServerLevel level, int centerX, int centerZ) {
        int originX = centerX - BANK_WIDTH / 2;
        int originZ = centerZ - BANK_DEPTH / 2;
        int minimumX = originX + BANK_PLOT_MIN_X;
        int maximumX = originX + BANK_PLOT_MAX_X;
        int minimumZ = originZ + BANK_APPROACH_ARRIVAL_Z;
        int maximumZ = originZ + BANK_PLOT_MAX_Z;
        for (int chunkX = Math.floorDiv(minimumX, 16);
                chunkX <= Math.floorDiv(maximumX, 16);
                chunkX++) {
            for (int chunkZ = Math.floorDiv(minimumZ, 16);
                    chunkZ <= Math.floorDiv(maximumZ, 16);
                    chunkZ++) {
                if (!level.hasChunk(chunkX, chunkZ)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static BlockPos safeOrigin(ServerLevel level, int centerX, int centerZ) {
        int originX = centerX - BANK_WIDTH / 2;
        int originZ = centerZ - BANK_DEPTH / 2;
        int minimumSurface = Integer.MAX_VALUE;
        int maximumSurface = Integer.MIN_VALUE;
        var survey = new VillageSitePreparation.Survey(level);
        List<Integer> surfaces = new ArrayList<>();
        for (int x = originX + BANK_PLOT_MIN_X; x <= originX + BANK_PLOT_MAX_X; x++) {
            for (int z = originZ + BANK_PLOT_MIN_Z; z <= originZ + BANK_PLOT_MAX_Z; z++) {
                if (!level.hasChunk(Math.floorDiv(x, 16), Math.floorDiv(z, 16))) {
                    return null;
                }
                Integer surface = survey.surface(x, z);
                if (surface == null) return null;
                surfaces.add(surface);
                minimumSurface = Math.min(minimumSurface, surface);
                maximumSurface = Math.max(maximumSurface, surface);
            }
        }
        var floor = TerrainFoundationPlan.levelledFloor(surfaces);
        if (floor.isEmpty()) return null;
        int floorY = floor.getAsInt();
        // Prefer a level north approach; cutting the back of a hill must not bury the entrance.
        for (int x = BANK_WIDTH / 2 - BANK_ENTRANCE_HALF_WIDTH;
                x <= BANK_WIDTH / 2 + BANK_ENTRANCE_HALF_WIDTH; x++) {
            Integer approach = survey.surface(originX + x, originZ - 3);
            if (approach == null) return null;
            floorY = Math.max(floorY, approach);
        }
        if (floorY > minimumSurface + TerrainFoundationPlan.MAX_TERRAIN_DROP) return null;

        // Cut/fill only new lots. The authored Bank and its legacy foundation geometry stay unchanged.
        BlockPos origin = new BlockPos(
                originX,
                floorY,
                originZ);
        List<List<Integer>> entranceProfiles = sampleNaturalEntranceProfiles(level, origin);
        if (entranceProfiles == null
                || VillageBankPlacementPolicy.planWideEntranceSteps(
                                entranceProfiles, TerrainFoundationPlan.MAX_TERRAIN_DROP)
                        .isEmpty()) {
            return null;
        }
        for (int x = BANK_PLOT_MIN_X; x <= BANK_PLOT_MAX_X; x++) {
            for (int z = BANK_PLOT_MIN_Z; z <= BANK_PLOT_MAX_Z; z++) {
                for (int y = 0; y <= BANK_HEIGHT; y++) {
                    BlockPos position = origin.offset(x, y, z);
                    if (!survey.available(position, floorY)) {
                        return null;
                    }
                }
            }
        }
        return origin;
    }

    /** Samples each lane independently so a cross-slope cannot leave part of a wide stair afloat. */
    private static List<List<Integer>> sampleNaturalEntranceProfiles(
            ServerLevel level, BlockPos origin) {
        var survey = new VillageSitePreparation.Survey(level);
        List<List<Integer>> profiles = new ArrayList<>(BANK_ENTRANCE_HALF_WIDTH * 2 + 1);
        for (int lane = -BANK_ENTRANCE_HALF_WIDTH;
                lane <= BANK_ENTRANCE_HALF_WIDTH;
                lane++) {
            profiles.add(new ArrayList<>(TerrainFoundationPlan.MAX_TERRAIN_DROP + 1));
        }
        int centerX = BANK_WIDTH / 2;
        for (int z = -3; z >= BANK_APPROACH_ARRIVAL_Z; z--) {
            int laneIndex = 0;
            for (int x = centerX - BANK_ENTRANCE_HALF_WIDTH;
                    x <= centerX + BANK_ENTRANCE_HALF_WIDTH;
                    x++) {
                int worldX = origin.getX() + x;
                int worldZ = origin.getZ() + z;
                if (!level.hasChunk(Math.floorDiv(worldX, 16), Math.floorDiv(worldZ, 16))) {
                    return null;
                }
                Integer surface = survey.surface(worldX, worldZ);
                if (surface == null) return null;
                int offset = surface - origin.getY();
                if (offset < -TerrainFoundationPlan.MAX_TERRAIN_DROP || offset > 0) {
                    return null;
                }
                profiles.get(laneIndex++).add(offset);
            }
        }
        return profiles.stream().map(List::copyOf).toList();
    }

    static boolean isNaturalBankGround(BlockState state) {
        return state.is(BlockTags.DIRT)
                || state.is(Blocks.GRASS_BLOCK)
                || state.is(Blocks.PODZOL)
                || state.is(Blocks.MYCELIUM)
                || state.is(Blocks.SAND)
                || state.is(Blocks.RED_SAND)
                || state.is(Blocks.STONE)
                || state.is(Blocks.ANDESITE)
                || state.is(Blocks.DIORITE)
                || state.is(Blocks.GRANITE)
                || state.is(Blocks.GRAVEL)
                || state.is(Blocks.DEEPSLATE)
                || state.is(Blocks.TUFF)
                || state.is(Blocks.CALCITE)
                || state.is(Blocks.SNOW_BLOCK);
    }

    private static boolean isLoaded(ServerLevel level, BlockPos position) {
        return level.hasChunk(
                Math.floorDiv(position.getX(), 16), Math.floorDiv(position.getZ(), 16));
    }

    /** Recognizes legacy barrels or the new non-job-site cabinetry without placing either. */
    private static boolean hasBankCounterFrame(ServerLevel level, BlockPos bankerAnchor) {
        BlockPos counter = bankerAnchor.north();
        if (!isLoaded(level, counter.west()) || !isLoaded(level, counter.east())) {
            return false;
        }
        BlockState west = level.getBlockState(counter.west());
        BlockState east = level.getBlockState(counter.east());
        return (west.is(Blocks.BARREL) && east.is(Blocks.BARREL))
                || (west.is(Blocks.CHISELED_BOOKSHELF)
                        && east.is(Blocks.CHISELED_BOOKSHELF));
    }

    /** Exact reserved bell cells, including old sites; independent of whether the bell was broken. */
    static Map<Long, Long> generatedBankBellRegions(EconomyService economy) {
        Map<Long, Long> bells = new HashMap<>();
        economy.generatedBankAnchorsSnapshot().forEach((key, anchor) -> {
            int version = economy.generatedBankStructureVersion(key);
            if (version < LEGACY_BANK_STRUCTURE_VERSION || economy.isFallbackBankRegion(key)) return;
            addBankBells(bells, key, BlockPos.of(anchor), version);
            // Old versions may have occupied either forecourt when the Bank was retired.
            for (long retired : economy.retiredBankAnchors(key)) {
                addBankBells(bells, key, BlockPos.of(retired), 2);
                addBankBells(bells, key, BlockPos.of(retired), version);
            }
        });
        economy.pendingBankConstructionsSnapshot().forEach((key, plan) ->
                addBankBells(bells, key, BlockPos.of(plan.origin()).offset(
                        BANK_WIDTH / 2, 1, BANK_DEPTH - 2), BANK_STRUCTURE_VERSION));
        return bells;
    }

    private static void addBankBells(Map<Long, Long> bells, long key, BlockPos anchor, int version) {
        BlockPos origin = anchor.offset(-BANK_WIDTH / 2, -1, -(BANK_DEPTH - 2));
        bells.put(origin.offset(2, 2, version >= 5 ? -3 : -1).asLong(), key);
        if (version >= 4) bells.put(origin.offset(BANK_WIDTH / 2, 9, BANK_DEPTH / 2).asLong(), key);
    }

    /** At most eight loaded candidates and one durable repair per discovery pass; no chunk loads. */
    private static void reconcileBankBellVillages(ServerLevel level, EconomyService economy, long pass) {
        var bells = new ArrayList<>(generatedBankBellRegions(economy).entrySet());
        bells.sort(Map.Entry.comparingByKey());
        for (int i = 0; i < Math.min(8, bells.size()); i++) {
            var bell = bells.get((int) Math.floorMod(pass * 8 + i, bells.size()));
            BlockPos position = BlockPos.of(bell.getKey());
            if (!isLoaded(level, position) || level.players().stream()
                    .noneMatch(p -> p.blockPosition().distSqr(position) <= 256.0 * 256.0)) continue;
            UUID id = economy.villageIdAt("minecraft:overworld", bell.getKey());
            if (id == null || id.equals(economy.villageIdForBankRegion(bell.getValue()))) continue;
            if (economy.reconcileEmptyBankBellVillage(id, bell.getValue(), bell.getKey())) {
                System.out.println("[The Emerald Standard] Reconciled empty Bank-bell village "
                        + id + " into " + economy.villageIdForBankRegion(bell.getValue())
                        + "; structures and player accounts unchanged.");
                break;
            }
        }
    }

    /** Inspects the authored Bank without changing or force-loading any world state. */
    private static BankIntegrity inspectManagedBankIntegrity(
            ServerLevel level, BlockPos bankerAnchor, int structureVersion) {
        BlockPos origin = bankerAnchor.offset(
                -BANK_WIDTH / 2, -1, -(BANK_DEPTH - 2));
        // Prove the entire horizontal footprint loaded before the biome/palette lookup or any
        // block-state read. Dashboard clicks and distant Bankers must never force-load a Bank.
        if (!isBankPlotAreaLoaded(
                level,
                origin.getX() + BANK_WIDTH / 2,
                origin.getZ() + BANK_DEPTH / 2)) {
            return new BankIntegrity(
                    false, VillageMaterializationPolicy.IntegrityDecision.UNSAFE, "Bank chunks are not loaded; inspection deferred.");
        }
        BankPalette palette = paletteFor(level, origin);
        Map<BlockPos, BlockState> authored = new HashMap<>();
        List<BankPlacement> expectedPlan = structureVersion >= BANK_STRUCTURE_VERSION
                ? bankPlan(origin, palette)
                : structureVersion >= PREVIOUS_BANK_STRUCTURE_VERSION_V10
                        ? legacyBankPlanV10(origin, palette)
                : structureVersion >= PREVIOUS_BANK_STRUCTURE_VERSION_V9
                        ? legacyBankPlanV9(origin, palette)
                : structureVersion >= PREVIOUS_BANK_STRUCTURE_VERSION_V8
                ? legacyBankPlanV8(origin, palette)
                : structureVersion >= PREVIOUS_BANK_STRUCTURE_VERSION_V7
                        ? legacyBankPlanV7(origin, palette)
                : structureVersion >= PREVIOUS_BANK_STRUCTURE_VERSION_V6
                        ? legacyBankPlanV6(origin, palette)
                : structureVersion >= PREVIOUS_BANK_STRUCTURE_VERSION_V5
                        ? legacyBankPlanV5(origin, palette)
                : structureVersion >= PREVIOUS_BANK_STRUCTURE_VERSION_V4
                        ? legacyBankPlanV4(origin, palette)
                : structureVersion >= PREVIOUS_BANK_STRUCTURE_VERSION
                        ? legacyBankPlanV3(origin, palette)
                        : legacyBankPlanV2(origin, palette);
        for (BankPlacement placement : expectedPlan) {
            authored.put(placement.position(), placement.state());
        }
        boolean approachValid = appendBankApproachIntegrityPlan(level, origin, palette, authored);
        int mismatches = 0;
        String problem = approachValid ? "" : "Entrance approach has missing support or blocked access.";
        if (!hasBankCounterFrame(level, bankerAnchor)) problem = "The Bank counter cabinetry is missing.";
        for (Map.Entry<BlockPos, BlockState> entry : authored.entrySet()) {
            BlockState current = level.getBlockState(entry.getKey());
            BlockState expected = entry.getValue();
            boolean compatibleLegacyCounter = expected.is(Blocks.CHISELED_BOOKSHELF)
                    && current.is(Blocks.BARREL);
            boolean compatibleWorkstation = BankerProfessionSupport.isBankWorkstation(expected)
                    && BankerProfessionSupport.isBankWorkstation(current);
            boolean compatibleClearance = expected.isAir()
                    && VillageBankPlacementPolicy.preservesOutdoorClearance(
                            current.isAir(),
                            current.canBeReplaced(),
                            level.getFluidState(entry.getKey()).isEmpty(),
                            level.getBlockEntity(entry.getKey()) != null);
            boolean sameMaterial = current.is(expected.getBlock());
            if (!sameMaterial
                    && !compatibleLegacyCounter
                    && !compatibleWorkstation
                    && !compatibleClearance
                    && !expected.isAir()
                    && (current.canBeReplaced() || current.isAir())) {
                mismatches++;
            }
            int x = entry.getKey().getX() - origin.getX();
            int y = entry.getKey().getY() - origin.getY();
            int z = entry.getKey().getZ() - origin.getZ();
            boolean centralAccess = x == BANK_WIDTH / 2 && z >= -2 && z <= BANK_DEPTH - 4
                    && y >= 1 && y <= 2;
            boolean floor = y == 0 && x >= 0 && x < BANK_WIDTH && z >= 0 && z < BANK_DEPTH;
            String failure = "";
            if (BankerProfessionSupport.isBankWorkstation(expected) && !compatibleWorkstation) {
                failure = "Bank workstation is missing";
            } else if (expected.getBlock() instanceof DoorBlock
                    && (!(current.getBlock() instanceof DoorBlock)
                            || current.getValue(DoorBlock.HALF) != expected.getValue(DoorBlock.HALF)
                            || current.is(Blocks.IRON_DOOR))) {
                failure = "Entrance needs a usable wooden door";
            } else if (centralAccess && expected.isAir() && !compatibleClearance
                    && !current.getCollisionShape(level, entry.getKey()).isEmpty()) {
                failure = "Entrance/lobby access is obstructed";
            } else if (floor && current.getCollisionShape(level, entry.getKey()).isEmpty()) {
                failure = "Bank floor has a hole";
            }
            if ((floor || centralAccess) && (!level.getFluidState(entry.getKey()).isEmpty()
                    || current.is(Blocks.FIRE) || current.is(Blocks.SOUL_FIRE)
                    || current.is(Blocks.MAGMA_BLOCK) || current.is(Blocks.CACTUS)
                    || current.getBlock() instanceof net.minecraft.world.level.block.CampfireBlock)) {
                failure = "Bank access contains a hazard";
            }
            if (!failure.isEmpty() && problem.isEmpty())
                problem = failure + " at " + entry.getKey().toShortString() + ".";
        }
        for (int z = 0; z <= BANK_DEPTH - 4 && problem.isEmpty(); z++) {
            for (int y = 1; y <= 2; y++) {
                BlockPos cell = origin.offset(BANK_WIDTH / 2, y, z);
                BlockState state = level.getBlockState(cell);
                var collision = state.getCollisionShape(level, cell);
                boolean thinFloorCovering = y == 1 && !collision.isEmpty()
                        && collision.max(Direction.Axis.Y) <= 0.125;
                if (!(state.getBlock() instanceof DoorBlock) && !collision.isEmpty() && !thinFloorCovering) {
                    problem = "Entrance/lobby access is obstructed at " + cell.toShortString() + ".";
                    break;
                }
            }
        }
        var demolition = VillageMaterializationPolicy.assessIntegrity(authored.size(), mismatches);
        if (demolition == VillageMaterializationPolicy.IntegrityDecision.RELOCATE)
            return new BankIntegrity(true, demolition, "Large portions of the Bank are missing.");
        if (problem.isEmpty() && mismatches >= 12)
            problem = "Bank shell is damaged (" + mismatches + " missing authored blocks).";
        return new BankIntegrity(true, problem.isEmpty()
                ? VillageMaterializationPolicy.IntegrityDecision.INTACT
                : VillageMaterializationPolicy.IntegrityDecision.UNSAFE, problem);
    }

    public static String bankOperationProblem(ServerLevel level, EconomyService economy, Long key) {
        if (key == null) return "";
        Long anchor = economy.generatedBankAnchor(key);
        return anchor == null ? "No assigned Bank structure."
                : inspectManagedBankIntegrity(level, BlockPos.of(anchor),
                        economy.generatedBankStructureVersion(key)).problem();
    }

    /** Reconstructs terrain-dependent authored stairs/supports without changing the world. */
    private static boolean appendBankApproachIntegrityPlan(
            ServerLevel level,
            BlockPos origin,
            BankPalette palette,
            Map<BlockPos, BlockState> authored) {
        List<List<Integer>> profiles = sampleExistingEntranceProfiles(level, origin, palette);
        Optional<List<VillageBankPlacementPolicy.EntranceStep>> planned = profiles == null
                ? Optional.empty()
                : VillageBankPlacementPolicy.planWideEntranceSteps(
                        profiles, TerrainFoundationPlan.MAX_TERRAIN_DROP);
        if (planned.isEmpty()) {
            return false;
        }

        BlockState stair = palette.stairs().defaultBlockState()
                .setValue(StairBlock.FACING, Direction.SOUTH);
        int centerX = BANK_WIDTH / 2;
        for (VillageBankPlacementPolicy.EntranceStep step : planned.orElseThrow()) {
            for (int x = centerX - BANK_ENTRANCE_HALF_WIDTH;
                    x <= centerX + BANK_ENTRANCE_HALF_WIDTH;
                    x++) {
                BlockPos stairPosition = origin.offset(x, step.yOffset(), step.zOffset());
                authored.put(stairPosition, stair);
                authored.putIfAbsent(stairPosition.above(), Blocks.AIR.defaultBlockState());
                authored.putIfAbsent(stairPosition.above(2), Blocks.AIR.defaultBlockState());
            }
        }

        List<TerrainFoundationPlan.Cell> authoredCells = authored.entrySet().stream()
                .filter(entry -> !entry.getValue().isAir())
                .map(entry -> entry.getKey().subtract(origin))
                .map(relative -> new TerrainFoundationPlan.Cell(
                        relative.getX(), relative.getY(), relative.getZ()))
                .toList();
        Set<BlockPos> groundedColumns = new HashSet<>();
        BlockState foundation = palette.foundation().defaultBlockState();
        for (TerrainFoundationPlan.Cell support : TerrainFoundationPlan.appendSupportCells(
                authoredCells, TerrainFoundationPlan.MAX_TERRAIN_DROP)) {
            BlockPos column = new BlockPos(support.x(), 0, support.z());
            if (groundedColumns.contains(column)) {
                continue;
            }
            BlockPos target = origin.offset(support.x(), support.y(), support.z());
            if (isNaturalBankGround(level.getBlockState(target))) {
                groundedColumns.add(column);
                continue;
            }
            authored.putIfAbsent(target, foundation);
        }
        return true;
    }

    /** Ignores neighbor-driven shapes while enforcing authored orientation and support states. */
    private static boolean bankStructuralStateMatches(BlockState current, BlockState expected) {
        if (!current.is(expected.getBlock())) {
            return false;
        }
        if (expected.getBlock() instanceof DoorBlock) {
            return current.getValue(DoorBlock.FACING) == expected.getValue(DoorBlock.FACING)
                    && current.getValue(DoorBlock.HALF) == expected.getValue(DoorBlock.HALF)
                    && current.getValue(DoorBlock.HINGE) == expected.getValue(DoorBlock.HINGE);
        }
        if (expected.getBlock() instanceof StairBlock) {
            return current.getValue(StairBlock.FACING) == expected.getValue(StairBlock.FACING)
                    && current.getValue(StairBlock.HALF) == expected.getValue(StairBlock.HALF);
        }
        if (expected.getBlock() instanceof LanternBlock) {
            return current.getValue(LanternBlock.HANGING)
                    == expected.getValue(LanternBlock.HANGING);
        }
        if (expected.getBlock() instanceof RotatedPillarBlock) {
            return current.getValue(RotatedPillarBlock.AXIS)
                    == expected.getValue(RotatedPillarBlock.AXIS);
        }
        if (expected.getBlock() instanceof SlabBlock) {
            return current.getValue(SlabBlock.TYPE) == expected.getValue(SlabBlock.TYPE);
        }
        // Pane/fence/chest/book states legitimately change from neighbors or contents. Their
        // authored block identity is the structural contract; orientation-sensitive blocks above
        // retain the relevant authored properties.
        return true;
    }

    /** Maintains pane shapes and performs the guarded, one-time entrance upgrade for old Banks. */
    private static void maintainManagedBank(
            ServerLevel level,
            EconomyService economy,
            BlockPos bankerAnchor,
            UUID villageId,
            long bankKey,
            VillageBankPlacementPolicy.UpgradeAttemptGate upgradeGate) {
        BlockPos origin = bankerAnchor.offset(
                -BANK_WIDTH / 2, -1, -(BANK_DEPTH - 2));
        BankPalette palette = paletteFor(level, origin);
        if (!hasManagedBankSignature(level, origin, bankerAnchor, palette)) {
            return;
        }

        normalizeManagedBankPanes(level, origin, villageId, bankKey);
        int structureVersion = economy.generatedBankStructureVersion(bankKey);
        if (structureVersion >= LEGACY_BANK_STRUCTURE_VERSION) {
            // Version-two through version-six Banks remain valid frozen architecture. A player's
            // existing building is never silently reshaped; only new/replacement Banks use v7.
            LAST_BANK_UPGRADE_RETRY_TICK.remove(bankKey);
            return;
        }
        if (upgradeGate.claimed()
                || economy.isFallbackBankRegion(bankKey)
                || !hasLegacyEntranceSignature(level, origin, palette)) {
            return;
        }

        List<List<Integer>> profiles = sampleExistingEntranceProfiles(level, origin, palette);
        Optional<List<VillageBankPlacementPolicy.EntranceStep>> steps = profiles == null
                ? Optional.empty()
                : VillageBankPlacementPolicy.planWideEntranceSteps(
                        profiles, TerrainFoundationPlan.MAX_TERRAIN_DROP);
        if (steps.isEmpty()) {
            return;
        }
        List<BankPlacement> upgrade = bankEntranceUpgradePlan(
                level, origin, palette, steps.orElseThrow());
        if (upgrade == null) {
            return;
        }

        if (level.noSave()) {
            return;
        }
        long gameTime = level.getGameTime();
        Long previousAttempt = LAST_BANK_UPGRADE_RETRY_TICK.get(bankKey);
        if (!VillageBankPlacementPolicy.retryDue(
                gameTime, previousAttempt, BANK_UPGRADE_RETRY_INTERVAL_TICKS)) {
            return;
        }
        List<BankMutation> placed = new ArrayList<>();
        for (BankPlacement placement : upgrade) {
            BlockState existing = level.getBlockState(placement.position());
            if (existing.equals(placement.state())) {
                continue;
            }
            if (!isLoaded(level, placement.position())
                    || (!existing.isAir() && !existing.canBeReplaced())
                    || level.getBlockEntity(placement.position()) != null
                    || !level.getFluidState(placement.position()).isEmpty()
                    || !VillageDevelopmentProtection.mayPlace(
                            level,
                            villageId,
                            bankKey,
                            placement.position(),
                            existing,
                            placement.state())) {
                return;
            }
        }
        if (!upgradeGate.tryClaim(true)) {
            return;
        }
        LAST_BANK_UPGRADE_RETRY_TICK.put(bankKey, gameTime);
        for (BankPlacement placement : upgrade) {
            BlockState original = level.getBlockState(placement.position());
            if (original.equals(placement.state())) {
                continue;
            }
            if (!VillageConstructionOccupancy.mayChange(level,placement.position(),original,placement.state())) {
                rollbackBank(level,placed);
                return;
            }
            boolean changed = level.setBlock(placement.position(), placement.state(), 3);
            BlockState applied = level.getBlockState(placement.position());
            if (!changed || !applied.equals(placement.state())) {
                if (isOwnedBankPlacement(applied, placement.state())) {
                    placed.add(new BankMutation(placement, original));
                }
                rollbackBank(level, placed);
                return;
            }
            placed.add(new BankMutation(placement, original));
        }
        if (!flushBankChunks(level)) {
            rollbackBank(level, placed);
            flushBankChunks(level);
            return;
        }
        if (!economy.completeBankStructureUpgrade(
                bankKey,
                bankerAnchor.asLong(),
                LEGACY_BANK_STRUCTURE_VERSION)) {
            rollbackBank(level, placed);
            flushBankChunks(level);
        } else {
            LAST_BANK_UPGRADE_RETRY_TICK.remove(bankKey);
        }
    }

    /**
     * Waits for Minecraft's synchronous chunk-save barrier before the economy file records its
     * version marker. The remaining cross-file failure limit is documented with the save format.
     */
    private static boolean flushBankChunks(ServerLevel level) {
        if (level.noSave()) {
            return false;
        }
        try {
            level.getChunkSource().save(true);
            return true;
        } catch (RuntimeException failure) {
            return false;
        }
    }

    /** Flushes both chunk blocks and persistent entities before an economy identity commit. */
    private static boolean flushBankEntitiesAndChunks(ServerLevel level) {
        if (level.noSave()) {
            return false;
        }
        try {
            // ServerChunkCache.save(true) does not call the 26.2 entity manager. ServerLevel's
            // flush path does both, which is required before an entity UUID becomes durable
            // authority in the independent economy file.
            level.save(null, true, false);
            return true;
        } catch (RuntimeException failure) {
            return false;
        }
    }

    /**
     * Conversion may cross dimensions before a retry succeeds. A target-durable phase therefore
     * follows entity-inclusive barriers for every active level, not only the target's level.
     */
    private static boolean flushAllBankEntitiesAndChunks(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            if (!flushBankEntitiesAndChunks(level)) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasManagedBankSignature(
            ServerLevel level,
            BlockPos origin,
            BlockPos bankerAnchor,
            BankPalette palette) {
        BlockPos roofLantern = origin.offset(BANK_WIDTH / 2, 6, BANK_DEPTH / 2);
        BlockPos roofMount = roofLantern.above();
        BlockPos lowerDoorPosition = origin.offset(BANK_WIDTH / 2, 1, 0);
        BlockPos upperDoorPosition = lowerDoorPosition.above();
        BlockPos threshold = lowerDoorPosition.below();
        if (!isLoaded(level, roofLantern)
                || !isLoaded(level, roofMount)
                || !isLoaded(level, lowerDoorPosition)
                || !isLoaded(level, upperDoorPosition)
                || !isLoaded(level, threshold)
                || !BankerProfessionSupport.isBankWorkstation(
                        level.getBlockState(bankerAnchor.north()))
                || !hasBankCounterFrame(level, bankerAnchor)
                || !level.getBlockState(roofMount).is(palette.corner())
                || !level.getBlockState(roofLantern).is(Blocks.LANTERN)
                || !level.getBlockState(roofLantern).getValue(LanternBlock.HANGING)
                || !level.getBlockState(threshold).is(palette.foundation())) {
            return false;
        }
        BlockState lowerDoor = level.getBlockState(lowerDoorPosition);
        BlockState upperDoor = level.getBlockState(upperDoorPosition);
        return lowerDoor.is(palette.door())
                && lowerDoor.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER
                && lowerDoor.getValue(DoorBlock.FACING) == Direction.NORTH
                && upperDoor.is(palette.door())
                && upperDoor.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER
                && upperDoor.getValue(DoorBlock.FACING) == Direction.NORTH;
    }

    private static boolean hasLegacyEntranceSignature(
            ServerLevel level, BlockPos origin, BankPalette palette) {
        BlockPos topStair = origin.offset(BANK_WIDTH / 2, 0, -2);
        if (!isLoaded(level, topStair)) {
            return false;
        }
        BlockState state = level.getBlockState(topStair);
        return state.is(palette.stairs())
                && state.getValue(StairBlock.FACING) == Direction.SOUTH;
    }

    /** Finds the original natural surface below any crash-interrupted authored approach cells. */
    private static List<List<Integer>> sampleExistingEntranceProfiles(
            ServerLevel level, BlockPos origin, BankPalette palette) {
        List<List<Integer>> profiles = new ArrayList<>(BANK_ENTRANCE_HALF_WIDTH * 2 + 1);
        for (int lane = -BANK_ENTRANCE_HALF_WIDTH;
                lane <= BANK_ENTRANCE_HALF_WIDTH;
                lane++) {
            profiles.add(new ArrayList<>(TerrainFoundationPlan.MAX_TERRAIN_DROP + 1));
        }
        int centerX = BANK_WIDTH / 2;
        for (int z = -3; z >= BANK_APPROACH_ARRIVAL_Z; z--) {
            int laneIndex = 0;
            for (int x = centerX - BANK_ENTRANCE_HALF_WIDTH;
                    x <= centerX + BANK_ENTRANCE_HALF_WIDTH;
                    x++) {
                Integer offset = findExistingNaturalSurfaceOffset(
                        level, origin.offset(x, 0, z), palette);
                if (offset == null) {
                    return null;
                }
                profiles.get(laneIndex++).add(offset);
            }
        }
        return profiles.stream().map(List::copyOf).toList();
    }

    private static Integer findExistingNaturalSurfaceOffset(
            ServerLevel level, BlockPos column, BankPalette palette) {
        for (int y = 0; y >= -TerrainFoundationPlan.MAX_TERRAIN_DROP - 1; y--) {
            BlockPos position = column.offset(0, y, 0);
            if (!isLoaded(level, position)
                    || level.getBlockEntity(position) != null
                    || !level.getFluidState(position).isEmpty()) {
                return null;
            }
            BlockState state = level.getBlockState(position);
            if (isNaturalBankGround(state)) {
                return y + 1;
            }
            if (state.isAir()
                    || state.canBeReplaced()
                    || state.is(palette.stairs())
                    || state.is(palette.foundation())) {
                continue;
            }
            return null;
        }
        return null;
    }

    private static List<BankPlacement> bankEntranceUpgradePlan(
            ServerLevel level,
            BlockPos origin,
            BankPalette palette,
            List<VillageBankPlacementPolicy.EntranceStep> steps) {
        List<BankPlacement> result = new ArrayList<>();
        int centerX = BANK_WIDTH / 2;
        BlockState foundation = palette.foundation().defaultBlockState();
        BlockState stair = palette.stairs().defaultBlockState()
                .setValue(StairBlock.FACING, Direction.SOUTH);
        for (int x = centerX - BANK_ENTRANCE_HALF_WIDTH;
                x <= centerX + BANK_ENTRANCE_HALF_WIDTH;
                x++) {
            result.add(new BankPlacement(origin.offset(x, 0, -1), foundation));
            for (VillageBankPlacementPolicy.EntranceStep step : steps) {
                result.add(new BankPlacement(
                        origin.offset(x, step.yOffset(), step.zOffset()), stair));
            }
            for (int y = 1; y <= 2; y++) {
                BlockPos headroom = origin.offset(x, y, -1);
                if (!isLoaded(level, headroom)
                        || level.getBlockEntity(headroom) != null
                        || !level.getFluidState(headroom).isEmpty()) {
                    return null;
                }
                BlockState current = level.getBlockState(headroom);
                if (!current.isAir() && !current.canBeReplaced()) {
                    return null;
                }
            }
        }
        if (!hasEntranceHeadroom(level, result)) {
            return null;
        }

        List<TerrainFoundationPlan.Cell> authored = result.stream()
                .map(placement -> placement.position().subtract(origin))
                .map(relative -> new TerrainFoundationPlan.Cell(
                        relative.getX(), relative.getY(), relative.getZ()))
                .toList();
        Set<BlockPos> groundedColumns = new HashSet<>();
        for (TerrainFoundationPlan.Cell support : TerrainFoundationPlan.appendSupportCells(
                authored, TerrainFoundationPlan.MAX_TERRAIN_DROP)) {
            BlockPos column = new BlockPos(support.x(), 0, support.z());
            if (groundedColumns.contains(column)) {
                continue;
            }
            BlockPos target = origin.offset(support.x(), support.y(), support.z());
            if (!isLoaded(level, target)
                    || level.getBlockEntity(target) != null
                    || !level.getFluidState(target).isEmpty()) {
                return null;
            }
            BlockState current = level.getBlockState(target);
            if (isNaturalBankGround(current)) {
                groundedColumns.add(column);
                continue;
            }
            if (!current.equals(foundation)
                    && !current.isAir()
                    && !current.canBeReplaced()) {
                return null;
            }
            result.add(new BankPlacement(target, foundation));
        }

        // Preserve the previous shallow-foundation repair as part of this one-shot upgrade. A
        // claimed or player-replaced cell is skipped instead of being treated as Bank ownership.
        for (int x : new int[] {2, centerX - 2, centerX + 2}) {
            BlockPos target = origin.offset(x, 0, -1);
            BlockPos detail = target.above();
            if (!isLoaded(level, target)
                    || !isLoaded(level, detail)
                    || (x == 2 && !isLoaded(level, detail.above()))) {
                continue;
            }
            boolean detailMatches = x == 2
                    ? level.getBlockState(detail).is(palette.accent())
                            && level.getBlockState(detail.above()).is(Blocks.BELL)
                    : level.getBlockState(detail).is(palette.corner());
            if (!detailMatches) {
                continue;
            }
            BlockState current = level.getBlockState(target);
            if (current.equals(foundation)) {
                continue;
            }
            if ((current.isAir() || current.canBeReplaced())
                    && level.getBlockEntity(target) == null
                    && level.getFluidState(target).isEmpty()) {
                result.add(new BankPlacement(target, foundation));
            }
        }
        return List.copyOf(result);
    }

    private static boolean hasEntranceHeadroom(
            ServerLevel level, List<BankPlacement> entrance) {
        for (BankPlacement placement : entrance) {
            if (!(placement.state().getBlock() instanceof StairBlock)) {
                continue;
            }
            for (int offset = 1; offset <= 2; offset++) {
                BlockPos position = placement.position().above(offset);
                if (!isLoaded(level, position)
                        || level.getBlockEntity(position) != null
                        || !level.getFluidState(position).isEmpty()) {
                    return false;
                }
                BlockState state = level.getBlockState(position);
                if (!state.isAir() && !state.canBeReplaced()) {
                    return false;
                }
            }
        }
        return true;
    }

    private static void normalizeManagedBankPanes(
            ServerLevel level, BlockPos origin, UUID villageId, long bankKey) {
        for (int y = 2; y <= 3; y++) {
            for (int x = 0; x < BANK_WIDTH; x++) {
                for (int z = 0; z < BANK_DEPTH; z++) {
                    if (isBankWindowCell(x, y, z)) {
                        normalizeBankPaneState(
                                level, origin.offset(x, y, z), villageId, bankKey);
                    }
                }
            }
        }
        normalizeBankPaneState(
                level,
                origin.offset(BANK_WIDTH / 2, 3, 0),
                villageId,
                bankKey);
    }

    /** Recomputes one surviving authored pane without recreating or replacing player blocks. */
    static boolean normalizeBankPaneState(
            ServerLevel level, BlockPos position, UUID villageId, long bankKey) {
        if (!isLoaded(level, position)
                || !isLoaded(level, position.north())
                || !isLoaded(level, position.east())
                || !isLoaded(level, position.south())
                || !isLoaded(level, position.west())) {
            return false;
        }
        BlockState current = level.getBlockState(position);
        if (!current.is(Blocks.STAINED_GLASS_PANE.green())) {
            return false;
        }
        BlockState updated = Block.updateFromNeighbourShapes(current, level, position);
        if (updated.equals(current)) {
            return true;
        }
        if (!VillageDevelopmentProtection.mayPlace(
                level, villageId, bankKey, position, current, updated)) {
            return false;
        }
        return VillageConstructionOccupancy.setBlock(level,
                        position,
                        updated,
                        Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE)
                && level.getBlockState(position).equals(updated);
    }

    private static BankBuildResult buildBank(
            ServerLevel level, BlockPos origin, UUID villageId, long bankKey) {
        ensureBankTemplateValidated();
        BankPalette palette = paletteFor(level, origin);
        List<BankPlacement> plan = terrainSupportedBankPlan(level, origin, palette);
        if (plan == null) {
            return BankBuildResult.failed();
        }
        for (BankPlacement placement : plan) {
            if (!isLoaded(level, placement.position())) {
                return BankBuildResult.failed();
            }
            BlockState existing = level.getBlockState(placement.position());
            if ((!existing.isAir() && !existing.canBeReplaced())
                    || !level.getFluidState(placement.position()).isEmpty()
                    || level.getBlockEntity(placement.position()) != null
                    || !VillageDevelopmentProtection.mayPlace(
                            level,
                            villageId,
                            bankKey,
                            placement.position(),
                            existing,
                            placement.state())) {
                return BankBuildResult.failed();
            }
        }

        List<BankMutation> placed = new ArrayList<>(plan.size());
        for (BankPlacement placement : plan) {
            BlockState original = level.getBlockState(placement.position());
            boolean changed = VillageConstructionOccupancy.setBlock(level,placement.position(), placement.state(), 3);
            BlockState applied = level.getBlockState(placement.position());
            if (!changed || !applied.equals(placement.state())) {
                // Some integrations can report a failed/cancelled placement after mutating the
                // world. Include that authored block in rollback when it did appear.
                if (isOwnedBankPlacement(applied, placement.state())) {
                    placed.add(new BankMutation(placement, original));
                }
                rollbackBank(level, placed);
                return BankBuildResult.failed();
            }
            placed.add(new BankMutation(placement, original));
        }
        return new BankBuildResult(true, List.copyOf(placed));
    }

    private static BlockPos surfaceVillageProbe(ServerLevel level, BlockPos playerPosition) {
        int surface = level.getHeight(
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                playerPosition.getX(),
                playerPosition.getZ());
        return new BlockPos(playerPosition.getX(), surface, playerPosition.getZ());
    }

    /** Adds only the air/replaceable foundation cells needed to bridge a shallow natural lot. */
    private static List<BankPlacement> terrainSupportedBankPlan(
            ServerLevel level, BlockPos origin, BankPalette palette) {
        var survey = new VillageSitePreparation.Survey(level);
        List<BankPlacement> base = bankPlan(origin, palette);
        List<List<Integer>> profiles = sampleNaturalEntranceProfiles(level, origin);
        Optional<List<VillageBankPlacementPolicy.EntranceStep>> steps = profiles == null
                ? Optional.empty()
                : VillageBankPlacementPolicy.planWideEntranceSteps(
                        profiles, TerrainFoundationPlan.MAX_TERRAIN_DROP);
        if (steps.isEmpty()) {
            return null;
        }
        List<BankPlacement> result = new ArrayList<>(base);
        BlockState stair = palette.stairs().defaultBlockState()
                .setValue(StairBlock.FACING, Direction.SOUTH);
        int centerX = BANK_WIDTH / 2;
        for (VillageBankPlacementPolicy.EntranceStep step : steps.orElseThrow()) {
            if (step.zOffset() == -2) {
                continue;
            }
            for (int x = centerX - BANK_ENTRANCE_HALF_WIDTH;
                    x <= centerX + BANK_ENTRANCE_HALF_WIDTH;
                    x++) {
                result.add(new BankPlacement(
                        origin.offset(x, step.yOffset(), step.zOffset()), stair));
            }
        }
        for (BankPlacement cell : result) {
            if (!(cell.state().getBlock() instanceof StairBlock)) continue;
            if (!survey.available(cell.position().above(), origin.getY())
                    || !survey.available(cell.position().above(2), origin.getY())) return null;
        }
        List<TerrainFoundationPlan.Cell> authored = result.stream()
                .map(placement -> placement.position().subtract(origin))
                .map(relative -> new TerrainFoundationPlan.Cell(
                        relative.getX(), relative.getY(), relative.getZ()))
                .toList();
        Set<BlockPos> groundedColumns = new HashSet<>();
        for (TerrainFoundationPlan.Cell support : TerrainFoundationPlan.appendSupportCells(
                authored, TerrainFoundationPlan.MAX_TERRAIN_DROP)) {
            BlockPos column = new BlockPos(support.x(), 0, support.z());
            if (groundedColumns.contains(column)) {
                continue;
            }
            BlockPos target = origin.offset(support.x(), support.y(), support.z());
            if (!isLoaded(level, target)
                    || level.getBlockEntity(target) != null
                    || !level.getFluidState(target).isEmpty()) {
                return null;
            }
            BlockState current = level.getBlockState(target);
            if (isNaturalBankGround(current)) {
                // The first natural block in a column is already a sound footing. Do not tunnel
                // through it merely because the deterministic suffix has additional depth cells.
                groundedColumns.add(column);
                continue;
            }
            if (!current.isAir() && !survey.clearable(target)) {
                return null;
            }
            result.add(new BankPlacement(target, palette.foundation().defaultBlockState()));
        }
        return List.copyOf(result);
    }

    /** Frozen version-two blueprint used only to assess existing Banks without rewriting them. */
    private static List<BankPlacement> legacyBankPlanV2(
            BlockPos origin, BankPalette palette) {
        List<BankPlacement> placements = new ArrayList<>();
        for (int x = 0; x < BANK_WIDTH; x++) {
            for (int z = 0; z < BANK_DEPTH; z++) {
                placements.add(new BankPlacement(
                        origin.offset(x, 0, z),
                        (x == 0 || x == BANK_WIDTH - 1
                                        || z == 0 || z == BANK_DEPTH - 1
                                ? palette.foundation()
                                : palette.floor()).defaultBlockState()));
            }
        }
        for (int y = 1; y <= 4; y++) {
            for (int x = 0; x < BANK_WIDTH; x++) {
                for (int z = 0; z < BANK_DEPTH; z++) {
                    boolean edge = x == 0 || x == BANK_WIDTH - 1
                            || z == 0 || z == BANK_DEPTH - 1;
                    if (!edge) {
                        continue;
                    }
                    boolean corner = (x == 0 || x == BANK_WIDTH - 1)
                            && (z == 0 || z == BANK_DEPTH - 1);
                    boolean entrance = z == 0
                            && x == BANK_WIDTH / 2
                            && y <= 3;
                    boolean window = isBankWindowCell(x, y, z);
                    if (entrance) {
                        continue;
                    }
                    Block wallBlock = corner
                            ? palette.corner()
                            : y == 1 || y == 4 ? palette.accent() : palette.wall();
                    placements.add(new BankPlacement(
                            origin.offset(x, y, z),
                            window
                                    ? connectedBankPaneState(x == 0 || x == BANK_WIDTH - 1)
                                    : wallBlock.defaultBlockState()));
                }
            }
        }
        for (int z = -1; z <= BANK_DEPTH; z++) {
            int distanceFromEave = Math.min(z + 1, BANK_DEPTH - z);
            int roofY = 5 + (distanceFromEave + 1) / 2;
            for (int x = -1; x <= BANK_WIDTH; x++) {
                placements.add(new BankPlacement(
                        origin.offset(x, roofY, z),
                        palette.roofDeck().defaultBlockState()));
            }
            if (z >= 0 && z < BANK_DEPTH) {
                for (int y = 5; y < roofY; y++) {
                    placements.add(new BankPlacement(
                            origin.offset(0, y, z), palette.wall().defaultBlockState()));
                    placements.add(new BankPlacement(
                            origin.offset(BANK_WIDTH - 1, y, z),
                            palette.wall().defaultBlockState()));
                }
            }
        }
        for (int x = 1; x < BANK_WIDTH - 1; x++) {
            placements.add(new BankPlacement(
                    origin.offset(x, 5, 0), palette.wall().defaultBlockState()));
            placements.add(new BankPlacement(
                    origin.offset(x, 5, BANK_DEPTH - 1),
                    palette.wall().defaultBlockState()));
        }
        for (int x : new int[] {BANK_WIDTH / 2 - 2, BANK_WIDTH / 2 + 2}) {
            placements.add(new BankPlacement(
                    origin.offset(x, 0, -1), palette.foundation().defaultBlockState()));
            for (int y = 1; y <= 3; y++) {
                placements.add(new BankPlacement(
                        origin.offset(x, y, -1), palette.corner().defaultBlockState()));
            }
        }
        for (int x = BANK_WIDTH / 2 - 3; x <= BANK_WIDTH / 2 + 3; x++) {
            placements.add(new BankPlacement(
                    origin.offset(x, 4, -1), palette.roof().defaultBlockState()));
        }
        placements.add(new BankPlacement(
                origin.offset(2, 0, -1), palette.foundation().defaultBlockState()));
        placements.add(new BankPlacement(
                origin.offset(2, 1, -1), palette.accent().defaultBlockState()));
        placements.add(new BankPlacement(
                origin.offset(2, 2, -1), Blocks.BELL.defaultBlockState()));

        BlockState lowerDoor = palette.door().defaultBlockState()
                .setValue(DoorBlock.FACING, Direction.NORTH)
                .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
        placements.add(new BankPlacement(
                origin.offset(BANK_WIDTH / 2, 1, 0), lowerDoor));
        placements.add(new BankPlacement(
                origin.offset(BANK_WIDTH / 2, 2, 0),
                lowerDoor.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER)));
        placements.add(new BankPlacement(
                origin.offset(BANK_WIDTH / 2, 3, 0), connectedBankPaneState(false)));
        BlockState entranceStair = palette.stairs().defaultBlockState()
                .setValue(StairBlock.FACING, Direction.SOUTH);
        for (int x = BANK_WIDTH / 2 - BANK_ENTRANCE_HALF_WIDTH;
                x <= BANK_WIDTH / 2 + BANK_ENTRANCE_HALF_WIDTH;
                x++) {
            placements.add(new BankPlacement(
                    origin.offset(x, 0, -1), palette.foundation().defaultBlockState()));
            placements.add(new BankPlacement(origin.offset(x, 0, -2), entranceStair));
        }

        int counterZ = BANK_DEPTH - 3;
        for (int x = 2; x <= BANK_WIDTH - 3; x++) {
            BlockState counterState;
            if (x == BANK_WIDTH / 2) {
                counterState = BankerProfessionSupport.exchangeDeskOrLectern()
                        .defaultBlockState();
            } else if ((x & 1) == 0) {
                counterState = Blocks.CHEST.defaultBlockState();
            } else {
                counterState = Blocks.CHISELED_BOOKSHELF.defaultBlockState();
            }
            placements.add(new BankPlacement(
                    origin.offset(x, 1, counterZ), counterState));
        }
        placements.add(new BankPlacement(
                origin.offset(2, 1, counterZ - 1),
                Blocks.CHISELED_BOOKSHELF.defaultBlockState()));
        placements.add(new BankPlacement(
                origin.offset(BANK_WIDTH - 3, 1, counterZ - 1),
                Blocks.CHISELED_BOOKSHELF.defaultBlockState()));
        placements.add(new BankPlacement(
                origin.offset(1, 1, 1), Blocks.BOOKSHELF.defaultBlockState()));
        placements.add(new BankPlacement(
                origin.offset(1, 2, 1), Blocks.BOOKSHELF.defaultBlockState()));
        placements.add(new BankPlacement(
                origin.offset(BANK_WIDTH - 2, 1, 1), Blocks.BOOKSHELF.defaultBlockState()));
        placements.add(new BankPlacement(
                origin.offset(BANK_WIDTH - 2, 2, 1), Blocks.BOOKSHELF.defaultBlockState()));
        placements.add(new BankPlacement(
                origin.offset(1, 1, counterZ), Blocks.ENDER_CHEST.defaultBlockState()));
        placements.add(new BankPlacement(
                origin.offset(BANK_WIDTH - 2, 1, counterZ),
                Blocks.CRAFTING_TABLE.defaultBlockState()));
        placements.add(new BankPlacement(
                origin.offset(2, 1, 3), palette.fence().defaultBlockState()));
        placements.add(new BankPlacement(
                origin.offset(BANK_WIDTH - 3, 1, 3), palette.fence().defaultBlockState()));
        placements.add(new BankPlacement(
                origin.offset(2, 2, 3), Blocks.LANTERN.defaultBlockState()));
        placements.add(new BankPlacement(
                origin.offset(BANK_WIDTH - 3, 2, 3), Blocks.LANTERN.defaultBlockState()));
        placements.add(new BankPlacement(
                origin.offset(BANK_WIDTH / 2, 7, BANK_DEPTH / 2),
                palette.corner().defaultBlockState()));
        placements.add(new BankPlacement(
                origin.offset(BANK_WIDTH / 2, 6, BANK_DEPTH / 2),
                Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true)));
        return List.copyOf(placements);
    }

    /** Frozen version-three blueprint used only to assess existing Banks without rewriting them. */
    private static List<BankPlacement> legacyBankPlanV3(
            BlockPos origin, BankPalette palette) {
        List<BankPlacement> placements = new ArrayList<>();
        int centerX = BANK_WIDTH / 2;
        int counterZ = BANK_DEPTH - 3;
        for (int x = 0; x < BANK_WIDTH; x++) {
            for (int z = 0; z < BANK_DEPTH; z++) {
                boolean edge = x == 0 || x == BANK_WIDTH - 1
                        || z == 0 || z == BANK_DEPTH - 1;
                boolean publicLedgerInlay = !edge
                        && ((x == centerX && z >= 1 && z < counterZ)
                                || (z == 3 && x >= 3 && x <= BANK_WIDTH - 4));
                boolean vaultInlay = !edge
                        && x >= 1 && x <= 3
                        && z >= counterZ - 2 && z <= counterZ - 1;
                placements.add(new BankPlacement(
                        origin.offset(x, 0, z),
                        (edge
                                        ? palette.foundation()
                                        : publicLedgerInlay || vaultInlay
                                                ? palette.floorAccent()
                                                : palette.floor())
                                .defaultBlockState()));
            }
        }

        for (int y = 1; y <= 4; y++) {
            for (int x = 0; x < BANK_WIDTH; x++) {
                for (int z = 0; z < BANK_DEPTH; z++) {
                    boolean edge = x == 0 || x == BANK_WIDTH - 1
                            || z == 0 || z == BANK_DEPTH - 1;
                    if (!edge) {
                        continue;
                    }
                    boolean corner = (x == 0 || x == BANK_WIDTH - 1)
                            && (z == 0 || z == BANK_DEPTH - 1);
                    boolean entrance = z == 0
                            && x == centerX
                            && y <= 3;
                    boolean window = isBankWindowCell(x, y, z);
                    if (entrance) {
                        continue;
                    }
                    boolean civicSeal = z == 0 && x == centerX && y == 4;
                    Block wallBlock = civicSeal
                            ? Blocks.GLAZED_TERRACOTTA.green()
                            : corner
                                    ? palette.corner()
                                    : y == 4
                                            ? palette.secondaryTrim()
                                            : y == 1 ? palette.accent() : palette.wall();
                    placements.add(new BankPlacement(
                            origin.offset(x, y, z),
                            window
                                    ? connectedBankPaneState(x == 0 || x == BANK_WIDTH - 1)
                                    : wallBlock.defaultBlockState()));
                }
            }
        }

        // A full backing course closes every weather seam while a stair surface supplies a smooth,
        // deliberately shaped roof. This retains the conservative all-block weather shell without
        // presenting the former monolithic staircase of full cubes to the player.
        for (int z = -1; z <= BANK_DEPTH; z++) {
            int distanceFromEave = Math.min(z + 1, BANK_DEPTH - z);
            int roofY = 5 + (distanceFromEave + 1) / 2;
            for (int x = -1; x <= BANK_WIDTH; x++) {
                BlockState backing = x == centerX && z == BANK_DEPTH / 2
                        ? palette.corner().defaultBlockState()
                        : palette.roofDeck().defaultBlockState();
                placements.add(new BankPlacement(
                        origin.offset(x, roofY - 1, z), backing));
                boolean chimneyMount = x == 2 && z == BANK_DEPTH - 4;
                BlockState roofSurface = chimneyMount
                        ? palette.chimney().defaultBlockState()
                        : z == BANK_DEPTH / 2
                                ? palette.roofDeck().defaultBlockState()
                                : palette.roofStairs().defaultBlockState().setValue(
                                        StairBlock.FACING,
                                        z < BANK_DEPTH / 2
                                                ? Direction.NORTH
                                                : Direction.SOUTH);
                placements.add(new BankPlacement(
                        origin.offset(x, roofY, z), roofSurface));
            }
            if (z >= 0 && z < BANK_DEPTH) {
                for (int y = 5; y < roofY - 1; y++) {
                    Block gableFill = y == roofY - 2
                            ? palette.secondaryTrim()
                            : palette.wall();
                    placements.add(new BankPlacement(
                            origin.offset(0, y, z), gableFill.defaultBlockState()));
                    placements.add(new BankPlacement(
                            origin.offset(BANK_WIDTH - 1, y, z),
                            gableFill.defaultBlockState()));
                }
            }
        }
        // A glazed roof lantern gives the civic building a memorable skyline and admits daylight
        // without being trusted by the nighttime light-safety model.
        for (int x = -1; x <= BANK_WIDTH; x++) {
            if (x < centerX - 2 || x > centerX + 2) {
                placements.add(new BankPlacement(
                        origin.offset(x, 9, BANK_DEPTH / 2),
                        palette.roof().defaultBlockState()));
            }
        }
        for (int[] corner : new int[][] {
                {centerX - 1, BANK_DEPTH / 2 - 1},
                {centerX + 1, BANK_DEPTH / 2 - 1},
                {centerX - 1, BANK_DEPTH / 2 + 1},
                {centerX + 1, BANK_DEPTH / 2 + 1}
        }) {
            placements.add(new BankPlacement(
                    origin.offset(corner[0], 9, corner[1]),
                    palette.corner().defaultBlockState()));
        }
        placements.add(new BankPlacement(
                origin.offset(centerX, 9, BANK_DEPTH / 2 - 1),
                Blocks.STAINED_GLASS.green().defaultBlockState()));
        placements.add(new BankPlacement(
                origin.offset(centerX, 9, BANK_DEPTH / 2 + 1),
                Blocks.STAINED_GLASS.green().defaultBlockState()));
        placements.add(new BankPlacement(
                origin.offset(centerX - 1, 9, BANK_DEPTH / 2),
                Blocks.STAINED_GLASS.green().defaultBlockState()));
        placements.add(new BankPlacement(
                origin.offset(centerX + 1, 9, BANK_DEPTH / 2),
                Blocks.STAINED_GLASS.green().defaultBlockState()));
        for (int x = centerX - 2; x <= centerX + 2; x++) {
            for (int z = BANK_DEPTH / 2 - 2; z <= BANK_DEPTH / 2 + 2; z++) {
                placements.add(new BankPlacement(
                        origin.offset(x, 10, z),
                        (z == BANK_DEPTH / 2
                                        ? palette.secondaryTrim()
                                        : palette.roof())
                                .defaultBlockState()));
            }
        }
        // A two-deep civic portico, public bell, green seal, and paired lamps make the Bank
        // unmistakable from a village path. Every projected column has its own footing.
        for (int z : new int[] {-1, -2}) {
            for (int x : new int[] {centerX - 2, centerX + 2}) {
                placements.add(new BankPlacement(
                        origin.offset(x, 0, z), palette.foundation().defaultBlockState()));
                for (int y = 1; y <= 3; y++) {
                placements.add(new BankPlacement(
                        origin.offset(x, y, z),
                        (y == 3 ? palette.secondaryTrim() : palette.corner())
                                .defaultBlockState()));
                }
            }
        }
        for (int x : new int[] {centerX - 3, centerX + 3}) {
            placements.add(new BankPlacement(
                    origin.offset(x, 0, -2), palette.foundation().defaultBlockState()));
        }
        for (int x = centerX - 3; x <= centerX + 3; x++) {
            placements.add(new BankPlacement(
                    origin.offset(x, 4, -2),
                    palette.roofStairs().defaultBlockState()
                            .setValue(StairBlock.FACING, Direction.NORTH)));
        }
        placements.add(new BankPlacement(
                origin.offset(2, 0, -1), palette.foundation().defaultBlockState()));
        placements.add(new BankPlacement(
                origin.offset(2, 1, -1), palette.accent().defaultBlockState()));
        placements.add(new BankPlacement(
                origin.offset(2, 2, -1), Blocks.BELL.defaultBlockState()));
        placements.add(new BankPlacement(
                origin.offset(BANK_WIDTH - 3, 0, -1),
                palette.foundation().defaultBlockState()));
        placements.add(new BankPlacement(
                origin.offset(BANK_WIDTH - 3, 1, -1),
                palette.accent().defaultBlockState()));
        placements.add(new BankPlacement(
                origin.offset(BANK_WIDTH - 3, 2, -1),
                Blocks.LANTERN.defaultBlockState()));
        for (int x : new int[] {centerX - 3, centerX + 3}) {
            placements.add(new BankPlacement(
                    origin.offset(x, 3, -2), Blocks.IRON_CHAIN.defaultBlockState()));
            placements.add(new BankPlacement(
                    origin.offset(x, 2, -2),
                    Blocks.LANTERN.defaultBlockState()
                            .setValue(LanternBlock.HANGING, true)));
        }
        // The deep eave makes the three-wide public landing a real no-skylight spawn surface.
        // Center one restrained pendant on the existing roof backing so the doorway reads warmly
        // and every covered landing cell clears the same comfort gate as the teller hall.
        placements.add(new BankPlacement(
                origin.offset(centerX, 3, -1),
                Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true)));

        BlockState lowerDoor = palette.door().defaultBlockState()
                .setValue(DoorBlock.FACING, Direction.NORTH)
                .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
        placements.add(new BankPlacement(
                origin.offset(centerX, 1, 0), lowerDoor));
        placements.add(new BankPlacement(
                origin.offset(centerX, 2, 0),
                lowerDoor.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER)));
        placements.add(new BankPlacement(
                origin.offset(centerX, 3, 0),
                connectedBankPaneState(false)));
        BlockState entranceStair = palette.stairs().defaultBlockState()
                .setValue(StairBlock.FACING, Direction.SOUTH);
        for (int x = centerX - BANK_ENTRANCE_HALF_WIDTH;
                x <= centerX + BANK_ENTRANCE_HALF_WIDTH;
                x++) {
            placements.add(new BankPlacement(
                    origin.offset(x, 0, -1), palette.foundation().defaultBlockState()));
            placements.add(new BankPlacement(origin.offset(x, 0, -2), entranceStair));
        }

        for (int x = 2; x <= BANK_WIDTH - 3; x++) {
            BlockState counterState;
            if (x == centerX) {
                counterState = BankerProfessionSupport.exchangeDeskOrLectern().defaultBlockState();
            } else if ((x & 1) == 0) {
                counterState = Blocks.CHEST.defaultBlockState();
            } else {
                counterState = Blocks.CHISELED_BOOKSHELF.defaultBlockState();
            }
            placements.add(new BankPlacement(
                    origin.offset(x, 1, counterZ), counterState));
        }
        placements.add(new BankPlacement(
                origin.offset(1, 1, 1), Blocks.BOOKSHELF.defaultBlockState()));
        placements.add(new BankPlacement(
                origin.offset(1, 2, 1), Blocks.BOOKSHELF.defaultBlockState()));
        placements.add(new BankPlacement(
                origin.offset(BANK_WIDTH - 2, 1, 1), Blocks.BOOKSHELF.defaultBlockState()));
        placements.add(new BankPlacement(
                origin.offset(BANK_WIDTH - 2, 2, 1), Blocks.BOOKSHELF.defaultBlockState()));
        placements.add(new BankPlacement(
                origin.offset(1, 1, counterZ), Blocks.ENDER_CHEST.defaultBlockState()));
        placements.add(new BankPlacement(
                origin.offset(BANK_WIDTH - 2, 1, counterZ),
                Blocks.CRAFTING_TABLE.defaultBlockState()));

        // Tall record shelves behind the teller line form a distinct staff zone while preserving
        // the center aisle and the Banker's durable anchor.
        for (int shelfX : new int[] {2, 3, 4, 8, 9, 10}) {
            placements.add(new BankPlacement(
                    origin.offset(shelfX, 1, counterZ + 1),
                    Blocks.CHISELED_BOOKSHELF.defaultBlockState()));
            if ((shelfX & 1) == 0) {
                placements.add(new BankPlacement(
                        origin.offset(shelfX, 2, counterZ + 1),
                        Blocks.CHISELED_BOOKSHELF.defaultBlockState()));
            }
        }

        // Public writing tables give the lobby a human-scale zone without introducing extra job
        // sites. Their pressure-plate tops read as ledgers while leaving the central aisle clear.
        for (int tableX : new int[] {3, BANK_WIDTH - 4}) {
            placements.add(new BankPlacement(
                    origin.offset(tableX, 1, 4), palette.fence().defaultBlockState()));
            placements.add(new BankPlacement(
                    origin.offset(tableX, 2, 4),
                    Blocks.OAK_PRESSURE_PLATE.defaultBlockState()));
            placements.add(new BankPlacement(
                    origin.offset(tableX - 1, 1, 4),
                    palette.roofStairs().defaultBlockState()
                            .setValue(StairBlock.FACING, Direction.WEST)));
            placements.add(new BankPlacement(
                    origin.offset(tableX + 1, 1, 4),
                    palette.roofStairs().defaultBlockState()
                            .setValue(StairBlock.FACING, Direction.EAST)));
        }

        // A branded carpet runner joins the entrance to the teller hall without narrowing its
        // three-wide circulation route.
        for (int z = 1; z <= 6; z++) {
            for (int x = centerX - 1; x <= centerX + 1; x++) {
                placements.add(new BankPlacement(
                        origin.offset(x, 1, z), Blocks.CARPET.green().defaultBlockState()));
            }
        }

        // The Exchange Desk is the teller hall's unmistakable focal point: slim posts rise from
        // the counter and carry a branded lintel without obstructing the customer or Banker side.
        for (int frameX : new int[] {centerX - 1, centerX + 1}) {
            placements.add(new BankPlacement(
                    origin.offset(frameX, 2, counterZ),
                    palette.fence().defaultBlockState()));
        }
        for (int frameX = centerX - 1; frameX <= centerX + 1; frameX++) {
            placements.add(new BankPlacement(
                    origin.offset(frameX, 3, counterZ),
                    (frameX == centerX
                                    ? Blocks.GLAZED_TERRACOTTA.green()
                                    : palette.secondaryTrim())
                            .defaultBlockState()));
        }

        // A walk-in secure-record alcove occupies the rear-left bay. Its iron jambs, overhead
        // lintel, side grille, ledger shelf, and Ender Chest read as one vault composition while
        // preserving a two-block-high entrance at x=2 and the Banker's rear circulation lane.
        for (int jambX : new int[] {1, 3}) {
            for (int y = 1; y <= 2; y++) {
                placements.add(new BankPlacement(
                        origin.offset(jambX, y, counterZ - 2),
                        Blocks.IRON_BLOCK.defaultBlockState()));
            }
        }
        for (int lintelX = 1; lintelX <= 3; lintelX++) {
            placements.add(new BankPlacement(
                    origin.offset(lintelX, 3, counterZ - 2),
                    Blocks.IRON_BLOCK.defaultBlockState()));
        }
        for (int y = 1; y <= 2; y++) {
            placements.add(new BankPlacement(
                    origin.offset(3, y, counterZ - 1),
                    Blocks.IRON_BARS.defaultBlockState()));
        }
        placements.add(new BankPlacement(
                origin.offset(1, 1, counterZ - 1),
                Blocks.CHISELED_BOOKSHELF.defaultBlockState()));
        placements.add(new BankPlacement(
                origin.offset(2, 1, counterZ - 1), Blocks.ENDER_CHEST.defaultBlockState()));
        placements.add(new BankPlacement(
                origin.offset(1, 2, counterZ - 1), Blocks.LANTERN.defaultBlockState()));

        // Lobby pendants, counter lamps, a vault sconce, and the central chandelier light the
        // program they belong to instead of forming a conspicuous room-wide lantern grid.
        for (int lightX : new int[] {centerX - 2, centerX + 2}) {
            placements.add(new BankPlacement(
                    origin.offset(lightX, 5, 3),
                    Blocks.IRON_CHAIN.defaultBlockState()));
            placements.add(new BankPlacement(
                    origin.offset(lightX, 4, 3),
                    Blocks.LANTERN.defaultBlockState()
                            .setValue(LanternBlock.HANGING, true)));
            placements.add(new BankPlacement(
                    origin.offset(lightX, 2, counterZ),
                    Blocks.LANTERN.defaultBlockState()));
        }
        placements.add(new BankPlacement(
                origin.offset(centerX, 6, BANK_DEPTH / 2),
                Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true)));

        // Side and rear buttresses break long elevations into readable bays while remaining
        // shallow enough for the protected placement envelope.
        for (int sideX : new int[] {-1, BANK_WIDTH}) {
            for (int z : new int[] {2, BANK_DEPTH - 3}) {
                placements.add(new BankPlacement(
                        origin.offset(sideX, 0, z), palette.foundation().defaultBlockState()));
                for (int y = 1; y <= 3; y++) {
                    placements.add(new BankPlacement(
                            origin.offset(sideX, y, z), palette.corner().defaultBlockState()));
                }
                placements.add(new BankPlacement(
                        origin.offset(sideX, 4, z), palette.roof().defaultBlockState()));
            }
        }
        for (int x : new int[] {1, BANK_WIDTH - 2}) {
            placements.add(new BankPlacement(
                    origin.offset(x, 0, BANK_DEPTH),
                    palette.foundation().defaultBlockState()));
            for (int y = 1; y <= 3; y++) {
                placements.add(new BankPlacement(
                        origin.offset(x, y, BANK_DEPTH),
                        palette.corner().defaultBlockState()));
            }
        }

        // Supported sills give both side elevations depth; the rear ledger ledge sits between its
        // buttresses without masking any of the three authored rear windows.
        for (int sideX : new int[] {-1, BANK_WIDTH}) {
            for (int windowZ : new int[] {3, BANK_DEPTH - 4}) {
                placements.add(new BankPlacement(
                        origin.offset(sideX, 0, windowZ),
                        palette.foundation().defaultBlockState()));
                placements.add(new BankPlacement(
                        origin.offset(sideX, 1, windowZ),
                        palette.roof().defaultBlockState()));
            }
        }
        for (int rearX = centerX - 1; rearX <= centerX + 1; rearX++) {
            placements.add(new BankPlacement(
                    origin.offset(rearX, 0, BANK_DEPTH),
                    palette.foundation().defaultBlockState()));
            placements.add(new BankPlacement(
                    origin.offset(rearX, 1, BANK_DEPTH),
                    palette.roof().defaultBlockState()));
        }

        // The chimney begins with a full masonry roof mount above the continuous backing course,
        // so its smoke cap cannot hover over a stair-shaped roof surface.
        for (int y = 8; y <= 10; y++) {
            placements.add(new BankPlacement(
                    origin.offset(2, y, BANK_DEPTH - 4),
                    palette.chimney().defaultBlockState()));
        }
        placements.add(new BankPlacement(
                origin.offset(2, 11, BANK_DEPTH - 4),
                Blocks.CAMPFIRE.defaultBlockState()));
        return List.copyOf(placements);
    }

    /**
     * Frozen version-four blueprint used only to assess existing Banks without rewriting them.
     * Its outer shell remains the version-three composition and its secure-record alcove remains
     * the exact bounded v4 delta.
     */
    private static List<BankPlacement> legacyBankPlanV4(
            BlockPos origin, BankPalette palette) {
        int counterZ = BANK_DEPTH - 3;
        Set<BlockPos> replaced = new HashSet<>();

        // Replace the former one-wide vault frame, centered chest, duplicate loose Ender Chest,
        // and their lamp. The new iron threshold is also part of the deliberately bounded delta.
        for (int x = 1; x <= 4; x++) {
            replaced.add(origin.offset(x, 0, counterZ - 2));
            replaced.add(origin.offset(x, 3, counterZ - 2));
        }
        for (int y = 1; y <= 2; y++) {
            replaced.add(origin.offset(1, y, counterZ - 2));
            replaced.add(origin.offset(3, y, counterZ - 2));
            replaced.add(origin.offset(4, y, counterZ - 2));
            replaced.add(origin.offset(3, y, counterZ - 1));
            replaced.add(origin.offset(4, y, counterZ - 1));
        }
        replaced.add(origin.offset(1, 1, counterZ - 1));
        replaced.add(origin.offset(2, 1, counterZ - 1));
        replaced.add(origin.offset(1, 1, counterZ));
        replaced.add(origin.offset(1, 2, counterZ - 1));

        List<BankPlacement> placements = new ArrayList<>();
        for (BankPlacement placement : legacyBankPlanV3(origin, palette)) {
            if (!replaced.contains(placement.position())) {
                placements.add(placement);
            }
        }

        // Four iron foundation blocks make the secure threshold visually legible. Outer jambs
        // and the full lintel frame a true two-block-wide, two-block-high opening at x=2..3.
        for (int x = 1; x <= 4; x++) {
            placements.add(new BankPlacement(
                    origin.offset(x, 0, counterZ - 2),
                    Blocks.IRON_BLOCK.defaultBlockState()));
            placements.add(new BankPlacement(
                    origin.offset(x, 3, counterZ - 2),
                    Blocks.IRON_BLOCK.defaultBlockState()));
        }
        for (int jambX : new int[] {1, 4}) {
            for (int y = 1; y <= 2; y++) {
                placements.add(new BankPlacement(
                        origin.offset(jambX, y, counterZ - 2),
                        Blocks.IRON_BLOCK.defaultBlockState()));
            }
        }

        // A slim iron-bar return signals containment from the public hall without narrowing either
        // aisle lane. The Ender Chest is offset against the west wall, one block behind the frame;
        // the adjacent ledger shelf carries its own standing vault lamp.
        for (int y = 1; y <= 2; y++) {
            placements.add(new BankPlacement(
                    origin.offset(4, y, counterZ - 1),
                    Blocks.IRON_BARS.defaultBlockState()));
        }
        placements.add(new BankPlacement(
                origin.offset(1, 1, counterZ - 1),
                Blocks.ENDER_CHEST.defaultBlockState()));
        placements.add(new BankPlacement(
                origin.offset(1, 1, counterZ),
                Blocks.CHISELED_BOOKSHELF.defaultBlockState()));
        placements.add(new BankPlacement(
                origin.offset(1, 2, counterZ),
                Blocks.LANTERN.defaultBlockState()));
        return List.copyOf(placements);
    }

    /**
     * Frozen version-five civic Bank. Version four remains an immutable prefix; this method
     * replaces only the perimeter finish and skyline, then adds supported facade, forecourt, and
     * rear-ledger compositions. Existing version-two/three/four Banks are never routed here.
     */
    private static List<BankPlacement> legacyBankPlanV5(BlockPos origin, BankPalette palette) {
        LinkedHashMap<BlockPos, BlockState> authored = new LinkedHashMap<>();
        for (BankPlacement placement : legacyBankPlanV4(origin, palette)) {
            authored.put(placement.position(), placement.state());
        }

        appendBankV5CivicEnvelope(authored, origin, palette);
        appendBankV5WindowBays(authored, origin, palette);
        appendBankV5Forecourt(authored, origin, palette);
        appendBankV5RearLedgerElevation(authored, origin, palette);
        appendBankV5SupportedCounterPendants(authored, origin);
        appendBankV5DormerAndCupola(authored, origin, palette);

        return authored.entrySet().stream()
                .map(entry -> new BankPlacement(entry.getKey(), entry.getValue()))
                .toList();
    }

    /**
     * Version seven keeps the reviewed civic geometry and adds complementary roofing and low,
     * grounded courtyard planting. The older palette and all older version plans remain frozen.
     */
    private static List<BankPlacement> legacyBankPlanV7(BlockPos origin, BankPalette legacyPalette) {
        BankPalette palette = bankV7Palette(legacyPalette);
        LinkedHashMap<BlockPos, BlockState> authored = new LinkedHashMap<>();
        for (BankPlacement placement : legacyBankPlanV6(origin, palette)) {
            authored.put(placement.position(), placement.state());
        }
        appendBankV7ExteriorGardens(authored, origin, palette);
        return authored.entrySet().stream()
                .map(entry -> new BankPlacement(entry.getKey(), entry.getValue()))
                .toList();
    }

    /** Only the two exposed upper brick courses change; the full roof mount and smoke cap stay. */
    private static List<BankPlacement> legacyBankPlanV8(BlockPos origin, BankPalette legacyPalette) {
        return legacyBankPlanV7(origin, legacyPalette).stream().map(placement -> {
            BlockPos relative = placement.position().subtract(origin);
            if (relative.getX() == 2 && relative.getZ() == BANK_DEPTH - 4
                    && relative.getY() >= 9 && relative.getY() <= 10
                    && placement.state().is(Blocks.BRICKS)) {
                return new BankPlacement(placement.position(), Blocks.BRICK_WALL.defaultBlockState());
            }
            return placement;
        }).toList();
    }

    /** Set the runner back from the lintel: a 1.95-high villager cannot step onto carpet under a two-block doorway. */
    private static List<BankPlacement> legacyBankPlanV9(BlockPos origin, BankPalette legacyPalette) {
        return legacyBankPlanV8(origin, legacyPalette).stream().filter(placement -> {
            BlockPos relative = placement.position().subtract(origin);
            return !(relative.getY() == 1 && relative.getZ() == 1
                    && Math.abs(relative.getX() - BANK_WIDTH / 2) <= 1
                    && placement.state().is(Blocks.CARPET.green()));
        }).toList();
    }

    /** V10: the tall side of a roof stair points uphill. Chairs and inverted cornices stay put. */
    private static List<BankPlacement> legacyBankPlanV10(BlockPos origin, BankPalette legacyPalette) {
        Block roofStairs = bankV7Palette(legacyPalette).roofStairs();
        return legacyBankPlanV9(origin, legacyPalette).stream().map(placement -> {
            BlockState state = placement.state();
            if (placement.position().getY() - origin.getY() >= 5
                    && state.is(roofStairs) && state.getValue(StairBlock.HALF) == Half.BOTTOM) {
                return new BankPlacement(placement.position(),
                        state.setValue(StairBlock.FACING, state.getValue(StairBlock.FACING).getOpposite()));
            }
            return placement;
        }).toList();
    }

    /**
     * V11: the six forecourt seats open toward the front path (-Z), with their high backs
     * toward the Bank (+Z). Preserve every other cell, order and material in the frozen plan.
     * Never rotate arbitrary low stairs: the entrance and carved capitals are not benches.
     */
    private static List<BankPlacement> bankPlan(BlockPos origin, BankPalette legacyPalette) {
        return legacyBankPlanV10(origin, legacyPalette).stream().map(placement -> {
            if (isBankForecourtSeat(placement.position().subtract(origin))) {
                return new BankPlacement(placement.position(),
                        placement.state().setValue(StairBlock.FACING, Direction.SOUTH));
            }
            return placement;
        }).toList();
    }

    private static boolean isBankForecourtSeat(BlockPos relative) {
        if (relative.getY() != 1 || relative.getZ() != -4) return false;
        int x = relative.getX();
        return x == 0 || x == 1 || x == 3
                || x == BANK_WIDTH - 4 || x == BANK_WIDTH - 2 || x == BANK_WIDTH - 1;
    }

    /** New-only, idempotent palette projection; integrity checks for v2-v6 never call this. */
    private static BankPalette bankV7Palette(BankPalette old) {
        Block slab = old.roof();
        Block deck = old.roofDeck();
        Block stairs = old.roofStairs();
        if (old.foundation() == Blocks.SMOOTH_SANDSTONE) {
            // Terracotta-coloured timber roofing contrasts with the cool, pale sandstone walls.
            slab = Blocks.ACACIA_SLAB;
            deck = Blocks.ACACIA_PLANKS;
            stairs = Blocks.ACACIA_STAIRS;
        } else if (old.door() == Blocks.SPRUCE_DOOR
                && old.facadePier() != Blocks.STRIPPED_DARK_OAK_LOG) {
            // Slate tiles keep the Taiga's warm spruce walls readable beneath a durable roof.
            slab = Blocks.DEEPSLATE_TILE_SLAB;
            deck = Blocks.DEEPSLATE_TILES;
            stairs = Blocks.DEEPSLATE_TILE_STAIRS;
        } else if (old.door() == Blocks.ACACIA_DOOR
                || old.facadePier() == Blocks.STRIPPED_DARK_OAK_LOG) {
            // Savanna and Snowy already have dark stairs: match their eave/deck to that roof.
            slab = Blocks.DARK_OAK_SLAB;
            deck = Blocks.DARK_OAK_PLANKS;
        }
        return new BankPalette(old.foundation(), old.floor(), old.floorAccent(), old.wall(),
                old.corner(), slab, deck, stairs, old.fence(), old.accent(), old.secondaryTrim(),
                old.door(), old.stairs(), old.chimney(), old.facadePier(), old.civicPlinth(),
                old.civicCornice(), old.civicWall(), old.ledgerAccent(), old.forecourt(),
                old.roofCap(), old.cupolaBase());
    }

    private static void appendBankV7ExteriorGardens(
            Map<BlockPos, BlockState> authored, BlockPos origin, BankPalette palette) {
        boolean desert = palette.foundation() == Blocks.SMOOTH_SANDSTONE;
        boolean conifer = palette.door() == Blocks.SPRUCE_DOOR;
        Block foliage = conifer ? Blocks.SPRUCE_LEAVES
                : palette.door() == Blocks.ACACIA_DOOR ? Blocks.ACACIA_LEAVES : Blocks.OAK_LEAVES;
        Block flower = desert ? Blocks.POTTED_CACTUS
                : conifer ? Blocks.POTTED_FERN : Blocks.POTTED_DANDELION;
        // Four two-cell planting troughs occupy only free side corners inside the existing plot.
        // They never overlap a window bay, pier, lamp, or the entire three-wide public approach.
        for (int x : new int[] {-1, BANK_WIDTH}) {
            for (int startZ : new int[] {0, BANK_DEPTH - 2}) {
                boolean available = true;
                for (int z = startZ; z <= startZ + 1; z++) {
                    for (int y = 0; y <= 2; y++) {
                        BlockState existing = authored.get(origin.offset(x, y, z));
                        available &= existing == null || existing.isAir();
                    }
                }
                if (!available) {
                    continue;
                }
                for (int z = startZ; z <= startZ + 1; z++) {
                    authored.put(origin.offset(x, 0, z), palette.foundation().defaultBlockState());
                    BlockState planting = z == startZ ? flower.defaultBlockState()
                            : desert ? Blocks.POTTED_DEAD_BUSH.defaultBlockState()
                            : foliage.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
                    authored.put(origin.offset(x, 1, z), planting);
                }
            }
        }
    }

    /**
     * Version six is a frozen authored composition, never an in-place upgrade. The production plot,
     * doorway, public aisle, teller anchor and two-wide record-room route are unchanged. Its
     * visual delta gives the belfry one readable cap and makes masonry frames finer at eye level.
     */
    private static List<BankPlacement> legacyBankPlanV6(BlockPos origin, BankPalette palette) {
        LinkedHashMap<BlockPos, BlockState> authored = new LinkedHashMap<>();
        for (BankPlacement placement : legacyBankPlanV5(origin, palette)) {
            authored.put(placement.position(), placement.state());
        }
        BankCivicFinish finish = bankCivicFinish(palette);
        appendBankV6Facades(authored, origin, palette, finish);
        appendBankV6Belfry(authored, origin, palette, finish);
        appendBankV6RecordRoom(authored, origin, palette, finish);
        polishBankV6Materials(authored, origin, palette);
        return authored.entrySet().stream()
                .map(entry -> new BankPlacement(entry.getKey(), entry.getValue()))
                .toList();
    }

    private static void appendBankV6Facades(
            Map<BlockPos, BlockState> authored, BlockPos origin,
            BankPalette palette, BankCivicFinish finish) {
        int centerX = BANK_WIDTH / 2;
        // A restrained continuous structural course replaces the former dominant brick stripe.
        // Full backing remains behind shaped projecting mouldings, so light and rain cannot leak.
        for (int x = 0; x < BANK_WIDTH; x++) {
            for (int z = 0; z < BANK_DEPTH; z++) {
                if (x != 0 && x != BANK_WIDTH - 1 && z != 0 && z != BANK_DEPTH - 1) {
                    continue;
                }
                if (!(z == 0 && x == centerX)) {
                    authored.put(origin.offset(x, 4, z), finish.trim().defaultBlockState());
                }
                if (!bankV5FacadePier(x, z)
                        && !((x == 0 || x == BANK_WIDTH - 1)
                                && (z == 0 || z == BANK_DEPTH - 1))) {
                    for (int y = 2; y <= 3; y++) {
                        if (!isBankWindowCell(x, y, z) && !(z == 0 && x == centerX)) {
                            authored.put(origin.offset(x, y, z), finish.wall().defaultBlockState());
                        }
                    }
                }
            }
        }
        // Front windows are recessed between shaped sill, slim capitals and weather lintels.
        for (int windowX : new int[] {2, BANK_WIDTH - 3}) {
            authored.put(origin.offset(windowX, 1, -1),
                    bankCorniceStair(finish, Direction.NORTH, Half.TOP));
            for (int x = windowX - 1; x <= windowX + 1; x++) {
                authored.put(origin.offset(x, 4, -1), finish.trim().defaultBlockState());
            }
            for (int x : new int[] {windowX - 1, windowX + 1}) {
                authored.put(origin.offset(x, 3, -1),
                        bankCorniceStair(finish,
                                x < windowX ? Direction.EAST : Direction.WEST, Half.TOP));
            }
        }
        // Carved portico capitals frame a genuinely sheltered three-wide landing. The columns
        // remain full height, but their light stone caps no longer read as unrelated brick blocks.
        for (int z : new int[] {-2, -1}) {
            for (int x : new int[] {centerX - 2, centerX + 2}) {
                authored.put(origin.offset(x, 3, z), finish.trim().defaultBlockState());
            }
        }
        for (int x = centerX - 3; x <= centerX + 3; x++) {
            authored.put(origin.offset(x, 4, -2),
                    bankCorniceStair(finish, Direction.NORTH, Half.BOTTOM));
        }
        // Side bay reveals and a continuous low dado make the blind elevations feel authored.
        for (int sideX : new int[] {-1, BANK_WIDTH}) {
            Direction outward = sideX < 0 ? Direction.WEST : Direction.EAST;
            for (int z : new int[] {3, BANK_DEPTH - 4}) {
                authored.put(origin.offset(sideX, 1, z),
                        bankCorniceStair(finish, outward, Half.TOP));
                authored.put(origin.offset(sideX, 4, z), finish.trim().defaultBlockState());
            }
            for (int z : new int[] {2, BANK_DEPTH - 3}) {
                authored.put(origin.offset(sideX, 3, z),
                        bankCorniceStair(finish, outward, Half.TOP));
            }
        }
        for (int windowX : new int[] {3, centerX, BANK_WIDTH - 4}) {
            for (int x : new int[] {windowX - 1, windowX + 1}) {
                authored.put(origin.offset(x, 3, BANK_DEPTH),
                        bankCorniceStair(finish,
                                x < windowX ? Direction.EAST : Direction.WEST, Half.TOP));
            }
            if (windowX != centerX) {
                authored.put(origin.offset(windowX, 1, BANK_DEPTH),
                        bankCorniceStair(finish, Direction.SOUTH, Half.TOP));
            }
        }
        for (int x = centerX - 2; x <= centerX + 2; x++) {
            authored.put(origin.offset(x, 5, BANK_DEPTH), finish.trim().defaultBlockState());
        }
        // The public apron is a little civic garden rather than two isolated emblem cubes.
        for (int x : new int[] {4, BANK_WIDTH - 5}) {
            authored.put(origin.offset(x, 1, -3), finish.trim().defaultBlockState());
            authored.put(origin.offset(x, 2, -3), Blocks.POTTED_FERN.defaultBlockState());
        }
        for (int x : new int[] {0, 1, 3, BANK_WIDTH - 4, BANK_WIDTH - 2, BANK_WIDTH - 1}) {
            authored.put(origin.offset(x, 1, -4),
                    palette.roofStairs().defaultBlockState()
                            .setValue(StairBlock.FACING, Direction.NORTH));
        }
    }

    /** A two-course glazed belfry with a single supported copper/slate/snow cap. */
    private static void appendBankV6Belfry(
            Map<BlockPos, BlockState> authored, BlockPos origin,
            BankPalette palette, BankCivicFinish finish) {
        int centerX = BANK_WIDTH / 2;
        int centerZ = BANK_DEPTH / 2;
        for (int x = centerX - 2; x <= centerX + 2; x++) {
            for (int z = centerZ - 2; z <= centerZ + 2; z++) {
                authored.remove(origin.offset(x, 10, z));
                authored.remove(origin.offset(x, 11, z));
            }
        }
        for (int y = 9; y <= 10; y++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    boolean corner = Math.abs(dx) == 1 && Math.abs(dz) == 1;
                    if (dx == 0 && dz == 0) {
                        if (y == 10) {
                            authored.put(origin.offset(centerX, y, centerZ),
                                    Blocks.LANTERN.defaultBlockState()
                                            .setValue(LanternBlock.HANGING, true));
                        }
                    } else {
                        authored.put(origin.offset(centerX + dx, y, centerZ + dz),
                                (corner ? finish.belfryPier() : Blocks.STAINED_GLASS.green())
                                        .defaultBlockState());
                    }
                }
            }
        }
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                BlockState cap = finish.cap().defaultBlockState();
                // Bottom slabs meet the glazing without a half-block daylight seam. A full
                // central crown supplies an unambiguous bearing for the pendant underneath.
                if (cap.getBlock() instanceof SlabBlock) {
                    cap = cap.setValue(SlabBlock.TYPE,
                            dx == 0 && dz == 0 ? SlabType.DOUBLE : SlabType.BOTTOM);
                }
                authored.put(origin.offset(centerX + dx, 11, centerZ + dz), cap);
            }
        }
        // Restore the front dormer's ridge where the old five-wide cap had cut into it.
        authored.put(origin.offset(centerX, 10, centerZ - 2),
                palette.roofCap().defaultBlockState());
    }

    private static void appendBankV6RecordRoom(
            Map<BlockPos, BlockState> authored, BlockPos origin,
            BankPalette palette, BankCivicFinish finish) {
        int counterZ = BANK_DEPTH - 3;
        // A low masonry base, narrow stone jambs and a shaped lintel distinguish secure records
        // without the former two-block-high wall of bright iron in every lobby view.
        for (int x = 1; x <= 4; x++) {
            authored.put(origin.offset(x, 0, counterZ - 2), finish.trim().defaultBlockState());
            authored.put(origin.offset(x, 3, counterZ - 2),
                    finish.archSlab().defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
        }
        for (int x : new int[] {1, 4}) {
            for (int y = 1; y <= 2; y++) {
                authored.put(origin.offset(x, y, counterZ - 2),
                        Blocks.STONE_BRICK_WALL.defaultBlockState()
                                .setValue(WallBlock.UP, true));
            }
        }
        // Two orderly drawers flank the branded desk; outer archival shelves stay recognizable.
        // The existing rear storage capacity and each functional workstation are preserved.
        for (int x : new int[] {4, 8}) {
            authored.put(origin.offset(x, 1, counterZ),
                    Blocks.CHISELED_BOOKSHELF.defaultBlockState());
        }
        for (int x : new int[] {2, 10}) {
            authored.put(origin.offset(x, 1, counterZ),
                    Blocks.CHEST.defaultBlockState());
        }
        // A warm shelf niche behind the waiting tables creates a distinct public reading corner.
        for (int x : new int[] {1, BANK_WIDTH - 2}) {
            authored.put(origin.offset(x, 3, 1), Blocks.LANTERN.defaultBlockState());
        }
    }

    private static BlockState bankCorniceStair(
            BankCivicFinish finish, Direction facing, Half half) {
        return finish.archStairs().defaultBlockState()
                .setValue(StairBlock.FACING, facing).setValue(StairBlock.HALF, half);
    }

    /** Material-only final review pass: every authored position and block shape stays intact. */
    private static void polishBankV6Materials(
            Map<BlockPos, BlockState> authored, BlockPos origin, BankPalette palette) {
        boolean snowy = palette.facadePier() == Blocks.STRIPPED_DARK_OAK_LOG;
        boolean taiga = !snowy && palette.door() == Blocks.SPRUCE_DOOR;
        if (!snowy && !taiga) {
            return;
        }
        authored.replaceAll((position, state) -> {
            int x = position.getX() - origin.getX();
            int y = position.getY() - origin.getY();
            int z = position.getZ() - origin.getZ();
            if (snowy && state.is(Blocks.SNOW_BLOCK)) {
                // The inherited full snow cubes read as loose white blocks. Keep the exact
                // sealed ridge volume, but make it a coherent dark timber roof crown.
                return Blocks.DARK_OAK_PLANKS.defaultBlockState();
            }
            boolean shelteredFootCorner = y <= 1 && (x == 0 || x == BANK_WIDTH - 1)
                    && (z == 0 || z == BANK_DEPTH - 1);
            if (taiga && y <= 4 && !shelteredFootCorner) {
                // A maintained public building uses clean masonry indoors and on its cornice.
                // The small old roof accents and exterior footing corners retain local weathering.
                if (state.is(Blocks.MOSSY_STONE_BRICKS)) {
                    return Blocks.STONE_BRICKS.withPropertiesOf(state);
                }
                if (state.is(Blocks.MOSSY_COBBLESTONE)) {
                    return Blocks.COBBLESTONE.withPropertiesOf(state);
                }
                if (state.is(Blocks.MOSSY_STONE_BRICK_STAIRS)) {
                    return Blocks.STONE_BRICK_STAIRS.withPropertiesOf(state);
                }
                if (state.is(Blocks.MOSSY_STONE_BRICK_SLAB)) {
                    return Blocks.STONE_BRICK_SLAB.withPropertiesOf(state);
                }
            }
            return state;
        });
    }

    private static Block bankV6RidgeBlock(BankPalette palette) {
        return palette.roofCap() == Blocks.SNOW_BLOCK ? Blocks.DARK_OAK_PLANKS : palette.roofCap();
    }

    /** Headless regression: the final two palette refinements cannot alter the reviewed geometry. */
    static void validateBankV6MaterialRefinement() {
        for (VillageArchitecture.BiomeDialect dialect : List.of(
                VillageArchitecture.BiomeDialect.TAIGA, VillageArchitecture.BiomeDialect.SNOWY)) {
            BankPalette palette = paletteFor(dialect);
            BankCivicFinish previousFinish = dialect == VillageArchitecture.BiomeDialect.TAIGA
                    ? new BankCivicFinish(Blocks.MOSSY_STONE_BRICKS, Blocks.STONE_BRICKS,
                            Blocks.MOSSY_STONE_BRICK_STAIRS, Blocks.MOSSY_STONE_BRICK_SLAB,
                            Blocks.STRIPPED_SPRUCE_LOG, Blocks.COBBLED_DEEPSLATE_SLAB)
                    : new BankCivicFinish(Blocks.CALCITE, Blocks.POLISHED_DIORITE,
                            Blocks.POLISHED_DIORITE_STAIRS, Blocks.POLISHED_DIORITE_SLAB,
                            Blocks.STRIPPED_DARK_OAK_LOG, Blocks.POLISHED_DIORITE_SLAB);
            Map<BlockPos, BlockState> before = new LinkedHashMap<>();
            for (BankPlacement placement : legacyBankPlanV5(BlockPos.ZERO, palette)) {
                before.put(placement.position(), placement.state());
            }
            appendBankV6Facades(before, BlockPos.ZERO, palette, previousFinish);
            appendBankV6Belfry(before, BlockPos.ZERO, palette, previousFinish);
            appendBankV6RecordRoom(before, BlockPos.ZERO, palette, previousFinish);
            Map<BlockPos, BlockState> after = new LinkedHashMap<>();
            for (BankPlacement placement : legacyBankPlanV6(BlockPos.ZERO, palette)) {
                after.put(placement.position(), placement.state());
            }
            if (!before.keySet().equals(after.keySet())) {
                throw new IllegalStateException("Final Bank material refinement changed its envelope: " + dialect);
            }
            int changed = 0;
            for (Map.Entry<BlockPos, BlockState> entry : before.entrySet()) {
                BlockPos pos = entry.getKey();
                BlockState old = entry.getValue();
                BlockState current = after.get(pos);
                if (!old.getCollisionShape(EmptyBlockGetter.INSTANCE, pos).toAabbs().equals(
                                current.getCollisionShape(EmptyBlockGetter.INSTANCE, pos).toAabbs())
                        || old.getLightEmission() != current.getLightEmission()) {
                    throw new IllegalStateException("Final Bank material refinement changed shape/light at "
                            + dialect + '/' + pos);
                }
                if (!old.equals(current)) {
                    changed++;
                }
            }
            if (changed == 0) {
                throw new IllegalStateException("Final Bank material refinement did not apply: " + dialect);
            }
            System.out.println("PASS Bank v6 material-only refinement " + dialect + ": "
                    + changed + " changed states, identical occupied cells/collision/emission");
        }
    }

    /** Separate v6 finish roles keep the complete v2-v5 palette and plans frozen. */
    private static BankCivicFinish bankCivicFinish(BankPalette palette) {
        if (palette.foundation() == Blocks.SMOOTH_SANDSTONE) {
            return new BankCivicFinish(Blocks.CUT_SANDSTONE, Blocks.CHISELED_RED_SANDSTONE,
                    Blocks.RED_SANDSTONE_STAIRS, Blocks.RED_SANDSTONE_SLAB,
                    Blocks.SMOOTH_SANDSTONE, Blocks.CUT_COPPER_SLAB.waxed().unaffected());
        }
        if (palette.door() == Blocks.ACACIA_DOOR) {
            return new BankCivicFinish(Blocks.SMOOTH_SANDSTONE, Blocks.POLISHED_ANDESITE,
                    Blocks.STONE_BRICK_STAIRS, Blocks.STONE_BRICK_SLAB,
                    Blocks.STRIPPED_DARK_OAK_LOG, Blocks.CUT_COPPER_SLAB.waxed().weathered());
        }
        if (palette.facadePier() == Blocks.STRIPPED_DARK_OAK_LOG) {
            return new BankCivicFinish(Blocks.CALCITE, Blocks.POLISHED_ANDESITE,
                    Blocks.POLISHED_ANDESITE_STAIRS, Blocks.POLISHED_ANDESITE_SLAB,
                    Blocks.STRIPPED_DARK_OAK_LOG, Blocks.DEEPSLATE_TILE_SLAB);
        }
        if (palette.door() == Blocks.SPRUCE_DOOR) {
            return new BankCivicFinish(Blocks.STONE_BRICKS, Blocks.STONE_BRICKS,
                    Blocks.STONE_BRICK_STAIRS, Blocks.STONE_BRICK_SLAB,
                    Blocks.STRIPPED_SPRUCE_LOG, Blocks.COBBLED_DEEPSLATE_SLAB);
        }
        return new BankCivicFinish(Blocks.STONE_BRICKS, Blocks.POLISHED_ANDESITE,
                Blocks.STONE_BRICK_STAIRS, Blocks.STONE_BRICK_SLAB,
                Blocks.STRIPPED_OAK_LOG, Blocks.CUT_COPPER_SLAB.waxed().weathered());
    }

    private record BankCivicFinish(
            Block wall, Block trim, Block archStairs, Block archSlab,
            Block belfryPier, Block cap) {
    }

    /** Rehangs the two inherited chest-top counter lamps without changing any legacy plan. */
    private static void appendBankV5SupportedCounterPendants(
            Map<BlockPos, BlockState> authored, BlockPos origin) {
        int centerX = BANK_WIDTH / 2;
        int counterZ = BANK_DEPTH - 3;
        for (int x : new int[] {centerX - 2, centerX + 2}) {
            authored.put(origin.offset(x, 2, counterZ),
                    Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
            for (int y = 3; y <= 5; y++) {
                authored.put(origin.offset(x, y, counterZ),
                        Blocks.IRON_CHAIN.defaultBlockState()
                                .setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y));
            }
        }
    }

    /** Recasts broad wall planes as ordered civic bays without changing the interior footprint. */
    private static void appendBankV5CivicEnvelope(
            Map<BlockPos, BlockState> authored,
            BlockPos origin,
            BankPalette palette) {
        int centerX = BANK_WIDTH / 2;
        BlockState lowerDoor = palette.door().defaultBlockState()
                .setValue(DoorBlock.FACING, Direction.NORTH)
                .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
        for (int y = 1; y <= 4; y++) {
            for (int x = 0; x < BANK_WIDTH; x++) {
                for (int z = 0; z < BANK_DEPTH; z++) {
                    if (x != 0 && x != BANK_WIDTH - 1
                            && z != 0 && z != BANK_DEPTH - 1) {
                        continue;
                    }
                    BlockPos position = origin.offset(x, y, z);
                    boolean entrance = z == 0 && x == centerX && y <= 3;
                    if (entrance) {
                        if (y == 1) {
                            authored.put(position, lowerDoor);
                        } else if (y == 2) {
                            authored.put(position,
                                    lowerDoor.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
                        } else {
                            authored.put(position, connectedBankPaneState(false));
                        }
                        continue;
                    }
                    if (isBankWindowCell(x, y, z)) {
                        authored.put(position,
                                connectedBankPaneState(x == 0 || x == BANK_WIDTH - 1));
                        continue;
                    }
                    boolean corner = (x == 0 || x == BANK_WIDTH - 1)
                            && (z == 0 || z == BANK_DEPTH - 1);
                    boolean pier = bankV5FacadePier(x, z);
                    Block block = z == 0 && x == centerX && y == 4
                            ? Blocks.GLAZED_TERRACOTTA.green()
                            : corner || pier
                                    ? palette.facadePier()
                                    : y == 1
                                            ? palette.civicPlinth()
                                            : y == 4
                                                    ? palette.civicCornice()
                                                    : palette.civicWall();
                    authored.put(position, block.defaultBlockState());
                }
            }
        }
    }

    private static boolean bankV5FacadePier(int x, int z) {
        if (z == 0 || z == BANK_DEPTH - 1) {
            return x == 1 || x == 4 || x == BANK_WIDTH - 5 || x == BANK_WIDTH - 2;
        }
        if (x == 0 || x == BANK_WIDTH - 1) {
            return z == 2 || z == BANK_DEPTH / 2 || z == BANK_DEPTH - 3;
        }
        return false;
    }

    /**
     * Projects masonry reveals around the two lobby windows. The original panes remain one block
     * behind the new sills, so the facade gains real depth without shrinking the public hall.
     */
    private static void appendBankV5WindowBays(
            Map<BlockPos, BlockState> authored,
            BlockPos origin,
            BankPalette palette) {
        for (int windowX : new int[] {2, BANK_WIDTH - 3}) {
            // Version four used these cells for a loose bell/lantern pair. Version five composes
            // both objects in the forecourt instead of leaving them against the glazing.
            authored.remove(origin.offset(windowX, 1, -1));
            authored.remove(origin.offset(windowX, 2, -1));
            for (int x = windowX - 1; x <= windowX + 1; x++) {
                authored.put(origin.offset(x, 0, -1),
                        palette.foundation().defaultBlockState());
                authored.put(origin.offset(x, 4, -1),
                        palette.civicCornice().defaultBlockState());
            }
            for (int frameX : new int[] {windowX - 1, windowX + 1}) {
                for (int y = 1; y <= 3; y++) {
                    authored.put(origin.offset(frameX, y, -1),
                            palette.facadePier().defaultBlockState());
                }
            }
            authored.put(origin.offset(windowX, 1, -1),
                    palette.ledgerAccent().defaultBlockState());
        }
    }

    /** Keeps the exact three-wide stair/landing clear while giving the Bank a composed civic yard. */
    private static void appendBankV5Forecourt(
            Map<BlockPos, BlockState> authored,
            BlockPos origin,
            BankPalette palette) {
        int centerX = BANK_WIDTH / 2;
        for (int z = BANK_PLOT_MIN_Z; z <= -2; z++) {
            for (int x = 0; x < BANK_WIDTH; x++) {
                if (x >= centerX - BANK_ENTRANCE_HALF_WIDTH
                        && x <= centerX + BANK_ENTRANCE_HALF_WIDTH) {
                    continue;
                }
                authored.put(origin.offset(x, 0, z),
                        palette.forecourt().defaultBlockState());
            }
        }

        // A public bell and ledger lamp terminate the two paved wings. Both have full-block,
        // center-bearing supports and remain outside the approach's two-block-clear envelope.
        for (int x : new int[] {2, BANK_WIDTH - 3}) {
            authored.put(origin.offset(x, 1, -3),
                    palette.facadePier().defaultBlockState());
        }
        authored.put(origin.offset(2, 2, -3), Blocks.BELL.defaultBlockState());
        authored.put(origin.offset(BANK_WIDTH - 3, 2, -3),
                Blocks.LANTERN.defaultBlockState());

        // Low benches and branded end stones make the paved wings useful without roofing a new
        // hostile-spawn surface or narrowing the center stair.
        for (int x : new int[] {0, 1, 3, BANK_WIDTH - 4, BANK_WIDTH - 2, BANK_WIDTH - 1}) {
            authored.put(origin.offset(x, 1, -4), palette.roof().defaultBlockState());
        }
        authored.put(origin.offset(4, 1, -3),
                Blocks.GLAZED_TERRACOTTA.green().defaultBlockState());
        authored.put(origin.offset(BANK_WIDTH - 5, 1, -3),
                Blocks.GLAZED_TERRACOTTA.green().defaultBlockState());
    }

    /** Builds three recessed ledger bays and a raised rear civic pediment. */
    private static void appendBankV5RearLedgerElevation(
            Map<BlockPos, BlockState> authored,
            BlockPos origin,
            BankPalette palette) {
        int centerX = BANK_WIDTH / 2;
        for (int x = 1; x < BANK_WIDTH - 1; x++) {
            authored.put(origin.offset(x, 0, BANK_DEPTH),
                    palette.foundation().defaultBlockState());
        }
        for (int windowX : new int[] {3, centerX, BANK_WIDTH - 4}) {
            for (int frameX : new int[] {windowX - 1, windowX + 1}) {
                for (int y = 1; y <= 3; y++) {
                    authored.put(origin.offset(frameX, y, BANK_DEPTH),
                            palette.facadePier().defaultBlockState());
                }
            }
            authored.put(origin.offset(windowX, 1, BANK_DEPTH),
                    (windowX == centerX
                                    ? Blocks.GLAZED_TERRACOTTA.green()
                                    : palette.ledgerAccent())
                            .defaultBlockState());
        }

        // Two warm pendants hang directly from the retained full roof backing at the rear corners.
        for (int x : new int[] {0, BANK_WIDTH - 1}) {
            authored.put(origin.offset(x, 3, BANK_DEPTH),
                    Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        }

        // A compact gable centered over the rear ledger window gives the service elevation a
        // deliberate civic termination rather than a flat strip beneath the main roof.
        for (int x = centerX - 2; x <= centerX + 2; x++) {
            authored.put(origin.offset(x, 5, BANK_DEPTH),
                    palette.civicCornice().defaultBlockState());
        }
        for (int x = centerX - 1; x <= centerX + 1; x++) {
            authored.put(origin.offset(x, 6, BANK_DEPTH),
                    (x == centerX ? Blocks.GLAZED_TERRACOTTA.green() : palette.civicWall())
                            .defaultBlockState());
        }
        authored.put(origin.offset(centerX, 7, BANK_DEPTH),
                palette.facadePier().defaultBlockState());
        authored.put(origin.offset(centerX - 2, 6, BANK_DEPTH),
                palette.roofStairs().defaultBlockState()
                        .setValue(StairBlock.FACING, Direction.WEST));
        authored.put(origin.offset(centerX + 2, 6, BANK_DEPTH),
                palette.roofStairs().defaultBlockState()
                        .setValue(StairBlock.FACING, Direction.EAST));
        authored.put(origin.offset(centerX - 1, 7, BANK_DEPTH),
                palette.roofStairs().defaultBlockState()
                        .setValue(StairBlock.FACING, Direction.WEST));
        authored.put(origin.offset(centerX + 1, 7, BANK_DEPTH),
                palette.roofStairs().defaultBlockState()
                        .setValue(StairBlock.FACING, Direction.EAST));
        authored.put(origin.offset(centerX, 8, BANK_DEPTH),
                palette.roofCap().defaultBlockState());
    }

    /** Replaces the flat v4 cap with an integrated dormer, glazed bell cupola, and ridge course. */
    private static void appendBankV5DormerAndCupola(
            Map<BlockPos, BlockState> authored,
            BlockPos origin,
            BankPalette palette) {
        int centerX = BANK_WIDTH / 2;
        int centerZ = BANK_DEPTH / 2;

        // Remove only v4's former ridge/cap composition; the full weather backing underneath is
        // retained verbatim and remains the structural support for the version-five skyline.
        for (int x = -1; x <= BANK_WIDTH; x++) {
            authored.remove(origin.offset(x, 9, centerZ));
        }
        for (int x = centerX - 2; x <= centerX + 2; x++) {
            for (int z = centerZ - 2; z <= centerZ + 2; z++) {
                authored.remove(origin.offset(x, 10, z));
            }
        }
        for (int x = centerX - 1; x <= centerX + 1; x++) {
            for (int z = centerZ - 1; z <= centerZ + 1; z++) {
                authored.remove(origin.offset(x, 9, z));
            }
        }

        // A real front-facing dormer rises from the lower roof slope. Full gable backing prevents
        // visual holes at every angle; stair bands and a capped ridge articulate its silhouette.
        for (int z = -1; z <= 3; z++) {
            authored.put(origin.offset(centerX - 2, 8, z),
                    palette.roofStairs().defaultBlockState()
                            .setValue(StairBlock.FACING, Direction.WEST));
            authored.put(origin.offset(centerX + 2, 8, z),
                    palette.roofStairs().defaultBlockState()
                            .setValue(StairBlock.FACING, Direction.EAST));
            authored.put(origin.offset(centerX - 1, 9, z),
                    palette.roofStairs().defaultBlockState()
                            .setValue(StairBlock.FACING, Direction.WEST));
            authored.put(origin.offset(centerX + 1, 9, z),
                    palette.roofStairs().defaultBlockState()
                            .setValue(StairBlock.FACING, Direction.EAST));
            authored.put(origin.offset(centerX, 10, z),
                    palette.roofCap().defaultBlockState());
        }
        for (int z = 0; z <= 2; z++) {
            for (int x = centerX - 2; x <= centerX + 2; x++) {
                Block lower = x == centerX - 2 || x == centerX + 2
                        ? palette.facadePier()
                        : z == 0 && x == centerX
                                ? Blocks.STAINED_GLASS.green()
                                : palette.civicWall();
                authored.put(origin.offset(x, 7, z), lower.defaultBlockState());
            }
            for (int x = centerX - 1; x <= centerX + 1; x++) {
                Block upper = z == 0 && x == centerX
                        ? Blocks.GLAZED_TERRACOTTA.green()
                        : palette.civicWall();
                authored.put(origin.offset(x, 8, z), upper.defaultBlockState());
            }
            authored.put(origin.offset(centerX, 9, z),
                    palette.facadePier().defaultBlockState());
        }

        // The long ridge remains continuous on both sides of the cupola.
        for (int x = -1; x <= BANK_WIDTH; x++) {
            if (x < centerX - 1 || x > centerX + 1) {
                authored.put(origin.offset(x, 9, centerZ),
                        palette.roofCap().defaultBlockState());
            }
        }

        // A complete 3x3 masonry plinth replaces the partial stair surface beneath the bell
        // chamber. This prevents angle-dependent seams and gives every pier, window, and the bell
        // itself a full-block bearing course. The five-wide cap remains stair-shaped above it.
        for (int z = centerZ - 1; z <= centerZ + 1; z++) {
            for (int x = centerX - 1; x <= centerX + 1; x++) {
                authored.put(origin.offset(x, 8, z),
                        palette.cupolaBase().defaultBlockState());
            }
        }
        for (int[] corner : new int[][] {
                {centerX - 1, centerZ - 1},
                {centerX + 1, centerZ - 1},
                {centerX - 1, centerZ + 1},
                {centerX + 1, centerZ + 1}
        }) {
            authored.put(origin.offset(corner[0], 9, corner[1]),
                    palette.facadePier().defaultBlockState());
        }
        for (int[] window : new int[][] {
                {centerX, centerZ - 1},
                {centerX, centerZ + 1},
                {centerX - 1, centerZ},
                {centerX + 1, centerZ}
        }) {
            authored.put(origin.offset(window[0], 9, window[1]),
                    Blocks.STAINED_GLASS.green().defaultBlockState());
        }
        authored.put(origin.offset(centerX, 9, centerZ), Blocks.BELL.defaultBlockState());

        for (int x = centerX - 2; x <= centerX + 2; x++) {
            authored.put(origin.offset(x, 10, centerZ - 2),
                    palette.roofStairs().defaultBlockState()
                            .setValue(StairBlock.FACING, Direction.NORTH));
            authored.put(origin.offset(x, 10, centerZ + 2),
                    palette.roofStairs().defaultBlockState()
                            .setValue(StairBlock.FACING, Direction.SOUTH));
        }
        for (int z = centerZ - 1; z <= centerZ + 1; z++) {
            authored.put(origin.offset(centerX - 2, 10, z),
                    palette.roofCap().defaultBlockState());
            authored.put(origin.offset(centerX + 2, 10, z),
                    palette.roofCap().defaultBlockState());
            for (int x = centerX - 1; x <= centerX + 1; x++) {
                authored.put(origin.offset(x, 10, z),
                        palette.roof().defaultBlockState());
            }
        }
        for (int x = centerX - 1; x <= centerX + 1; x++) {
            authored.put(origin.offset(x, 11, centerZ),
                    (x == centerX
                                    ? Blocks.GLAZED_TERRACOTTA.green()
                                    : palette.roofCap())
                            .defaultBlockState());
        }
    }

    /** Shares the active immutable Bank identity with opt-in review manifests. */
    static int galleryBankStructureVersion() {
        return BANK_STRUCTURE_VERSION;
    }

    /** Resolves the exact production Bank blueprint for the opt-in structure review gallery. */
    static List<StructureGalleryBlock> galleryBankBlueprint(
            BlockPos origin, VillageArchitecture.BiomeDialect dialect) {
        if (!StructureGallery.enabled()) {
            throw new IllegalStateException("Structure gallery JVM opt-in is not enabled");
        }
        return bankPlan(origin, paletteFor(dialect)).stream()
                .map(placement -> new StructureGalleryBlock(
                        placement.position(), placement.state()))
                .toList();
    }

    private static boolean isBankWindowCell(int x, int y, int z) {
        return (y == 2 || y == 3)
                && ((z == 0 && (x == 2 || x == BANK_WIDTH - 3))
                        || (z == BANK_DEPTH - 1
                                && (x == 3
                                        || x == BANK_WIDTH / 2
                                        || x == BANK_WIDTH - 4))
                        || ((x == 0 || x == BANK_WIDTH - 1)
                                && (z == 3 || z == BANK_DEPTH - 4)));
    }

    private static BlockState connectedBankPaneState(boolean sideWall) {
        BlockState state = Blocks.STAINED_GLASS_PANE.green().defaultBlockState();
        return sideWall
                ? state.setValue(CrossCollisionBlock.NORTH, true)
                        .setValue(CrossCollisionBlock.SOUTH, true)
                : state.setValue(CrossCollisionBlock.EAST, true)
                        .setValue(CrossCollisionBlock.WEST, true);
    }

    /** Validates every current biome Bank blueprint during both loader smoke tests. */
    static void validateBankTemplate(ServerLevel level) {
        validateBankDialectPaletteContract();
    }

    /**
     * Validates one complete current Bank blueprint. Keeping the exact gate independent of a
     * {@link ServerLevel} lets production admission and the loader-neutral structure verifier run
     * the same geometry, access, attachment, and night-light proof for every biome dialect.
     */
    private static void validateCurrentBankBlueprint(
            BankPalette legacyPalette,
            String snapshotId,
            boolean allowUnregisteredDeskLectern) {
        BankPalette palette = bankV7Palette(legacyPalette);
        BlockPos origin = new BlockPos(0, 64, 0);
        List<BankPlacement> plan = bankPlan(origin, palette);
        if (plan.isEmpty() || plan.size() > 1_200) {
            throw new IllegalStateException("Invalid Village Bank template size: " + plan.size());
        }
        Set<BlockPos> occupied = new HashSet<>();
        Map<BlockPos, BlockState> authored = new HashMap<>();
        int desks = 0;
        int storage = 0;
        int lights = 0;
        int jobSites = 0;
        int barrels = 0;
        int doorHalves = 0;
        int entranceStairs = 0;
        int panes = 0;
        for (BankPlacement placement : plan) {
            BlockPos relative = placement.position().subtract(origin);
            if (!occupied.add(relative)) {
                throw new IllegalStateException(
                        "Duplicate Village Bank placement at " + relative);
            }
            if (relative.getX() < -1
                    || relative.getX() > BANK_WIDTH
                    || relative.getY() < 0
                    || relative.getY() > BANK_HEIGHT
                    || relative.getZ() < BANK_PLOT_MIN_Z
                    || relative.getZ() > BANK_DEPTH) {
                throw new IllegalStateException(
                        "Village Bank placement escaped its preflight volume at " + relative);
            }
            authored.put(relative, placement.state());
            if (isExpectedCurrentBankDesk(
                    placement.state(), allowUnregisteredDeskLectern)) {
                desks++;
            }
            if (placement.state().is(Blocks.BARREL)) {
                barrels++;
            }
            if (placement.state().is(Blocks.CHEST)
                    || placement.state().is(Blocks.ENDER_CHEST)
                    || placement.state().is(Blocks.CHISELED_BOOKSHELF)) {
                storage++;
            }
            if (placement.state().is(Blocks.LANTERN)) {
                lights++;
            }
            if (placement.state().is(palette.door())) {
                doorHalves++;
            }
            if (relative.getY() == 0
                    && relative.getZ() == -2
                    && placement.state().is(palette.stairs())) {
                entranceStairs++;
            }
            if (placement.state().is(Blocks.STAINED_GLASS_PANE.green())) {
                panes++;
                boolean sideWall = relative.getX() == 0
                        || relative.getX() == BANK_WIDTH - 1;
                boolean correctConnections = sideWall
                        ? placement.state().getValue(CrossCollisionBlock.NORTH)
                                && placement.state().getValue(CrossCollisionBlock.SOUTH)
                                && !placement.state().getValue(CrossCollisionBlock.EAST)
                                && !placement.state().getValue(CrossCollisionBlock.WEST)
                        : placement.state().getValue(CrossCollisionBlock.EAST)
                                && placement.state().getValue(CrossCollisionBlock.WEST)
                                && !placement.state().getValue(CrossCollisionBlock.NORTH)
                                && !placement.state().getValue(CrossCollisionBlock.SOUTH);
                if (!correctConnections) {
                    throw new IllegalStateException(
                            "Village Bank pane lost its authored wall connections at " + relative);
                }
            }
            boolean mappedJobSite = PoiTypes.forState(placement.state())
                    .map(holder -> holder.is(PoiTypeTags.ACQUIRABLE_JOB_SITE))
                    .orElse(false);
            // A plain headless bootstrap has neither loader POI registration nor a populated
            // dynamic POI state map. Count its one deliberately accepted lectern proxy as the one
            // Desk job site; the production path never enables this exception.
            boolean headlessDeskJobSite = allowUnregisteredDeskLectern
                    && placement.state().is(Blocks.LECTERN);
            if (mappedJobSite || headlessDeskJobSite) {
                jobSites++;
            }
        }
        BlockPos bankerAnchor = origin.offset(BANK_WIDTH / 2, 1, BANK_DEPTH - 2);
        BlockPos counter = bankerAnchor.north();
        BlockState counterState = plan.stream()
                .filter(placement -> placement.position().equals(counter))
                .map(BankPlacement::state)
                .findFirst()
                .orElse(Blocks.AIR.defaultBlockState());
        if (desks != 1
                || !isExpectedCurrentBankDesk(counterState, allowUnregisteredDeskLectern)
                || storage < 15
                || lights < 8
                || barrels != 0
                || jobSites != 1
                || doorHalves != 2
                || entranceStairs != 3
                || panes != 19) {
            throw new IllegalStateException(
                    "Village Bank blueprint lost its entrance, desk-only job-site, storage, or "
                            + "lighting contract (desks=" + desks
                            + ", storage=" + storage
                            + ", lights=" + lights
                            + ", barrels=" + barrels
                            + ", jobSites=" + jobSites
                            + ", doorHalves=" + doorHalves
                            + ", entranceStairs=" + entranceStairs
                            + ", panes=" + panes + ")");
        }
        BlockPos entrance = new BlockPos(BANK_WIDTH / 2, 1, 0);
        BlockState lowerDoor = authored.get(entrance);
        BlockState upperDoor = authored.get(entrance.above());
        BlockState stair = authored.get(new BlockPos(BANK_WIDTH / 2, 0, -2));
        if (lowerDoor == null
                || !lowerDoor.is(palette.door())
                || lowerDoor.getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER
                || upperDoor == null
                || !upperDoor.is(palette.door())
                || upperDoor.getValue(DoorBlock.HALF) != DoubleBlockHalf.UPPER
                || stair == null
                || !stair.is(palette.stairs())
                || stair.getValue(StairBlock.FACING) != Direction.SOUTH) {
            throw new IllegalStateException("Village Bank entrance lost its door or terrain stair");
        }
        for (int x = BANK_WIDTH / 2 - BANK_ENTRANCE_HALF_WIDTH;
                x <= BANK_WIDTH / 2 + BANK_ENTRANCE_HALF_WIDTH;
                x++) {
            BlockState landing = authored.get(new BlockPos(x, 0, -1));
            BlockState approach = authored.get(new BlockPos(x, 0, -2));
            if (landing == null
                    || !landing.is(palette.foundation())
                    || approach == null
                    || !approach.is(palette.stairs())
                    || approach.getValue(StairBlock.FACING) != Direction.SOUTH) {
                throw new IllegalStateException(
                        "Village Bank entrance lost its three-wide landing or top stair at x=" + x);
            }
            for (int z = -2; z <= -1; z++) {
                if (!isBankWalkableCell(authored.get(new BlockPos(x, 1, z)))
                        || !isBankWalkableCell(authored.get(new BlockPos(x, 2, z)))) {
                    throw new IllegalStateException(
                            "Village Bank portico obstructs its three-wide approach at "
                                    + new BlockPos(x, 1, z));
                }
            }
        }
        List<TerrainFoundationPlan.Cell> authoredCells = authored.keySet().stream()
                .map(position -> new TerrainFoundationPlan.Cell(
                        position.getX(), position.getY(), position.getZ()))
                .toList();
        if (!TerrainFoundationPlan.appendSupportCells(authoredCells, 0).isEmpty()) {
            throw new IllegalStateException(
                    "Village Bank blueprint contains an outdoor detail without a y=0 footing");
        }
        for (int x : new int[] {
                2, BANK_WIDTH / 2 - 2, BANK_WIDTH / 2 + 2
        }) {
            BlockState footing = authored.get(new BlockPos(x, 0, -1));
            if (footing == null || !footing.is(palette.foundation())) {
                throw new IllegalStateException(
                        "Village Bank porch or bell lost its foundation at x=" + x);
            }
        }
        for (int z = -1; z <= BANK_DEPTH; z++) {
            int distanceFromEave = Math.min(z + 1, BANK_DEPTH - z);
            int roofY = 5 + (distanceFromEave + 1) / 2;
            for (int x = -1; x <= BANK_WIDTH; x++) {
                BlockState backingState = authored.get(new BlockPos(x, roofY - 1, z));
                boolean centralMount = x == BANK_WIDTH / 2 && z == BANK_DEPTH / 2;
                boolean frontBayBacking = z == -1
                        && ((x >= 1 && x <= 3)
                                || (x >= BANK_WIDTH - 4 && x <= BANK_WIDTH - 2));
                if (backingState == null
                        || !(centralMount
                                ? backingState.is(palette.corner())
                                : frontBayBacking
                                        ? backingState.is(bankCivicFinish(palette).trim())
                                        : backingState.is(palette.roofDeck()))) {
                    throw new IllegalStateException(
                            "Village Bank roof backing is not continuous at "
                                    + new BlockPos(x, roofY - 1, z));
                }
                BlockState roofState = authored.get(new BlockPos(x, roofY, z));
                boolean ridge = z == BANK_DEPTH / 2;
                boolean chimneyMount = x == 2 && z == BANK_DEPTH - 4;
                boolean cupolaPlinth = x >= BANK_WIDTH / 2 - 1
                        && x <= BANK_WIDTH / 2 + 1
                        && z >= BANK_DEPTH / 2 - 1
                        && z <= BANK_DEPTH / 2 + 1;
                boolean dormerBase = z == 2
                        && x >= BANK_WIDTH / 2 - 2
                        && x <= BANK_WIDTH / 2 + 2;
                boolean rearPedimentBase = z == BANK_DEPTH
                        && x >= BANK_WIDTH / 2 - 2
                        && x <= BANK_WIDTH / 2 + 2;
                boolean correctSurface = cupolaPlinth
                        ? roofState != null && roofState.is(palette.cupolaBase())
                        : chimneyMount
                        ? roofState != null && roofState.is(palette.chimney())
                        : dormerBase
                                ? roofState != null
                                        && (roofState.is(palette.facadePier())
                                                || roofState.is(palette.civicWall()))
                        : rearPedimentBase
                                ? roofState != null && roofState.is(bankCivicFinish(palette).trim())
                        : ridge
                                ? roofState != null && roofState.is(palette.roofDeck())
                                : roofState != null
                                        && roofState.is(palette.roofStairs())
                                        && roofState.getValue(StairBlock.FACING)
                                                == (z < BANK_DEPTH / 2
                                                        ? Direction.SOUTH
                                                        : Direction.NORTH);
                if (!correctSurface) {
                    throw new IllegalStateException(
                            "Village Bank roof is not continuous at "
                                    + new BlockPos(x, roofY, z));
                }
            }
        }
        validateBankV10Skyline(authored, palette);
        validateBankForecourtSeats(authored, palette);
        validateBankV7ExteriorGardens(authored, palette);
        for (int x = 0; x < BANK_WIDTH; x++) {
            if (!authored.containsKey(new BlockPos(x, 5, 0))
                    || !authored.containsKey(new BlockPos(x, 5, BANK_DEPTH - 1))) {
                throw new IllegalStateException(
                        "Village Bank facade or rear wall is open below the roof at x=" + x);
            }
        }
        validateBankInteriorZoning(authored, palette, allowUnregisteredDeskLectern);
        validateBankInteriorLighting(authored, snapshotId);
    }

    private static boolean isExpectedCurrentBankDesk(
            BlockState state, boolean allowUnregisteredDeskLectern) {
        return BankerProfessionSupport.isExchangeDesk(state)
                || (allowUnregisteredDeskLectern && state != null && state.is(Blocks.LECTERN));
    }

    private static volatile boolean bankTemplateValidated;

    /** Fail closed before any production Bank mutation if one biome dialect is unsafe. */
    private static void ensureBankTemplateValidated() {
        if (bankTemplateValidated) {
            return;
        }
        synchronized (VillageBankManager.class) {
            if (!bankTemplateValidated) {
                validateBankDialectPaletteContract();
                bankTemplateValidated = true;
            }
        }
    }

    /**
     * Ensures every biome has a visible signature and independently passes the exact night-light
     * proof. The smoke level's biome must not stand in for palettes whose block states differ.
     */
    static void validateBankDialectPaletteContract() {
        validateBankDialectPaletteContract(false);
    }

    /**
     * Headless JVMs have bootstrapped vanilla's frozen registries before this verifier starts, so
     * loader registration cannot install the custom Desk there. The safe lectern fallback has the
     * same structural position and light-occlusion behavior; production and loader smoke tests
     * continue to require the actual Exchange Desk through the no-argument gate above.
     */
    static void validateHeadlessBankDialectStructureContract() {
        validateBankDialectPaletteContract(true);
    }

    private static void validateBankDialectPaletteContract(
            boolean allowUnregisteredDeskLectern) {
        Set<List<Block>> signatures = new HashSet<>();
        for (VillageArchitecture.BiomeDialect dialect
                : VillageArchitecture.BiomeDialect.values()) {
            BankPalette palette = bankV7Palette(paletteFor(dialect));
            signatures.add(List.of(
                    palette.foundation(),
                    palette.wall(),
                    palette.corner(),
                    palette.roofDeck(),
                    palette.roofStairs(),
                    palette.secondaryTrim(),
                    palette.stairs(),
                    palette.facadePier(),
                    palette.civicPlinth(),
                    palette.civicCornice(),
                    palette.civicWall(),
                    palette.ledgerAccent(),
                    palette.forecourt(),
                    palette.roofCap(),
                    palette.cupolaBase()));

            validateCurrentBankBlueprint(
                    palette,
                    "village-bank-v7/" + dialect.id() + "/night-interior",
                    allowUnregisteredDeskLectern);
        }
        if (signatures.size() != VillageArchitecture.BiomeDialect.values().length) {
            throw new IllegalStateException(
                    "Village Bank biome dialects lost distinct trim or roof signatures");
        }
        BankPalette snowy = bankV7Palette(paletteFor(VillageArchitecture.BiomeDialect.SNOWY));
        BankPalette taiga = bankV7Palette(paletteFor(VillageArchitecture.BiomeDialect.TAIGA));
        if (snowy.secondaryTrim() == taiga.secondaryTrim()
                || snowy.roofStairs() == taiga.roofStairs()
                || snowy.stairs() == taiga.stairs()
                || snowy.civicWall() == taiga.civicWall()
                || snowy.facadePier() == taiga.facadePier()
                || snowy.roofCap() == taiga.roofCap()) {
            throw new IllegalStateException(
                    "Snowy Village Bank is no longer visually distinct from Taiga");
        }
    }

    /** Small real-block planters must remain grounded, persistent, and below the window reveals. */
    private static void validateBankV7ExteriorGardens(
            Map<BlockPos, BlockState> authored, BankPalette palette) {
        for (int x : new int[] {-1, BANK_WIDTH}) {
            for (int z : new int[] {0, 1, BANK_DEPTH - 2, BANK_DEPTH - 1}) {
                BlockPos footing = new BlockPos(x, 0, z);
                BlockState base = authored.get(footing);
                BlockState plant = authored.get(footing.above());
                if (base == null || !base.is(palette.foundation())
                        || !base.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, footing)
                        || plant == null
                        || !(plant.getBlock() instanceof net.minecraft.world.level.block.FlowerPotBlock
                                || plant.getBlock() instanceof LeavesBlock)
                        || (plant.getBlock() instanceof LeavesBlock
                                && !plant.getValue(LeavesBlock.PERSISTENT))
                        || !isBankWalkableCell(authored.get(footing.above(2)))) {
                    throw new IllegalStateException("Village Bank v7 planting lost its low, durable footing at "
                            + footing);
                }
            }
        }
        for (BlockState state : authored.values()) {
            if (state.is(Blocks.TARGET)) {
                throw new IllegalStateException("A non-training Village Bank gained target decoration");
            }
        }
    }

    /** Reject backwards seats at admission, before any current Bank is placed. */
    private static void validateBankForecourtSeats(
            Map<BlockPos, BlockState> authored, BankPalette palette) {
        for (int x : new int[] {0, 1, 3, BANK_WIDTH - 4, BANK_WIDTH - 2, BANK_WIDTH - 1}) {
            BlockPos position = new BlockPos(x, 1, -4);
            BlockState expected = palette.roofStairs().defaultBlockState()
                    .setValue(StairBlock.FACING, Direction.SOUTH);
            if (!expected.equals(authored.get(position))) {
                throw new IllegalStateException("Village Bank bench must open toward the front path at " + position);
            }
        }
    }

    /** Proves the new roof feature has continuous bearing, enclosed glazing and one weather cap. */
    private static void validateBankV10Skyline(
            Map<BlockPos, BlockState> authored, BankPalette palette) {
        int centerX = BANK_WIDTH / 2;
        int centerZ = BANK_DEPTH / 2;
        BankCivicFinish finish = bankCivicFinish(palette);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                requireBankBlock(authored, new BlockPos(centerX + dx, 8, centerZ + dz),
                        palette.cupolaBase(), "belfry continuous bearing");
                for (int y = 9; y <= 10; y++) {
                    Block expected = dx == 0 && dz == 0
                            ? y == 9 ? Blocks.BELL : Blocks.LANTERN
                            : Math.abs(dx) == 1 && Math.abs(dz) == 1
                                    ? finish.belfryPier() : Blocks.STAINED_GLASS.green();
                    requireBankBlock(authored, new BlockPos(centerX + dx, y, centerZ + dz),
                            expected, "belfry framed enclosure");
                }
            }
        }
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                BlockPos capPosition = new BlockPos(centerX + dx, 11, centerZ + dz);
                requireBankBlock(authored, capPosition,
                        finish.cap(), "belfry single weather cap");
                BlockState cap = authored.get(capPosition);
                if (cap.getValue(SlabBlock.TYPE)
                        != (dx == 0 && dz == 0 ? SlabType.DOUBLE : SlabType.BOTTOM)) {
                    throw new IllegalStateException(
                            "Village Bank belfry cap opened a daylight seam at " + capPosition);
                }
            }
        }
        for (int z = -1; z <= 3; z++) {
            for (int side : new int[] {-1, 1}) {
                requireBankStair(authored, new BlockPos(centerX + side * 2, 8, z),
                        palette.roofStairs(), side < 0 ? Direction.EAST : Direction.WEST,
                        "dormer lower sealed slope");
                requireBankStair(authored, new BlockPos(centerX + side, 9, z),
                        palette.roofStairs(), side < 0 ? Direction.EAST : Direction.WEST,
                        "dormer upper sealed slope");
            }
            requireBankBlock(authored, new BlockPos(centerX, 10, z),
                    bankV6RidgeBlock(palette), "dormer continuous ridge");
        }
        requireBankBlock(authored, new BlockPos(centerX, 8, BANK_DEPTH),
                bankV6RidgeBlock(palette), "rear ledger pediment crown");
    }

    /** Frozen version-five skyline contract retained as compatibility documentation. */
    private static void validateBankV5Skyline(
            Map<BlockPos, BlockState> authored, BankPalette palette) {
        int centerX = BANK_WIDTH / 2;
        int centerZ = BANK_DEPTH / 2;

        for (int z = centerZ - 1; z <= centerZ + 1; z++) {
            for (int x = centerX - 1; x <= centerX + 1; x++) {
                requireBankBlock(
                        authored,
                        new BlockPos(x, 8, z),
                        palette.cupolaBase(),
                        "cupola bearing plinth");
            }
        }

        for (int z = -1; z <= 3; z++) {
            requireBankStair(
                    authored,
                    new BlockPos(centerX - 2, 8, z),
                    palette.roofStairs(),
                    Direction.WEST,
                    "front dormer west slope");
            requireBankStair(
                    authored,
                    new BlockPos(centerX + 2, 8, z),
                    palette.roofStairs(),
                    Direction.EAST,
                    "front dormer east slope");
            requireBankStair(
                    authored,
                    new BlockPos(centerX - 1, 9, z),
                    palette.roofStairs(),
                    Direction.WEST,
                    "front dormer upper west slope");
            requireBankStair(
                    authored,
                    new BlockPos(centerX + 1, 9, z),
                    palette.roofStairs(),
                    Direction.EAST,
                    "front dormer upper east slope");
            requireBankBlock(
                    authored,
                    new BlockPos(centerX, 10, z),
                    z == 3 ? palette.roofStairs() : palette.roofCap(),
                    "front dormer ridge");
        }

        for (int[] corner : new int[][] {
                {centerX - 1, centerZ - 1},
                {centerX + 1, centerZ - 1},
                {centerX - 1, centerZ + 1},
                {centerX + 1, centerZ + 1}
        }) {
            requireBankBlock(
                    authored,
                    new BlockPos(corner[0], 9, corner[1]),
                    palette.facadePier(),
                    "cupola pier");
        }
        for (int[] window : new int[][] {
                {centerX, centerZ - 1},
                {centerX, centerZ + 1},
                {centerX - 1, centerZ},
                {centerX + 1, centerZ}
        }) {
            requireBankBlock(
                    authored,
                    new BlockPos(window[0], 9, window[1]),
                    Blocks.STAINED_GLASS.green(),
                    "cupola glazing");
        }
        requireBankBlock(
                authored,
                new BlockPos(centerX, 9, centerZ),
                Blocks.BELL,
                "cupola bell");

        for (int x = centerX - 2; x <= centerX + 2; x++) {
            requireBankStair(
                    authored,
                    new BlockPos(x, 10, centerZ - 2),
                    palette.roofStairs(),
                    Direction.NORTH,
                    "cupola north eave");
            requireBankStair(
                    authored,
                    new BlockPos(x, 10, centerZ + 2),
                    palette.roofStairs(),
                    Direction.SOUTH,
                    "cupola south eave");
        }
        for (int z = centerZ - 1; z <= centerZ + 1; z++) {
            requireBankBlock(
                    authored,
                    new BlockPos(centerX - 2, 10, z),
                    palette.roofCap(),
                    "cupola west cap");
            requireBankBlock(
                    authored,
                    new BlockPos(centerX + 2, 10, z),
                    palette.roofCap(),
                    "cupola east cap");
            for (int x = centerX - 1; x <= centerX + 1; x++) {
                requireBankBlock(
                        authored,
                        new BlockPos(x, 10, z),
                        palette.roof(),
                        "cupola weather cap");
            }
        }
        for (int x = centerX - 1; x <= centerX + 1; x++) {
            requireBankBlock(
                    authored,
                    new BlockPos(x, 11, centerZ),
                    x == centerX ? Blocks.GLAZED_TERRACOTTA.green() : palette.roofCap(),
                    "cupola crown");
        }

        requireBankBlock(
                authored,
                new BlockPos(centerX, 8, BANK_DEPTH),
                palette.roofCap(),
                "rear ledger pediment crown");
    }

    private static void requireBankBlock(
            Map<BlockPos, BlockState> authored,
            BlockPos position,
            Block expected,
            String feature) {
        BlockState state = authored.get(position);
        if (state == null || !state.is(expected)) {
            throw new IllegalStateException(
                    "Village Bank " + feature + " is missing at " + position);
        }
    }

    private static void requireBankStair(
            Map<BlockPos, BlockState> authored,
            BlockPos position,
            Block expected,
            Direction facing,
            String feature) {
        BlockState state = authored.get(position);
        if (state == null
                || !state.is(expected)
                || !(state.getBlock() instanceof StairBlock)
                || state.getValue(StairBlock.FACING) != facing) {
            throw new IllegalStateException(
                    "Village Bank " + feature + " is malformed at " + position);
        }
    }

    /** Guards the public, teller, and secure zones plus an unobstructed Desk sightline. */
    private static void validateBankInteriorZoning(
            Map<BlockPos, BlockState> authored,
            BankPalette palette,
            boolean allowUnregisteredDeskLectern) {
        int centerX = BANK_WIDTH / 2;
        int counterZ = BANK_DEPTH - 3;
        for (int z = 1; z < counterZ; z++) {
            for (int x = centerX - 1; x <= centerX + 1; x++) {
                if (!isBankWalkableCell(authored.get(new BlockPos(x, 1, z)))
                        || !isBankWalkableCell(authored.get(new BlockPos(x, 2, z)))) {
                    throw new IllegalStateException(
                            "Village Bank public aisle or Desk sightline is blocked at "
                                    + new BlockPos(x, 1, z));
                }
            }
        }
        if (!isExpectedCurrentBankDesk(
                        authored.get(new BlockPos(centerX, 1, counterZ)),
                        allowUnregisteredDeskLectern)
                || !authored.get(new BlockPos(centerX, 3, counterZ))
                        .is(Blocks.GLAZED_TERRACOTTA.green())
                || !authored.get(new BlockPos(centerX - 1, 2, counterZ))
                        .is(palette.fence())
                || !authored.get(new BlockPos(centerX + 1, 2, counterZ))
                        .is(palette.fence())) {
            throw new IllegalStateException(
                    "Village Bank Exchange Desk lost its teller-hall focal frame");
        }
        for (int frameX = 1; frameX <= 4; frameX++) {
            if (!authored.get(new BlockPos(frameX, 0, counterZ - 2))
                            .is(bankCivicFinish(palette).trim())
                    || !authored.get(new BlockPos(frameX, 3, counterZ - 2))
                            .is(bankCivicFinish(palette).archSlab())) {
                throw new IllegalStateException(
                        "Village Bank secure-record alcove lost its masonry base or lintel at x="
                                + frameX);
            }
        }
        for (int jambX : new int[] {1, 4}) {
            for (int y = 1; y <= 2; y++) {
                if (!authored.get(new BlockPos(jambX, y, counterZ - 2))
                        .is(Blocks.STONE_BRICK_WALL)) {
                    throw new IllegalStateException(
                            "Village Bank secure-record alcove lost its outer jamb at "
                                    + new BlockPos(jambX, y, counterZ - 2));
                }
            }
        }
        for (int aisleX = 2; aisleX <= 3; aisleX++) {
            for (int aisleZ = counterZ - 3; aisleZ <= counterZ - 1; aisleZ++) {
                if (!isBankWalkableCell(authored.get(new BlockPos(aisleX, 1, aisleZ)))
                        || !isBankWalkableCell(
                                authored.get(new BlockPos(aisleX, 2, aisleZ)))) {
                    throw new IllegalStateException(
                            "Village Bank secure-record alcove lost its two-wide reachable aisle at "
                                    + new BlockPos(aisleX, 1, aisleZ));
                }
            }
        }
        for (int connectorX = 4; connectorX <= centerX - 1; connectorX++) {
            if (!isBankWalkableCell(authored.get(new BlockPos(connectorX, 1, counterZ - 3)))
                    || !isBankWalkableCell(
                            authored.get(new BlockPos(connectorX, 2, counterZ - 3)))) {
                throw new IllegalStateException(
                        "Village Bank secure-record aisle is disconnected from the public hall at "
                                + new BlockPos(connectorX, 1, counterZ - 3));
            }
        }
        if (!authored.get(new BlockPos(1, 1, counterZ - 1)).is(Blocks.ENDER_CHEST)
                || !authored.get(new BlockPos(1, 1, counterZ))
                        .is(Blocks.CHISELED_BOOKSHELF)
                || !authored.get(new BlockPos(1, 2, counterZ)).is(Blocks.LANTERN)
                || !authored.get(new BlockPos(4, 1, counterZ - 1)).is(Blocks.IRON_BARS)
                || !authored.get(new BlockPos(4, 2, counterZ - 1)).is(Blocks.IRON_BARS)) {
            throw new IllegalStateException(
                    "Village Bank secure-record alcove lost its offset chest, lit ledger shelf, or side grille");
        }
        BlockState bankerFeet = authored.get(new BlockPos(centerX, 1, counterZ + 1));
        BlockState bankerHead = authored.get(new BlockPos(centerX, 2, counterZ + 1));
        if (!isBankWalkableCell(bankerFeet) || !isBankWalkableCell(bankerHead)) {
            throw new IllegalStateException(
                    "Village Bank teller framing obstructs the Banker's durable anchor");
        }
    }

    private static boolean isBankWalkableCell(BlockState state) {
        return state == null || state.isAir() || state.is(Blocks.CARPET.green());
    }

    /**
     * Proves that the complete Bank remains readable and hostile-spawn-safe without skylight.
     * Targets come from every authored spawn-support surface with clear headroom and authored
     * weather cover, including the portico, porch, apron, and entry landing outside the nominal
     * wall rectangle. This keeps the five-dialect gate exact without trusting daylight or terrain.
     */
    private static void validateBankInteriorLighting(
            Map<BlockPos, BlockState> authored, String snapshotId) {
        AuthoredLightFixtureSupportValidator.validate(authored, snapshotId);
        Bounds bounds = bankLightingBounds(authored);
        Set<Voxel> floors = bankCoveredSpawnableFloors(authored, bounds);
        if (floors.isEmpty()) {
            throw new IllegalStateException("Village Bank has no usable covered floor cells");
        }

        Map<Voxel, Integer> dampening = new HashMap<>();
        List<LightEmitter> emitters = new ArrayList<>();
        for (Map.Entry<BlockPos, BlockState> entry : authored.entrySet()) {
            BlockPos position = entry.getKey();
            Voxel voxel = new Voxel(position.getX(), position.getY(), position.getZ());
            if (!bounds.contains(voxel)) {
                continue;
            }
            BlockState state = entry.getValue();
            int stateDampening = state.getLightDampening();
            // Carpet is a walkable floor overlay and does not occlude block light in Minecraft.
            // Its outline shape must not be promoted to an opaque cube by this conservative
            // loader-neutral adapter.
            boolean walkableCarpet = state.is(Blocks.CARPET.green());
            if (!walkableCarpet
                    && stateDampening <= 0
                    && (state.canOcclude() || state.useShapeForLightOcclusion())) {
                stateDampening = WholeBuildingLightingValidator.MAXIMUM_BLOCK_LIGHT;
            }
            if (!walkableCarpet && stateDampening > 0) {
                dampening.put(
                        voxel,
                        Math.min(
                                WholeBuildingLightingValidator.MAXIMUM_BLOCK_LIGHT,
                                stateDampening));
            }
            int emission = state.getLightEmission();
            if (emission > 0) {
                emitters.add(new LightEmitter(voxel, emission));
            }
        }
        WholeBuildingLightingValidator.validate(new LightingSnapshot(
                        snapshotId,
                        bounds,
                        floors,
                        dampening,
                        emitters))
                .requireComfortablyLit();
    }

    private static Bounds bankLightingBounds(Map<BlockPos, BlockState> authored) {
        if (authored.isEmpty()) {
            throw new IllegalStateException("Cannot derive Village Bank lighting bounds");
        }
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (BlockPos position : authored.keySet()) {
            minX = Math.min(minX, position.getX());
            minY = Math.min(minY, position.getY());
            minZ = Math.min(minZ, position.getZ());
            maxX = Math.max(maxX, position.getX());
            maxY = Math.max(maxY, position.getY());
            maxZ = Math.max(maxZ, position.getZ());
        }
        return new Bounds(new Voxel(minX, minY, minZ), new Voxel(maxX, maxY, maxZ));
    }

    private static Set<Voxel> bankCoveredSpawnableFloors(
            Map<BlockPos, BlockState> authored, Bounds bounds) {
        Set<Voxel> floors = new HashSet<>();
        for (Map.Entry<BlockPos, BlockState> entry : authored.entrySet()) {
            BlockPos support = entry.getKey();
            BlockPos feet = support.above();
            if (!bounds.contains(new Voxel(feet.getX(), feet.getY(), feet.getZ()))
                    || !entry.getValue().isValidSpawn(
                            EmptyBlockGetter.INSTANCE, support, EntityTypes.ZOMBIE)
                    || !isBankWalkableCell(authored.get(feet))
                    || !isBankWalkableCell(authored.get(feet.above()))
                    || !bankWeatherCovered(authored, feet, bounds.maximum().y())) {
                continue;
            }
            floors.add(new Voxel(feet.getX(), feet.getY(), feet.getZ()));
        }
        return Set.copyOf(floors);
    }

    private static boolean bankWeatherCovered(
            Map<BlockPos, BlockState> authored, BlockPos feet, int maximumY) {
        for (int y = feet.getY() + 2; y <= maximumY; y++) {
            BlockState cover = authored.get(new BlockPos(feet.getX(), y, feet.getZ()));
            if (cover != null && !cover.isAir()) {
                return true;
            }
        }
        return false;
    }

    private static void rollbackBank(ServerLevel level, List<BankMutation> placements) {
        for (int index = placements.size() - 1; index >= 0; index--) {
            BankMutation mutation = placements.get(index);
            BankPlacement placement = mutation.placement();
            if (isOwnedBankPlacement(
                    level.getBlockState(placement.position()), placement.state())) {
                level.setBlock(placement.position(), mutation.original(), 3);
            }
        }
    }

    static boolean isOwnedBankPlacement(BlockState current, BlockState planned) {
        return current != null && planned != null && current.is(planned.getBlock());
    }

    private static BankPalette paletteFor(ServerLevel level, BlockPos origin) {
        var biome = level.getBiome(origin);
        if (biome.is(BiomeTags.HAS_VILLAGE_DESERT)) {
            return paletteFor(VillageArchitecture.BiomeDialect.DESERT);
        }
        if (biome.is(BiomeTags.HAS_VILLAGE_SAVANNA)) {
            return paletteFor(VillageArchitecture.BiomeDialect.SAVANNA);
        }
        if (biome.is(BiomeTags.HAS_VILLAGE_SNOWY)) {
            return paletteFor(VillageArchitecture.BiomeDialect.SNOWY);
        }
        if (biome.is(BiomeTags.HAS_VILLAGE_TAIGA)) {
            return paletteFor(VillageArchitecture.BiomeDialect.TAIGA);
        }
        return paletteFor(VillageArchitecture.BiomeDialect.PLAINS);
    }

    private static BankPalette paletteFor(VillageArchitecture.BiomeDialect dialect) {
        return switch (dialect) {
            case DESERT -> new BankPalette(
                    Blocks.SMOOTH_SANDSTONE,
                    Blocks.CUT_SANDSTONE,
                    Blocks.CHISELED_SANDSTONE,
                    Blocks.SANDSTONE,
                    Blocks.CHISELED_SANDSTONE,
                    Blocks.SANDSTONE_SLAB,
                    Blocks.SANDSTONE,
                    Blocks.SANDSTONE_STAIRS,
                    Blocks.ACACIA_FENCE,
                    Blocks.CUT_SANDSTONE,
                    Blocks.CUT_RED_SANDSTONE,
                    Blocks.ACACIA_DOOR,
                    Blocks.SANDSTONE_STAIRS,
                    Blocks.CUT_RED_SANDSTONE,
                    Blocks.CHISELED_RED_SANDSTONE,
                    Blocks.SMOOTH_RED_SANDSTONE,
                    Blocks.CUT_RED_SANDSTONE,
                    Blocks.CUT_SANDSTONE,
                    Blocks.CHISELED_SANDSTONE,
                    Blocks.SMOOTH_SANDSTONE,
                    Blocks.RED_SANDSTONE_SLAB,
                    Blocks.CHISELED_RED_SANDSTONE);
            case SAVANNA -> new BankPalette(
                    Blocks.STONE_BRICKS,
                    Blocks.ACACIA_PLANKS,
                    Blocks.POLISHED_ANDESITE,
                    Blocks.ACACIA_PLANKS,
                    Blocks.STRIPPED_ACACIA_LOG,
                    Blocks.ACACIA_SLAB,
                    Blocks.ACACIA_PLANKS,
                    Blocks.DARK_OAK_STAIRS,
                    Blocks.ACACIA_FENCE,
                    Blocks.SMOOTH_STONE,
                    Blocks.MUD_BRICKS,
                    Blocks.ACACIA_DOOR,
                    Blocks.STONE_BRICK_STAIRS,
                    Blocks.BRICKS,
                    Blocks.STRIPPED_ACACIA_LOG,
                    Blocks.STONE_BRICKS,
                    Blocks.POLISHED_ANDESITE,
                    Blocks.MUD_BRICKS,
                    Blocks.BRICKS,
                    Blocks.SMOOTH_STONE,
                    Blocks.DARK_OAK_SLAB,
                    Blocks.POLISHED_ANDESITE);
            case SNOWY -> new BankPalette(
                    Blocks.STONE_BRICKS,
                    Blocks.SPRUCE_PLANKS,
                    Blocks.POLISHED_DIORITE,
                    Blocks.SPRUCE_PLANKS,
                    Blocks.STRIPPED_SPRUCE_LOG,
                    Blocks.SPRUCE_SLAB,
                    Blocks.SPRUCE_PLANKS,
                    Blocks.DARK_OAK_STAIRS,
                    Blocks.SPRUCE_FENCE,
                    Blocks.CHISELED_STONE_BRICKS,
                    Blocks.POLISHED_DIORITE,
                    Blocks.SPRUCE_DOOR,
                    Blocks.STONE_BRICK_STAIRS,
                    Blocks.STONE_BRICKS,
                    Blocks.STRIPPED_DARK_OAK_LOG,
                    Blocks.STONE_BRICKS,
                    Blocks.CALCITE,
                    Blocks.POLISHED_DIORITE,
                    Blocks.CHISELED_QUARTZ_BLOCK,
                    Blocks.POLISHED_DIORITE,
                    Blocks.SNOW_BLOCK,
                    Blocks.DARK_OAK_PLANKS);
            case TAIGA -> new BankPalette(
                    Blocks.STONE_BRICKS,
                    Blocks.SPRUCE_PLANKS,
                    Blocks.POLISHED_ANDESITE,
                    Blocks.SPRUCE_PLANKS,
                    Blocks.STRIPPED_SPRUCE_LOG,
                    Blocks.SPRUCE_SLAB,
                    Blocks.SPRUCE_PLANKS,
                    Blocks.SPRUCE_STAIRS,
                    Blocks.SPRUCE_FENCE,
                    Blocks.CHISELED_STONE_BRICKS,
                    Blocks.MOSSY_COBBLESTONE,
                    Blocks.SPRUCE_DOOR,
                    Blocks.COBBLESTONE_STAIRS,
                    Blocks.BRICKS,
                    Blocks.STRIPPED_SPRUCE_LOG,
                    Blocks.MOSSY_COBBLESTONE,
                    Blocks.POLISHED_ANDESITE,
                    Blocks.MOSSY_STONE_BRICKS,
                    Blocks.CHISELED_STONE_BRICKS,
                    Blocks.STONE_BRICKS,
                    Blocks.COBBLED_DEEPSLATE_SLAB,
                    Blocks.MOSSY_STONE_BRICKS);
            case PLAINS -> new BankPalette(
                    Blocks.STONE_BRICKS,
                    Blocks.OAK_PLANKS,
                    Blocks.POLISHED_ANDESITE,
                    Blocks.OAK_PLANKS,
                    Blocks.STRIPPED_OAK_LOG,
                    Blocks.DARK_OAK_SLAB,
                    Blocks.DARK_OAK_PLANKS,
                    Blocks.DARK_OAK_STAIRS,
                    Blocks.OAK_FENCE,
                    Blocks.CHISELED_STONE_BRICKS,
                    Blocks.BRICKS,
                    Blocks.OAK_DOOR,
                    Blocks.STONE_BRICK_STAIRS,
                    Blocks.BRICKS,
                    Blocks.STRIPPED_OAK_LOG,
                    Blocks.POLISHED_ANDESITE,
                    Blocks.BRICKS,
                    Blocks.STONE_BRICKS,
                    Blocks.CHISELED_STONE_BRICKS,
                    Blocks.SMOOTH_STONE,
                    Blocks.DARK_OAK_SLAB,
                    Blocks.BRICKS);
        };
    }

    private record BankPalette(
            Block foundation,
            Block floor,
            Block floorAccent,
            Block wall,
            Block corner,
            Block roof,
            Block roofDeck,
            Block roofStairs,
            Block fence,
            Block accent,
            Block secondaryTrim,
            Block door,
            Block stairs,
            Block chimney,
            Block facadePier,
            Block civicPlinth,
            Block civicCornice,
            Block civicWall,
            Block ledgerAccent,
            Block forecourt,
            Block roofCap,
            Block cupolaBase) {
    }

    private static boolean spawnBanker(
            ServerLevel level,
            BlockPos position,
            boolean generatedStructure,
            long regionKey,
            BlockPos exchangeDeskPosition,
            EconomyService economy) {
        Villager banker = EntityTypes.VILLAGER.create(
                level,
                generatedStructure
                        ? EntitySpawnReason.STRUCTURE
                        : EntitySpawnReason.NATURAL);
        if (banker == null) {
            return false;
        }
        banker.teleportTo(position.getX() + 0.5, position.getY(), position.getZ() + 0.5);
        if (!BankerAccess.markBanker(banker, regionKey, exchangeDeskPosition)) {
            banker.discard();
            return false;
        }
        banker.setHomeTo(position, EmeraldConfig.current().bankerRestrictionRadius());
        banker.setCustomName(Component.translatable("entity.the_emerald_standard.banker"));
        if (!level.addFreshEntity(banker)) {
            return false;
        }
        if (!flushBankEntitiesAndChunks(level)) {
            banker.discard();
            flushBankEntitiesAndChunks(level);
            return false;
        }
        // Tagging/entity insertion happens first. If the save fails, the next scan rediscovers
        // this same scoped entity and retries the UUID write instead of spawning another.
        economy.rememberGeneratedBanker(regionKey, banker.getUUID());
        return true;
    }

    private static long regionKey(BlockPos position, int regionSize) {
        int regionX = Math.floorDiv(position.getX(), regionSize);
        int regionZ = Math.floorDiv(position.getZ(), regionSize);
        return ((long) regionX << 32) ^ (regionZ & 0xFFFFFFFFL);
    }

    private static long mix64(long value) {
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    private record BankPlacement(BlockPos position, BlockState state) {
    }

    private record BankMutation(BankPlacement placement, BlockState original) {
    }

    private record BankPlotSearch(List<BlockPos> candidates, boolean complete) {
    }

    private record BankPlotCandidate(
            BlockPos origin, VillageBankPlacementPolicy.CandidatePriority priority) {
    }

    private record BankBuildAttempt(
            BlockPos origin,
            BankBuildResult build,
            boolean searchComplete,
            boolean hadCandidates) {
    }

    private record BankIntegrity(
            boolean complete, VillageMaterializationPolicy.IntegrityDecision decision, String problem) {
    }

    /** Result consumed by both loaders so recognized but unavailable counters are not silent. */
    public record BankDeskAccess(
            BlockPos accessPoint, BankWorkstationAccessPolicy.Decision decision, Long bankRegionKey) {
    }

    private record BankBuildResult(boolean built, List<BankMutation> placements) {
        private static BankBuildResult failed() {
            return new BankBuildResult(false, List.of());
        }
    }

    private record BankerDeathSaveBarrier(
            long regionKey,
            UUID canonicalRootId,
            UUID observedEntityId,
            ServerLevel level,
            Entity entity) {
    }
}
