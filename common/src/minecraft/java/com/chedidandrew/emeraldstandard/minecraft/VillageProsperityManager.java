package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.EconomyService;
import com.chedidandrew.emeraldstandard.core.EconomyState;
import com.chedidandrew.emeraldstandard.core.VillageArchitecture;
import com.chedidandrew.emeraldstandard.core.VillageProsperityEngine;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Connects the loader-neutral Village Prosperity System to loaded Minecraft villages.
 *
 * <p>Offline progression is data-only. This manager never force-loads chunks, never lets villagers
 * mine arbitrary terrain, and materializes only a small bounded number of blocks while players are
 * nearby.</p>
 */
public final class VillageProsperityManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(
            "the_emerald_standard_village_development");
    private static final String VILLAGE_TAG_PREFIX = "the_emerald_standard_village_";
    private static final int PROJECT_SITE_CANDIDATES_PER_PULSE = 1;
    private static final int PROJECT_TRAIL_INSPECTIONS_PER_PULSE = 16;
    private static final int PROJECT_ENTRANCE_INSPECTIONS_PER_PULSE = 32;
    private static final long PROJECT_TRAIL_PULSE_CADENCE = 2L;
    private static final Map<UUID, Long> LAST_SETTLER_TICK = new HashMap<>();
    private static final Map<UUID, Long> LAST_WORKER_VISUAL_TICK = new HashMap<>();
    private static final Set<BlueprintMismatchKey> REPORTED_BLUEPRINT_MISMATCHES =
            new HashSet<>();

    private VillageProsperityManager() {
    }

    /** Clears world-session-only presentation and pacing state between server instances. */
    public static void resetRuntimeState() {
        LAST_SETTLER_TICK.clear();
        LAST_WORKER_VISUAL_TICK.clear();
        REPORTED_BLUEPRINT_MISMATCHES.clear();
    }

    public static void tick(MinecraftServer server, EconomyService economy) {
        EmeraldConfig config = EmeraldConfig.current();
        economy.configureVillageProsperity(
                config.villageProsperitySimulationEnabled(),
                config.villageVisualProgressionEnabled(),
                config.villageMarketIntegrationEnabled(),
                config.villageAutomaticRecoveryEnabled());
        if (!config.villageProsperitySimulationEnabled()
                && !config.villageVisualProgressionEnabled()) {
            return;
        }

        long gameTime = server.overworld().getGameTime();
        List<ServerLevel> levels = new ArrayList<>();
        server.getAllLevels().forEach(levels::add);
        if (gameTime % config.villageProsperityScanIntervalTicks() == 0L) {
            for (ServerLevel level : levels) {
                scanLoadedVillages(level, economy, config);
            }
        }
        if (config.villageVisualProgressionEnabled()
                && gameTime % config.villageConstructionIntervalTicks() == 0L
                && !levels.isEmpty()) {
            MaterializationBudget budget = new MaterializationBudget(
                    config.villageConstructionBlocksPerTick(),
                    VillageMaterializationPolicy.MAX_NEARBY_VILLAGES_PER_PASS);
            int firstLevel = Math.floorMod(
                    gameTime / config.villageConstructionIntervalTicks(), levels.size());
            for (int step = 0; step < levels.size(); step++) {
                ServerLevel level = levels.get((firstLevel + step) % levels.size());
                try {
                    budget = materializeDevelopment(level, economy, config, gameTime, budget);
                } catch (BlueprintPlanMismatchException mismatch) {
                    if (mismatch.remainingBudget != null) {
                        budget = mismatch.remainingBudget;
                    }
                    economy.deferVillageProjectMaterialization(
                            mismatch.villageId, mismatch.projectId, gameTime, true);
                    BlueprintMismatchKey key = new BlueprintMismatchKey(
                            mismatch.villageId, mismatch.projectId);
                    if (REPORTED_BLUEPRINT_MISMATCHES.add(key)) {
                        LOGGER.error(
                                "Paused incompatible Blueprint V2 project {} for village {}; no blocks from that plan were written",
                                mismatch.projectId,
                                mismatch.villageId,
                                mismatch);
                    }
                    continue;
                }
                if (budget.remainingVillages <= 0) {
                    break;
                }
            }
        }
    }

    public static void forgetPlayer(UUID playerId) {
        // Player-specific state is intentionally not stored here. Kept as an integration seam.
    }

    /** Records only an actual death event. Entity absence or chunk unload never counts as death. */
    public static void onVillagerDeath(
            Villager villager, DamageSource source, EconomyService economy) {
        recordResidentDeath(
                villager,
                professionId(villager.getVillagerData().profession()),
                source,
                economy);
    }

    /** An infected tracked resident remains a real casualty when its zombie form is killed. */
    public static void onZombieVillagerDeath(
            ZombieVillager zombie, DamageSource source, EconomyService economy) {
        VillageBankManager.onZombieBankerDeath(zombie, economy);
        recordResidentDeath(
                zombie,
                professionId(zombie.getVillagerData().profession()),
                source,
                economy);
    }

    private static void recordResidentDeath(
            LivingEntity resident,
            String profession,
            DamageSource source,
            EconomyService economy) {
        UUID villageId = villageId(resident);
        if (villageId == null) {
            if (!(resident.level() instanceof ServerLevel serverLevel)
                    || !serverLevel.isVillage(resident.blockPosition())) {
                return;
            }
            String dimensionKey = dimensionKey(serverLevel);
            EconomyService.VillageSnapshot nearest = economy.nearestVillageSnapshot(
                    dimensionKey, resident.blockPosition().asLong(), 64.0);
            if (nearest == null) {
                return;
            }
            if (resident instanceof ZombieVillager) {
                EconomyState.ResidentRecord infected =
                        nearest.village().residents.get(resident.getUUID());
                if (infected == null
                        || infected.status != VillageProsperityEngine.ResidentStatus.INFECTED) {
                    return;
                }
            }
            villageId = nearest.village().villageId;
        }
        if (villageId == null) {
            return;
        }

        Entity killer = responsibleEntity(source);
        ServerPlayer responsible = responsiblePlayer(resident, source);
        UUID responsiblePlayer = responsible == null ? null : responsible.getUUID();
        VillageProsperityEngine.IncidentCause cause = responsible == null
                ? classifyCause(killer)
                : VillageProsperityEngine.IncidentCause.PLAYER;
        boolean recorded = economy.recordVillagerDeath(
                villageId,
                resident.getUUID(),
                profession,
                resident.blockPosition().asLong(),
                cause,
                responsiblePlayer);
        if (recorded && resident.level() instanceof ServerLevel level) {
            DebugFlightRecorder.recordVillageIncident(
                    level,
                    villageId,
                    resident.getUUID(),
                    cause,
                    responsiblePlayer,
                    resident.blockPosition());
        }
    }

    public static UUID villageId(Entity entity) {
        if (entity == null) {
            return null;
        }
        for (String tag : entity.entityTags()) {
            if (!tag.startsWith(VILLAGE_TAG_PREFIX)) {
                continue;
            }
            String compact = tag.substring(VILLAGE_TAG_PREFIX.length());
            if (compact.length() != 32) {
                continue;
            }
            try {
                return UUID.fromString(compact.substring(0, 8)
                        + "-" + compact.substring(8, 12)
                        + "-" + compact.substring(12, 16)
                        + "-" + compact.substring(16, 20)
                        + "-" + compact.substring(20));
            } catch (IllegalArgumentException ignored) {
                // Ignore malformed third-party or manually edited scoreboard tags.
            }
        }
        return null;
    }

    private static void scanLoadedVillages(
            ServerLevel level, EconomyService economy, EmeraldConfig config) {
        String dimensionKey = dimensionKey(level);
        Set<UUID> observed = new HashSet<>();
        Set<Long> sampledAreas = new HashSet<>();
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            if (player.level() != level) {
                continue;
            }
            BlockPos playerPosition = player.blockPosition();
            // Minecraft's POI village test is height-sensitive. Use a terrain-level companion
            // probe so creative flight and elytra discovery still census the village below.
            BlockPos villageProbe = surfaceVillageProbe(level, playerPosition);
            if (!level.isVillage(playerPosition) && !level.isVillage(villageProbe)) {
                continue;
            }
            int sampleX = Math.floorDiv(villageProbe.getX(), 64);
            int sampleZ = Math.floorDiv(villageProbe.getZ(), 64);
            long sampleKey = ((long) sampleX << 32) ^ (sampleZ & 0xFFFFFFFFL);
            if (!sampledAreas.add(sampleKey)) {
                continue;
            }

            AABB area = new AABB(villageProbe).inflate(48.0, 24.0, 48.0);
            List<Villager> nearbyVillagers = level.getEntitiesOfClass(
                    Villager.class, area, villager -> villager.isAlive());
            UUID preferredVillageId = preferredTaggedVillage(nearbyVillagers);
            List<Villager> villagers = preferredVillageId == null
                    ? nearbyVillagers
                    : nearbyVillagers.stream()
                            .filter(villager -> {
                                UUID tagged = villageId(villager);
                                return tagged == null || preferredVillageId.equals(tagged);
                            })
                            .toList();
            BlockPos approximateCenter = centerOf(villagers, villageProbe);
            EconomyService.VillageSnapshot taggedVillage = preferredVillageId == null
                    ? null
                    : economy.villageSnapshot(preferredVillageId);
            boolean trustedTaggedVillage =
                    isMatchingVillage(taggedVillage, dimensionKey, approximateCenter, 96.0);
            BlockPos center = trustedTaggedVillage
                    ? BlockPos.of(taggedVillage.village().centerPos)
                    : stableCenter(level, villagers, villageProbe);
            int bedCount = countBeds(level, center, 24, 6);
            List<Monster> hostiles = level.getEntitiesOfClass(
                    Monster.class,
                    new AABB(center).inflate(40.0, 16.0, 40.0),
                    LivingEntity::isAlive);
            boolean raidActive = hostiles.stream().anyMatch(VillageProsperityManager::isRaider);
            EconomyService.VillageSnapshot knownVillage = trustedTaggedVillage
                    ? taggedVillage
                    : null;
            if (!isMatchingVillage(knownVillage, dimensionKey, center, 72.0)) {
                knownVillage = economy.nearestVillageSnapshot(
                        dimensionKey, center.asLong(), 48.0);
            }
            UUID trustedVillageId = knownVillage == null
                    ? null
                    : knownVillage.village().villageId;
            long regionKey = "minecraft:overworld".equals(dimensionKey)
                    ? VillageBankManager.bankKeyForVillage(
                            economy,
                            dimensionKey,
                            center,
                            trustedVillageId,
                            config.villageRegionSize())
                    : regionKey(center, config.villageRegionSize(), dimensionKey);
            UUID mappedVillageId = economy.villageIdForBankRegion(regionKey);
            UUID knownVillageId = knownVillage == null ? null : knownVillage.village().villageId;
            Long anchor = "minecraft:overworld".equals(dimensionKey)
                            && (mappedVillageId == null || mappedVillageId.equals(knownVillageId))
                    ? economy.generatedBankAnchor(regionKey)
                    : null;

            List<EconomyService.ResidentObservation> residents = new ArrayList<>();
            for (Villager villager : villagers) {
                if (!villager.isAlive()) {
                    continue;
                }
                residents.add(new EconomyService.ResidentObservation(
                        villager.getUUID(),
                        professionId(villager.getVillagerData().profession()),
                        villager.blockPosition().asLong()));
            }
            EconomyService.VillageSnapshot snapshot = economy.observeVillage(
                    preferredVillageId,
                    new EconomyService.VillageObservation(
                            dimensionKey,
                            center.asLong(),
                            regionKey,
                            anchor == null ? 0L : anchor,
                            villagers.size(),
                            bedCount,
                            hostiles.size(),
                            raidActive,
                            residents));
            if (snapshot == null || !observed.add(snapshot.village().villageId)) {
                continue;
            }
            DebugFlightRecorder.recordVillageObservation(level.getServer(), snapshot);
            for (Villager villager : villagers) {
                assignVillage(villager, snapshot.village().villageId);
            }
            List<ZombieVillager> infectedResidents = level.getEntitiesOfClass(
                    ZombieVillager.class,
                    area,
                    zombie -> zombie.isAlive()
                            && (snapshot.village().villageId.equals(villageId(zombie))
                                    || snapshot.village().residents.containsKey(zombie.getUUID())));
            for (ZombieVillager zombie : infectedResidents) {
                economy.recordResidentStatus(
                        snapshot.village().villageId,
                        zombie.getUUID(),
                        "minecraft:none",
                        zombie.blockPosition().asLong(),
                        VillageProsperityEngine.ResidentStatus.INFECTED);
            }
            UUID currentAssociation = economy.villageIdForBankRegion(regionKey);
            if (anchor != null
                    && (currentAssociation == null
                            || currentAssociation.equals(snapshot.village().villageId))) {
                economy.associateBankRegionWithVillage(
                        regionKey, snapshot.village().villageId, anchor);
            }
        }
    }

    private static MaterializationBudget materializeDevelopment(
            ServerLevel level,
            EconomyService economy,
            EmeraldConfig config,
            long gameTime,
            MaterializationBudget budget) {
        int remainingBlockBudget = budget.remainingBlocks;
        String dimensionKey = dimensionKey(level);
        List<Long> playerPositions = level.players().stream()
                .map(player -> player.blockPosition().asLong())
                .toList();
        if (playerPositions.isEmpty()) {
            return budget;
        }
        List<EconomyService.VillageSnapshot> snapshots = economy.villageSnapshotsNear(
                dimensionKey, playerPositions, config.villageDevelopmentRadius());
        List<EconomyService.VillageProjectLot> excludedProjectLots = new ArrayList<>(
                economy.villageProjectLotExclusions(dimensionKey));
        List<Long> managedBankLots = new ArrayList<>();
        if ("minecraft:overworld".equals(dimensionKey)) {
            managedBankLots.addAll(economy.generatedBankAnchorsSnapshot().values());
            economy.retiredBankAnchorsSnapshot().values().forEach(managedBankLots::addAll);
        }
        int villagesToProcess = VillageMaterializationPolicy.villagesToProcess(
                snapshots.size(), budget.remainingVillages);
        int firstVillage = snapshots.isEmpty()
                ? 0
                : Math.floorMod(
                        gameTime / config.villageConstructionIntervalTicks()
                                + dimensionKey.hashCode(),
                        snapshots.size());
        int processedVillages = 0;
        try {
            for (int step = 0; step < villagesToProcess; step++) {
                processedVillages++;
                EconomyService.VillageSnapshot snapshot = snapshots.get(
                        VillageMaterializationPolicy.rotatingIndex(
                                firstVillage, step, snapshots.size()));
                EconomyState.VillageRecord village = snapshot.village();
            BlockPos villageCenter = BlockPos.of(village.centerPos);
            if (!positionColumnLoaded(level, villageCenter)) {
                continue;
            }
            spawnPendingSettler(level, economy, village, config, gameTime);
            reconcileOneMaterializedProject(
                    level,
                    economy,
                    village,
                    config,
                    gameTime,
                    excludedProjectLots);
            long constructionPulse = gameTime / config.villageConstructionIntervalTicks();
            long staggeredConstructionPulse = constructionPulse + village.villageId.hashCode();
            if (remainingBlockBudget > 0
                    && Math.floorMod(
                                    staggeredConstructionPulse, PROJECT_TRAIL_PULSE_CADENCE)
                            == 0L) {
                long trailSelectionOrdinal = Math.floorDiv(
                        staggeredConstructionPulse, PROJECT_TRAIL_PULSE_CADENCE);
                remainingBlockBudget -= materializeOneModularEntranceApproach(
                        level,
                        economy,
                        village,
                        trailSelectionOrdinal,
                        Math.min(1, remainingBlockBudget));
                if (remainingBlockBudget <= 0) {
                    continue;
                }
                remainingBlockBudget -= materializeOneModularTrailCenterSurfaceMigration(
                        level,
                        economy,
                        village,
                        trailSelectionOrdinal,
                        Math.min(1, remainingBlockBudget));
                if (remainingBlockBudget <= 0) {
                    continue;
                }
                remainingBlockBudget -= materializeOneModularTrail(
                        level,
                        economy,
                        village,
                        trailSelectionOrdinal,
                        Math.min(1, remainingBlockBudget));
            }
            if (remainingBlockBudget <= 0) {
                continue;
            }
            // Recovery settlers may materialize at population zero, but buildings never do. This
            // keeps the physical world aligned with the authoritative productive population.
            if (village.population <= 0
                    || village.lifecycle == VillageProsperityEngine.Lifecycle.EXTINCT
                    || village.lifecycle == VillageProsperityEngine.Lifecycle.ABANDONED) {
                continue;
            }
            Long selectedProjectId = economy.claimNextDueVillageVisualProject(
                    village.villageId, gameTime);
            if (selectedProjectId == null) {
                continue;
            }
            // The integrity pass above may have changed the authoritative project record. Refresh
            // after claiming the persisted per-village selection ordinal so this pulse never acts
            // on the stale proximity snapshot that preceded that mutation.
            EconomyService.VillageSnapshot claimedSnapshot = economy.villageSnapshot(
                    village.villageId);
            if (claimedSnapshot == null) {
                continue;
            }
            village = claimedSnapshot.village();
            EconomyState.VillageProject project = village.projects.stream()
                    .filter(candidate -> candidate.projectId == selectedProjectId)
                    .findFirst()
                    .orElse(null);
            if (project == null) {
                continue;
            }
            if (project.originPos == 0L) {
                ProjectSiteSearch siteSearch = findProjectOrigin(
                        level,
                        economy,
                        village,
                        project,
                        excludedProjectLots,
                        managedBankLots);
                if (siteSearch.availability
                        == VillageMaterializationPolicy.SiteAvailability.SEARCH_INCOMPLETE) {
                    continue;
                }
                if (siteSearch.availability
                        == VillageMaterializationPolicy.SiteAvailability.INCOMPLETE_UNLOADED) {
                    economy.deferVillageProjectMaterialization(
                            village.villageId, project.projectId, gameTime, true);
                    DebugFlightRecorder.recordConstruction(
                            level,
                            village.villageId,
                            project.projectId,
                            project.type.name(),
                            "site_waiting_for_chunks",
                            villageCenter,
                            project.materializedBlocks,
                            project.totalBlocks,
                            "Candidate frontier was not fully loaded; persisted backoff lets later projects advance without force-loading chunks");
                    continue;
                }
                if (siteSearch.availability
                        == VillageMaterializationPolicy.SiteAvailability.UNSAFE) {
                    DebugFlightRecorder.recordConstruction(
                            level,
                            village.villageId,
                            project.projectId,
                            project.type.name(),
                            "site_unavailable",
                            villageCenter,
                            project.materializedBlocks,
                            project.totalBlocks,
                            "Every fully loaded candidate project lot was unsafe or occupied");
                    economy.deferVillageProjectMaterialization(
                            village.villageId, project.projectId, gameTime);
                    continue;
                }
                BlockPos origin = siteSearch.origin;
                if (isManagedProject(project)) {
                    project.designStage = VillageStructureProgression.desiredVisualStage(
                            village.developmentTier);
                    project.trailAnchorPos = siteSearch.trailAnchor.asLong();
                    project.trailAnchorSet = true;
                    project.entranceApproachVersion =
                            EconomyState.ENTRANCE_APPROACH_VERSION;
                    project.entranceApproachStepCount = siteSearch.entranceApproachStepCount;
                    project.entranceApproachCursor = 0;
                    project.entranceApproachTotalCells =
                            siteSearch.entranceApproachTotalCells;
                    project.entranceApproachComplete =
                            siteSearch.entranceApproachTotalCells == 0;
                }
                List<Placement> template = projectTemplate(level, origin, village, project);
                String designPlanHash = isBlueprint(project)
                        ? blueprintPlanHash(
                                village,
                                project,
                                blueprintPlacementPlan(level, origin, village, project))
                        : "";
                List<Placement> modularTrail = isManagedProject(project)
                        ? managedProjectTrail(origin, village, project)
                        : List.of();
                ProjectBounds bounds = bounds(origin, template);
                boolean reserved = isManagedProject(project)
                        ? economy.reserveVillageProjectSite(
                                village.villageId,
                                project.projectId,
                                origin.asLong(),
                                bounds.minimum.asLong(),
                                bounds.maximum.asLong(),
                                template.size(),
                                village.architectureDialect,
                                project.designRotation,
                                project.designStage,
                                project.trailAnchorPos,
                                modularTrail.size(),
                                project.entranceApproachStepCount,
                                project.entranceApproachTotalCells,
                                designPlanHash)
                        : economy.reserveVillageProjectSite(
                                village.villageId,
                                project.projectId,
                                origin.asLong(),
                                bounds.minimum.asLong(),
                                bounds.maximum.asLong(),
                                template.size());
                if (!reserved) {
                    continue;
                }
                project.originPos = origin.asLong();
                project.boundsMinPos = bounds.minimum.asLong();
                project.boundsMaxPos = bounds.maximum.asLong();
                project.totalBlocks = template.size();
                if (isBlueprint(project)) {
                    project.designPlanHash = designPlanHash;
                }
                // The service snapshot predates this multi-village construction pulse. Publish
                // the new reservation into the local exclusion set immediately so a later village
                // in the same pulse cannot reserve an overlapping sparse or unfinished footprint.
                excludedProjectLots.add(new EconomyService.VillageProjectLot(
                        project.boundsMinPos, project.boundsMaxPos));
                if (isManagedProject(project)) {
                    project.trailMaterializedBlocks = 0;
                    project.trailTotalBlocks = modularTrail.size();
                    project.trailMaterializedComplete = false;
                }
                DebugFlightRecorder.recordConstruction(
                        level,
                        village.villageId,
                        project.projectId,
                        project.type.name(),
                        "site_reserved",
                        origin,
                        0,
                        project.totalBlocks,
                        "");
            }

            BlockPos origin = BlockPos.of(project.originPos);
            if (!positionColumnLoaded(level, origin)) {
                continue;
            }
            List<Placement> placements = projectTemplate(level, origin, village, project);
            int index = Math.min(project.materializedBlocks, placements.size());
            int constructionTarget = VillageMaterializationPolicy.constructionTargetBlocks(
                    project.economicProgress,
                    project.economicComplete,
                    placements.size(),
                    groundbreakingPrefix(placements));
            int placedThisTick = 0;
            boolean blocked = false;
            boolean unloaded = false;
            while (index < constructionTarget && remainingBlockBudget > 0) {
                Placement placement = placements.get(index);
                if (placement.isCosmetic() && project.manualRepairRequired) {
                    // A structural integrity rewind must never turn destroyed yard props into a
                    // renewable repair source. Cosmetics get their one attempt only during fresh
                    // construction (or when first appended by a genuine template upgrade).
                    index++;
                    continue;
                }
                if (!placementColumnLoaded(level, origin, placement)) {
                    if (placement.isCosmetic()) {
                        // Exterior dressing is a one-shot best effort. An unloaded side/rear
                        // yard must never hold the economically authoritative building hostage
                        // or cause the generator to force-load terrain just for decoration.
                        index++;
                        continue;
                    }
                    // Persist the same bounded backoff used for unsafe placements instead of
                    // probing an unloaded boundary on every village pulse.
                    blocked = true;
                    unloaded = true;
                    break;
                }
                BlockPos target = placementTarget(level, origin, placement);
                BlockState current = level.getBlockState(target);
                if (placementSatisfied(level, origin, target, current, placement)) {
                    if (placement.isStructuralAuthority()
                            && !VillageDevelopmentProtection.mayPlace(
                                    level,
                                    village.villageId,
                                    project.projectId,
                                    target,
                                    current,
                                    placement.state)) {
                        blocked = true;
                        break;
                    }
                    index++;
                    continue;
                }
                if (placement.isAccessClearance()) {
                    // Semantic air is an integrity assertion, never a request to remove a block.
                    // Preserve the obstruction and suspend this project until the route is clear.
                    blocked = true;
                    break;
                }
                if (!VillageDevelopmentProtection.mayPlace(
                        level,
                        village.villageId,
                        project.projectId,
                        target,
                        current,
                        placement.state)) {
                    if (placement.isTrail() || placement.isCosmetic()) {
                        // A protected cell may leave a small retrofit gap, but it must not block
                        // the building's safe foundation/detail suffix or overwrite player work.
                        index++;
                        continue;
                    }
                    blocked = true;
                    break;
                }
                if (placement.isTrail() && !trailHasClearance(level, target)) {
                    index++;
                    continue;
                }
                boolean safe = level.getBlockEntity(target) == null
                        && level.getFluidState(target).isEmpty()
                        && mayApplyPlacement(current, placement)
                        && (!placement.isCosmetic()
                                || cosmeticPlacementSupported(level, origin, target, placement));
                if (!safe) {
                    if (placement.isTrail() || placement.isCosmetic()) {
                        index++;
                        continue;
                    }
                    blocked = true;
                    break;
                }
                if (!level.setBlock(target, placement.state, 3)
                        || !level.getBlockState(target).is(placement.state.getBlock())) {
                    if (placement.isCosmetic()) {
                        index++;
                        continue;
                    }
                    blocked = true;
                    break;
                }
                placedThisTick++;
                remainingBlockBudget--;
                index++;
            }
            boolean complete = index >= placements.size();
            if (isManagedProject(project) && (placedThisTick > 0 || complete)) {
                normalizeAuthoredModularConnections(
                        level,
                        origin,
                        village.villageId,
                        project.projectId,
                        placements.subList(0, index));
            }
            boolean completionDamaged = false;
            if (complete) {
                // Construction is incremental, so a player can change an earlier authored block
                // after its cursor has advanced. Verify the complete template once before granting
                // authority; never repair or overwrite a mismatch discovered here.
                int verifiedPrefix = placements.size();
                for (int verifyIndex = 0; verifyIndex < placements.size(); verifyIndex++) {
                    Placement placement = placements.get(verifyIndex);
                    if (placement.isTrail() || placement.isCosmetic()) {
                        continue;
                    }
                    if (!placementColumnLoaded(level, origin, placement)) {
                        complete = false;
                        blocked = true;
                        unloaded = true;
                        break;
                    }
                    BlockPos target = placementTarget(level, origin, placement);
                    if (!placementSatisfied(
                            level,
                            origin,
                            target,
                            level.getBlockState(target),
                            placement)) {
                        verifiedPrefix = verifyIndex;
                        complete = false;
                        completionDamaged = true;
                        break;
                    }
                }
                if (completionDamaged) {
                    ProjectBounds currentBounds = bounds(origin, placements);
                    economy.requireManualVillageProjectRepair(
                            village.villageId,
                            project.projectId,
                            verifiedPrefix,
                            placements.size(),
                            currentBounds.minimum.asLong(),
                            currentBounds.maximum.asLong());
                    DebugFlightRecorder.recordConstruction(
                            level,
                            village.villageId,
                            project.projectId,
                            project.type.name(),
                            "completion_modified",
                            origin,
                            verifiedPrefix,
                            placements.size(),
                            "Player changes were preserved; the authored building remains unsafe");
                }
            }
            if (!completionDamaged && (index > project.materializedBlocks || complete)) {
                economy.updateVillageProjectMaterialization(
                        village.villageId,
                        project.projectId,
                        index,
                        placements.size(),
                        complete,
                        false);
                DebugFlightRecorder.recordConstruction(
                        level,
                        village.villageId,
                        project.projectId,
                        project.type.name(),
                        complete ? "completed" : "progress",
                        origin,
                        index,
                        placements.size(),
                        "");
            }
            if (blocked) {
                DebugFlightRecorder.recordConstruction(
                        level,
                        village.villageId,
                        project.projectId,
                        project.type.name(),
                        "blocked",
                        origin,
                        index,
                        placements.size(),
                        unloaded ? "Required chunk was unloaded" : "Placement was occupied, protected, or rejected");
                // The site was fully validated before reservation. Keep it even at prefix zero:
                // it may be a completed structure undergoing integrity repair, and relocating it
                // could leave an orphaned duplicate. Retry gates prevent a blocked-site hot loop.
                economy.deferVillageProjectMaterialization(
                        village.villageId, project.projectId, gameTime, true);
            }
            if (placedThisTick > 0) {
                showWorkerActivity(level, village, project, origin, gameTime);
                double x = origin.getX() + 0.5;
                double y = origin.getY() + 1.5;
                double z = origin.getZ() + 0.5;
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, x, y, z, 2, 1.2, 0.7, 1.2, 0.02);
                if (gameTime % 20L == 0L) {
                    level.playSound(
                            null,
                            origin,
                            SoundEvents.WOOD_PLACE,
                            SoundSource.BLOCKS,
                            0.35F,
                            1.0F);
                }
            }
            }
        } catch (BlueprintPlanMismatchException mismatch) {
            throw mismatch.withRemainingBudget(new MaterializationBudget(
                    remainingBlockBudget,
                    Math.max(0, budget.remainingVillages - processedVillages)));
        }
        return new MaterializationBudget(
                remainingBlockBudget, budget.remainingVillages - processedVillages);
    }

    /** Terrain supports stay bottom-up, followed by enough authored blocks to show the worksite. */
    private static int groundbreakingPrefix(List<Placement> placements) {
        int prefix = 0;
        while (prefix < placements.size()
                && placements.get(prefix).role == PlacementRole.TERRAIN_SUPPORT) {
            prefix++;
        }
        return Math.min(
                placements.size(),
                prefix + VillageMaterializationPolicy.GROUNDBREAKING_VISIBLE_BLOCKS);
    }

    /**
     * Scans the historical coarse-dirt center cells once on a cursor that is deliberately
     * independent from the append-only road cursor. A protected, obstructed, or otherwise unsafe
     * coordinate is adopted as a permanent gap; an unloaded coordinate waits for a later pulse.
     */
    private static int materializeOneModularTrailCenterSurfaceMigration(
            ServerLevel level,
            EconomyService economy,
            EconomyState.VillageRecord village,
            long selectionOrdinal,
            int writeBudget) {
        if (writeBudget <= 0) {
            return 0;
        }
        TrailCenterSurfaceWork work = nextModularTrailCenterSurfaceWork(
                village, selectionOrdinal);
        if (work == null) {
            return 0;
        }
        EconomyState.VillageProject project = work.project;
        List<Placement> migration = work.trail;
        int expectedVersion = project.trailCenterSurfaceVersion;
        if (project.trailCenterSurfaceMigrationTotalCells == 0) {
            if (economy.initializeVillageProjectTrailCenterSurfaceMigration(
                    village.villageId,
                    project.projectId,
                    expectedVersion,
                    migration.size())) {
                project.trailCenterSurfaceMigrationTotalCells = migration.size();
                if (migration.isEmpty()) {
                    project.trailCenterSurfaceVersion =
                            EconomyState.TRAIL_CENTER_SURFACE_VERSION;
                }
            }
            // Freeze the exact candidate count durably before the first world write.
            return 0;
        }
        if (project.trailCenterSurfaceMigrationTotalCells != migration.size()
                || project.trailCenterSurfaceMigrationCursor > migration.size()) {
            return 0;
        }

        BlockPos origin = BlockPos.of(project.originPos);
        int previousIndex = project.trailCenterSurfaceMigrationCursor;
        int index = previousIndex;
        int inspected = 0;
        int placed = 0;
        while (index < migration.size()
                && inspected < PROJECT_TRAIL_INSPECTIONS_PER_PULSE
                && placed < writeBudget) {
            Placement placement = migration.get(index);
            inspected++;
            if (!placementColumnLoaded(level, origin, placement)) {
                break;
            }
            BlockPos target = placementTarget(level, origin, placement);
            BlockState current = level.getBlockState(target);
            if (placementSatisfied(level, origin, target, current, placement)) {
                index++;
                continue;
            }
            boolean protectionAllowed = VillageDevelopmentProtection.mayPlace(
                    level,
                    village.villageId,
                    project.projectId,
                    target,
                    current,
                    placement.state);
            boolean safe = protectionAllowed
                    && trailHasClearance(level, target)
                    && level.getBlockEntity(target) == null
                    && level.getFluidState(target).isEmpty()
                    && mayApplyPlacement(current, placement);
            if (safe
                    && level.setBlock(target, placement.state, 3)
                    && level.getBlockState(target).is(placement.state.getBlock())) {
                placed++;
            }
            // The migration is deliberately one-shot: claimed cells and failed optional writes
            // are not repaired again after this coordinate has been inspected.
            index++;
        }
        boolean complete = index >= migration.size();
        if (index > previousIndex || complete) {
            if (economy.updateVillageProjectTrailCenterSurfaceMigration(
                    village.villageId,
                    project.projectId,
                    expectedVersion,
                    previousIndex,
                    index,
                    migration.size(),
                    complete)) {
                project.trailCenterSurfaceVersion = complete
                        ? EconomyState.TRAIL_CENTER_SURFACE_VERSION
                        : expectedVersion;
                project.trailCenterSurfaceMigrationCursor = complete ? 0 : index;
                project.trailCenterSurfaceMigrationTotalCells = complete
                        ? 0
                        : migration.size();
            }
        }
        return placed;
    }

    private static TrailCenterSurfaceWork nextModularTrailCenterSurfaceWork(
            EconomyState.VillageRecord village, long selectionOrdinal) {
        List<TrailCenterSurfaceWork> candidates = new ArrayList<>();
        for (EconomyState.VillageProject project : village.projects) {
            if (!VillageArchitecture.MODULAR_SCHEMA.equals(project.designSchema)
                    || !project.economicComplete
                    || !project.materializedComplete
                    || project.manualRepairRequired
                    || project.abstractOnly
                    || project.originPos == 0L
                    || !project.trailAnchorSet
                    || !project.trailMaterializedComplete
                    || project.trailCenterSurfaceMigrationCursor < 0
                    || project.trailCenterSurfaceVersion
                            >= EconomyState.TRAIL_CENTER_SURFACE_VERSION) {
                continue;
            }
            BlockPos origin = BlockPos.of(project.originPos);
            candidates.add(new TrailCenterSurfaceWork(
                    project, modularProjectTrailCenterSurfaceMigration(origin, village, project)));
        }
        return candidates.isEmpty()
                ? null
                : candidates.get((int) Math.floorMod(selectionOrdinal, candidates.size()));
    }

    /**
     * Advances one persisted, non-authoritative road cursor. Unsafe or claimed cells become
     * permanent gaps; unloaded cells wait without blocking the associated building.
     */
    private static int materializeOneModularTrail(
            ServerLevel level,
            EconomyService economy,
            EconomyState.VillageRecord village,
            long selectionOrdinal,
            int writeBudget) {
        if (writeBudget <= 0) {
            return 0;
        }
        TrailWork work = nextModularTrailWork(village, selectionOrdinal);
        if (work == null) {
            return 0;
        }
        EconomyState.VillageProject project = work.project;
        BlockPos origin = BlockPos.of(project.originPos);
        List<Placement> trail = work.trail;
        if (!VillageMaterializationPolicy.compatibleTrailTarget(
                project.trailMaterializedBlocks,
                project.trailTotalBlocks,
                trail.size())) {
            return 0;
        }
        if (trail.size() > project.trailTotalBlocks) {
            int persistedTotal = project.trailTotalBlocks;
            if (!economy.extendVillageProjectTrailTarget(
                    village.villageId,
                    project.projectId,
                    persistedTotal,
                    trail.size())) {
                return 0;
            }
            project.trailTotalBlocks = trail.size();
            project.trailMaterializedComplete = false;
        }

        int index = Math.min(project.trailMaterializedBlocks, trail.size());
        int previousIndex = index;
        int inspected = 0;
        int placed = 0;
        while (index < trail.size()
                && inspected < PROJECT_TRAIL_INSPECTIONS_PER_PULSE
                && placed < writeBudget) {
            Placement placement = trail.get(index);
            inspected++;
            if (!placementColumnLoaded(level, origin, placement)) {
                break;
            }
            if (!previousPrimaryTrailColumnLoaded(level, origin, trail, index)) {
                break;
            }
            BlockPos target = placementTarget(level, origin, placement);
            BlockState current = level.getBlockState(target);
            if (placementSatisfied(level, origin, target, current, placement)) {
                index++;
                continue;
            }
            BlockState proposed = modularTrailPlacementState(placement);
            boolean protectionAllowed = VillageDevelopmentProtection.mayPlace(
                    level,
                    village.villageId,
                    project.projectId,
                    target,
                    current,
                    proposed);
            boolean safe = protectionAllowed
                    && trailHasClearance(level, target)
                    && trailGradeWalkable(level, origin, trail, index, target)
                    && level.getBlockEntity(target) == null
                    && level.getFluidState(target).isEmpty()
                    && mayApplyPlacement(current, placement);
            if (!safe) {
                index++;
                continue;
            }
            if (level.setBlock(target, proposed, 3)
                    && level.getBlockState(target).is(proposed.getBlock())) {
                placed++;
            }
            // A failed optional path write must not stall the authoritative construction queue.
            index++;
        }
        boolean complete = index >= trail.size();
        if (index > previousIndex || complete) {
            if (economy.updateVillageProjectTrailMaterialization(
                    village.villageId,
                    project.projectId,
                    index,
                    trail.size(),
                    complete)) {
                project.trailMaterializedBlocks = index;
                project.trailMaterializedComplete = complete;
            }
        }
        return placed;
    }

    /** Keeps the persisted historical plan order while ensuring no new center cell emits coarse dirt. */
    private static BlockState modularTrailPlacementState(Placement placement) {
        return placement.role == PlacementRole.TRAIL_PRIMARY
                        && placement.state.is(Blocks.COARSE_DIRT)
                ? Blocks.DIRT_PATH.defaultBlockState()
                : placement.state;
    }

    /** Includes completed narrow legacy roads when their deterministic plan has a wider suffix. */
    private static TrailWork nextModularTrailWork(
            EconomyState.VillageRecord village, long selectionOrdinal) {
        List<TrailWork> candidates = new ArrayList<>();
        for (EconomyState.VillageProject project : village.projects) {
            if (!VillageArchitecture.isManagedStructureSchema(project.designSchema)
                    || !project.economicComplete
                    || !project.materializedComplete
                    || project.manualRepairRequired
                    || project.abstractOnly
                    || project.originPos == 0L
                    || !project.trailAnchorSet) {
                continue;
            }
            BlockPos origin = BlockPos.of(project.originPos);
            List<Placement> trail = managedProjectTrail(origin, village, project);
            if (trail.isEmpty()
                    || !VillageMaterializationPolicy.compatibleTrailTarget(
                            project.trailMaterializedBlocks,
                            project.trailTotalBlocks,
                            trail.size())
                    || (project.trailMaterializedComplete
                            && trail.size() == project.trailTotalBlocks)) {
                continue;
            }
            candidates.add(new TrailWork(project, trail));
        }
        return candidates.isEmpty()
                ? null
                : candidates.get((int) Math.floorMod(selectionOrdinal, candidates.size()));
    }

    /**
     * Materializes one frozen, non-authoritative entrance approach as soon as its building is
     * complete. The whole tiny plan is preflighted before it is persisted; later player changes
     * cause the remaining work to be waived rather than overwritten or repaired.
     */
    private static int materializeOneModularEntranceApproach(
            ServerLevel level,
            EconomyService economy,
            EconomyState.VillageRecord village,
            long selectionOrdinal,
            int writeBudget) {
        if (writeBudget <= 0) {
            return 0;
        }
        EconomyState.VillageProject project = nextModularEntranceApproachProject(
                village, selectionOrdinal);
        if (project == null) {
            return 0;
        }
        BlockPos origin = BlockPos.of(project.originPos);

        if (project.entranceApproachVersion < EconomyState.ENTRANCE_APPROACH_VERSION) {
            EntranceApproachSearch search = planModularEntranceApproach(
                    level, origin, village, project);
            if (search.availability
                    == VillageMaterializationPolicy.SiteAvailability.INCOMPLETE_UNLOADED) {
                return 0;
            }
            if (search.availability
                    != VillageMaterializationPolicy.SiteAvailability.AVAILABLE) {
                if (economy.waiveVillageProjectEntranceApproach(
                        village.villageId,
                        project.projectId,
                        project.entranceApproachVersion,
                        project.entranceApproachCursor,
                        project.entranceApproachTotalCells)) {
                    project.entranceApproachVersion =
                            EconomyState.ENTRANCE_APPROACH_VERSION;
                    project.entranceApproachStepCount = 0;
                    project.entranceApproachCursor = 0;
                    project.entranceApproachTotalCells = 0;
                    project.entranceApproachComplete = true;
                    DebugFlightRecorder.recordConstruction(
                            level,
                            village.villageId,
                            project.projectId,
                            project.type.name(),
                            "entrance_retrofit_waived",
                            origin,
                            0,
                            0,
                            "The existing approach was occupied or unsafe; player blocks were preserved");
                }
                return 0;
            }
            if (!economy.initializeVillageProjectEntranceApproach(
                    village.villageId,
                    project.projectId,
                    project.entranceApproachVersion,
                    search.stepCount,
                    search.placements.size())) {
                return 0;
            }
            project.entranceApproachVersion = EconomyState.ENTRANCE_APPROACH_VERSION;
            project.entranceApproachStepCount = search.stepCount;
            project.entranceApproachCursor = 0;
            project.entranceApproachTotalCells = search.placements.size();
            project.entranceApproachComplete = search.placements.isEmpty();
            // Freeze the complete route before the first world mutation. A later pulse performs
            // the bounded writes, making a save failure unable to reroll coordinates or facings.
            return 0;
        }

        List<Placement> approach = modularEntranceApproachPlacements(
                origin, village, project, project.entranceApproachStepCount);
        if (approach.size() != project.entranceApproachTotalCells
                || project.entranceApproachCursor > approach.size()) {
            if (economy.waiveVillageProjectEntranceApproach(
                    village.villageId,
                    project.projectId,
                    project.entranceApproachVersion,
                    project.entranceApproachCursor,
                    project.entranceApproachTotalCells)) {
                project.entranceApproachCursor = project.entranceApproachTotalCells;
                project.entranceApproachComplete = true;
            }
            return 0;
        }

        int previousIndex = project.entranceApproachCursor;
        // A frozen cursor is not authority to finish around a later player edit. Re-read the
        // already-inspected prefix without mutating it; an unloaded prefix waits, while a changed
        // prefix permanently waives the unseen suffix instead of repairing the approach.
        for (int prefixIndex = 0; prefixIndex < previousIndex; prefixIndex++) {
            Placement placement = approach.get(prefixIndex);
            if (!placementColumnLoaded(level, origin, placement)) {
                return 0;
            }
            BlockPos target = placementTarget(level, origin, placement);
            if (placementSatisfied(
                    level,
                    origin,
                    target,
                    level.getBlockState(target),
                    placement)) {
                continue;
            }
            if (economy.waiveVillageProjectEntranceApproach(
                    village.villageId,
                    project.projectId,
                    project.entranceApproachVersion,
                    previousIndex,
                    project.entranceApproachTotalCells)) {
                project.entranceApproachCursor = project.entranceApproachTotalCells;
                project.entranceApproachComplete = true;
                DebugFlightRecorder.recordConstruction(
                        level,
                        village.villageId,
                        project.projectId,
                        project.type.name(),
                        "entrance_retrofit_prefix_modified",
                        target,
                        prefixIndex,
                        approach.size(),
                        "An already-inspected approach cell changed; the player edit was preserved and the remaining approach was waived");
            }
            return 0;
        }

        int index = previousIndex;
        int inspected = 0;
        int placed = 0;
        while (index < approach.size()
                && inspected < PROJECT_ENTRANCE_INSPECTIONS_PER_PULSE
                && placed < writeBudget) {
            Placement placement = approach.get(index);
            inspected++;
            if (!placementColumnLoaded(level, origin, placement)) {
                break;
            }
            BlockPos target = placementTarget(level, origin, placement);
            BlockState current = level.getBlockState(target);
            if (placementSatisfied(level, origin, target, current, placement)) {
                index++;
                continue;
            }
            boolean safe = level.getBlockEntity(target) == null
                    && level.getFluidState(target).isEmpty()
                    && mayApplyPlacement(current, placement)
                    && VillageDevelopmentProtection.mayPlace(
                            level,
                            village.villageId,
                            project.projectId,
                            target,
                            current,
                            placement.state);
            if (!safe) {
                if (economy.waiveVillageProjectEntranceApproach(
                        village.villageId,
                        project.projectId,
                        project.entranceApproachVersion,
                        previousIndex,
                        project.entranceApproachTotalCells)) {
                    project.entranceApproachCursor = project.entranceApproachTotalCells;
                    project.entranceApproachComplete = true;
                    DebugFlightRecorder.recordConstruction(
                            level,
                            village.villageId,
                            project.projectId,
                            project.type.name(),
                            "entrance_retrofit_interrupted",
                            target,
                            index,
                            approach.size(),
                            "A later obstruction was preserved; the optional approach will not repair or overwrite it");
                }
                return placed;
            }
            if (!level.setBlock(target, placement.state, 3)
                    || !placementSatisfied(
                            level,
                            origin,
                            target,
                            level.getBlockState(target),
                            placement)) {
                break;
            }
            placed++;
            index++;
        }
        boolean complete = index >= approach.size();
        if (index > previousIndex || complete) {
            if (economy.updateVillageProjectEntranceApproach(
                    village.villageId,
                    project.projectId,
                    project.entranceApproachVersion,
                    previousIndex,
                    index,
                    approach.size(),
                    complete)) {
                project.entranceApproachCursor = index;
                project.entranceApproachComplete = complete;
            }
        }
        return placed;
    }

    private static EconomyState.VillageProject nextModularEntranceApproachProject(
            EconomyState.VillageRecord village, long selectionOrdinal) {
        List<EconomyState.VillageProject> candidates = village.projects.stream()
                .filter(project -> VillageArchitecture.isManagedStructureSchema(project.designSchema)
                        && project.economicComplete
                        && project.materializedComplete
                        && !project.manualRepairRequired
                        && !project.abstractOnly
                        && project.originPos != 0L
                        && project.trailAnchorSet
                        && (project.entranceApproachVersion
                                        < EconomyState.ENTRANCE_APPROACH_VERSION
                                || !project.entranceApproachComplete))
                .toList();
        return candidates.isEmpty()
                ? null
                : candidates.get((int) Math.floorMod(selectionOrdinal, candidates.size()));
    }

    /** Plans and fully preflights the descent along the frozen primary-road coordinates. */
    private static EntranceApproachSearch planModularEntranceApproach(
            ServerLevel level,
            BlockPos origin,
            EconomyState.VillageRecord village,
            EconomyState.VillageProject project) {
        List<Placement> primary = modularPrimaryTrailPrefix(
                origin,
                village,
                project,
                TerrainFoundationPlan.MAX_TERRAIN_DROP + 1);
        if (primary.isEmpty()) {
            return EntranceApproachSearch.unsafe();
        }
        List<Integer> surfaceOffsets = new ArrayList<>(primary.size());
        for (Placement routeCell : primary) {
            int worldX = origin.getX() + routeCell.dx;
            int worldZ = origin.getZ() + routeCell.dz;
            if (!level.hasChunk(Math.floorDiv(worldX, 16), Math.floorDiv(worldZ, 16))) {
                return EntranceApproachSearch.unloaded();
            }
            Integer surface = entranceWalkingSurface(level, worldX, worldZ, origin.getY());
            if (surface == null) {
                return EntranceApproachSearch.unsafe();
            }
            surfaceOffsets.add(surface - origin.getY());
        }
        var planned = VillageEntranceApproachPlan.plan(
                surfaceOffsets, TerrainFoundationPlan.MAX_TERRAIN_DROP);
        if (planned.isEmpty()) {
            return EntranceApproachSearch.unsafe();
        }
        int stepCount = planned.orElseThrow().size();
        List<Placement> placements = modularEntranceApproachPlacements(
                origin, village, project, stepCount);
        if (placements.size() > EconomyState.MAX_ENTRANCE_APPROACH_CELLS) {
            return EntranceApproachSearch.unsafe();
        }
        VillageMaterializationPolicy.SiteAvailability availability =
                mayUseEntranceApproachSite(
                        level, village.villageId, project.projectId, origin, placements);
        return new EntranceApproachSearch(placements, stepCount, availability);
    }

    /** Finds the first solid walking surface while allowing harmless plants and snow layers. */
    private static Integer entranceWalkingSurface(
            ServerLevel level, int x, int z, int originY) {
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        int bottom = originY - TerrainFoundationPlan.MAX_TERRAIN_DROP - 1;
        for (int y = top; y >= bottom; y--) {
            BlockPos position = new BlockPos(x, y, z);
            BlockState state = level.getBlockState(position);
            if (level.getBlockEntity(position) != null
                    || !level.getFluidState(position).isEmpty()) {
                return null;
            }
            if (state.isAir() || state.canBeReplaced()) {
                continue;
            }
            return isEntranceApproachGround(state) ? y + 1 : null;
        }
        return null;
    }

    private static boolean isEntranceApproachGround(BlockState state) {
        return isNaturalProjectGround(state) || isVillageTrailGround(state);
    }

    private static List<Placement> modularPrimaryTrailPrefix(
            BlockPos origin,
            EconomyState.VillageRecord village,
            EconomyState.VillageProject project,
            int limit) {
        return managedProjectTrail(origin, village, project).stream()
                .filter(placement -> placement.role == PlacementRole.TRAIL_PRIMARY)
                .limit(Math.max(0, limit))
                .toList();
    }

    /** Rebuilds a frozen plan from immutable recipe, rotation, anchor, and step-count data. */
    private static List<Placement> modularEntranceApproachPlacements(
            BlockPos origin,
            EconomyState.VillageRecord village,
            EconomyState.VillageProject project,
            int stepCount) {
        if (stepCount < 0 || stepCount > TerrainFoundationPlan.MAX_TERRAIN_DROP) {
            return List.of();
        }
        List<Placement> route = modularPrimaryTrailPrefix(
                origin, village, project, stepCount);
        if (route.size() < stepCount) {
            return List.of();
        }
        Palette palette = managedProjectPalette(village, project);
        StructureSize structure = projectSize(project);
        BlockPos previous = rotateRelative(
                structure.width / 2,
                0,
                -1,
                structure,
                project.designRotation);

        List<Placement> stairs = new ArrayList<>(stepCount);
        for (int index = 0; index < stepCount; index++) {
            Placement routeCell = route.get(index);
            Direction facing = horizontalDirection(
                    previous.getX() - routeCell.dx,
                    previous.getZ() - routeCell.dz);
            if (facing == null) {
                return List.of();
            }
            int yOffset = -index - 1;
            stairs.add(Placement.entranceStair(
                    routeCell.dx,
                    yOffset,
                    routeCell.dz,
                    palette.stairs.defaultBlockState()
                            .setValue(StairBlock.FACING, facing)));
            previous = new BlockPos(routeCell.dx, yOffset, routeCell.dz);
        }

        List<TerrainFoundationPlan.Cell> stairCells = stairs.stream()
                .map(stair -> new TerrainFoundationPlan.Cell(
                        stair.dx, stair.dy, stair.dz))
                .toList();
        List<Placement> supports = TerrainFoundationPlan.appendSupportCells(
                        stairCells, TerrainFoundationPlan.MAX_TERRAIN_DROP)
                .stream()
                .map(cell -> Placement.entranceSupport(
                        cell.x(),
                        cell.y(),
                        cell.z(),
                        palette.floor.defaultBlockState()))
                .sorted(Comparator.comparingInt(Placement::dy)
                        .thenComparingInt(Placement::dz)
                        .thenComparingInt(Placement::dx))
                .toList();
        List<Placement> result = new ArrayList<>(supports.size() + stairs.size() * 3);
        result.addAll(supports);
        Set<BlockPos> clearance = new HashSet<>();
        for (Placement stair : stairs) {
            for (int y : new int[] {stair.dy + 1, stair.dy + 2}) {
                BlockPos position = new BlockPos(stair.dx, y, stair.dz);
                if (clearance.add(position)) {
                    result.add(Placement.entranceClearance(
                            position.getX(), position.getY(), position.getZ()));
                }
            }
        }
        result.addAll(stairs);
        return List.copyOf(result);
    }

    private static Direction horizontalDirection(int dx, int dz) {
        if (dx == 1 && dz == 0) {
            return Direction.EAST;
        }
        if (dx == -1 && dz == 0) {
            return Direction.WEST;
        }
        if (dx == 0 && dz == 1) {
            return Direction.SOUTH;
        }
        if (dx == 0 && dz == -1) {
            return Direction.NORTH;
        }
        return null;
    }

    private static VillageMaterializationPolicy.SiteAvailability mayUseEntranceApproachSite(
            ServerLevel level,
            UUID villageId,
            long projectId,
            BlockPos origin,
            List<Placement> placements) {
        for (Placement placement : placements) {
            if (!placementColumnLoaded(level, origin, placement)) {
                return VillageMaterializationPolicy.SiteAvailability.INCOMPLETE_UNLOADED;
            }
        }
        for (Placement placement : placements) {
            BlockPos target = placementTarget(level, origin, placement);
            BlockState current = level.getBlockState(target);
            if (placementSatisfied(level, origin, target, current, placement)) {
                continue;
            }
            if (level.getBlockEntity(target) != null
                    || !level.getFluidState(target).isEmpty()
                    || !mayApplyPlacement(current, placement)
                    || !VillageDevelopmentProtection.mayPlace(
                            level,
                            villageId,
                            projectId,
                            target,
                            current,
                            placement.state)) {
                return VillageMaterializationPolicy.SiteAvailability.UNSAFE;
            }
        }
        return VillageMaterializationPolicy.SiteAvailability.AVAILABLE;
    }

    /** Displays bounded worker theatre without creating persistent AI or economic authority. */
    private static void showWorkerActivity(
            ServerLevel level,
            EconomyState.VillageRecord village,
            EconomyState.VillageProject project,
            BlockPos projectOrigin,
            long gameTime) {
        VillageProsperityEngine.ProjectType projectType = project.type;
        long previous = LAST_WORKER_VISUAL_TICK.getOrDefault(
                village.villageId, Long.MIN_VALUE / 2L);
        if (gameTime - previous < 80L) {
            return;
        }
        List<Villager> workers = level.getEntitiesOfClass(
                        Villager.class,
                        new AABB(projectOrigin).inflate(48.0, 12.0, 48.0),
                        villager -> villager.isAlive()
                                && !BankerAccess.isBanker(villager)
                                && !villager.isSleeping()
                                && village.villageId.equals(villageId(villager)))
                .stream()
                .sorted(Comparator
                        .comparingInt((Villager villager) -> workerPreference(villager, projectType))
                        .thenComparingDouble(villager -> villager.distanceToSqr(
                                projectOrigin.getX() + 0.5,
                                projectOrigin.getY() + 1.0,
                                projectOrigin.getZ() + 0.5)))
                .limit(2)
                .toList();
        BlockPos waypoint = workerWaypoint(level, projectOrigin, project);
        for (Villager worker : workers) {
            if (waypoint != null) {
                double distance = worker.distanceToSqr(
                        waypoint.getX() + 0.5,
                        waypoint.getY(),
                        waypoint.getZ() + 0.5);
                if (distance > 16.0 && distance <= 48.0 * 48.0) {
                    // This is a one-shot, low-speed path request. It adds theatre around active
                    // construction without installing a persistent AI goal or affecting output.
                    worker.getNavigation().moveTo(
                            waypoint.getX() + 0.5,
                            waypoint.getY(),
                            waypoint.getZ() + 0.5,
                            0.55);
                }
            }
            worker.getLookControl().setLookAt(
                    projectOrigin.getX() + 0.5,
                    projectOrigin.getY() + 1.0,
                    projectOrigin.getZ() + 0.5);
            worker.swing(InteractionHand.MAIN_HAND);
            level.sendParticles(
                    projectType == VillageProsperityEngine.ProjectType.MINE_ENTRANCE
                            ? ParticleTypes.CRIT
                            : ParticleTypes.HAPPY_VILLAGER,
                    worker.getX(), worker.getY() + 1.1, worker.getZ(),
                    1, 0.15, 0.2, 0.15, 0.0);
        }
        if (!workers.isEmpty()) {
            LAST_WORKER_VISUAL_TICK.put(village.villageId, gameTime);
        }
    }

    private static BlockPos workerWaypoint(
            ServerLevel level,
            BlockPos origin,
            EconomyState.VillageProject project) {
        StructureSize dimensions = rotatedSize(projectSize(project), project.designRotation);
        BlockPos entrance = projectEntrance(origin, project);
        int[][] offsets = {
                {entrance.getX() - origin.getX(), entrance.getZ() - origin.getZ()},
                {dimensions.width / 2, -2},
                {-2, dimensions.depth / 2},
                {dimensions.width / 2, dimensions.depth + 1},
                {dimensions.width + 1, dimensions.depth / 2},
                {-2, -2},
                {dimensions.width + 1, -2},
                {-2, dimensions.depth + 1},
                {dimensions.width + 1, dimensions.depth + 1}
        };
        for (int[] offset : offsets) {
            int x = origin.getX() + offset[0];
            int z = origin.getZ() + offset[1];
            if (!level.hasChunk(Math.floorDiv(x, 16), Math.floorDiv(z, 16))) {
                continue;
            }
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos feet = new BlockPos(x, y, z);
            BlockPos ground = feet.below();
            if (level.getFluidState(feet).isEmpty()
                    && level.getFluidState(feet.above()).isEmpty()
                    && level.getBlockState(feet).isAir()
                    && level.getBlockState(feet.above()).isAir()
                    && level.getBlockState(ground).isFaceSturdy(level, ground, Direction.UP)) {
                return feet;
            }
        }
        return null;
    }

    private static int workerPreference(
            Villager villager, VillageProsperityEngine.ProjectType projectType) {
        String profession = professionId(villager.getVillagerData().profession());
        boolean preferred = switch (projectType) {
            case MINE_ENTRANCE, SMITHY -> profession.contains("mason")
                    || profession.contains("toolsmith")
                    || profession.contains("weaponsmith")
                    || profession.contains("armorer");
            case WAREHOUSE, MARKET_SQUARE, EXCHANGE_HALL -> profession.contains("cartographer")
                    || profession.contains("librarian")
                    || profession.contains("banker")
                    || profession.contains("cleric");
            case GRANARY -> profession.contains("farmer")
                    || profession.contains("fisherman")
                    || profession.contains("butcher");
            case GUARD_POST -> profession.contains("armorer")
                    || profession.contains("weaponsmith")
                    || profession.contains("toolsmith");
            case COTTAGE, HOUSE, INN -> profession.contains("none")
                    || profession.contains("nitwit")
                    || profession.contains("farmer");
        };
        return preferred ? 0 : 1;
    }

    private static void spawnPendingSettler(
            ServerLevel level,
            EconomyService economy,
            EconomyState.VillageRecord village,
            EmeraldConfig config,
            long gameTime) {
        if (village.lifecycle == VillageProsperityEngine.Lifecycle.ABANDONED
                || village.lifecycle == VillageProsperityEngine.Lifecycle.EXTINCT
                || (village.population <= 0
                        && village.lifecycle != VillageProsperityEngine.Lifecycle.RECOVERING)) {
            return;
        }
        Long previousAttempt = LAST_SETTLER_TICK.get(village.villageId);
        if (!VillageMaterializationPolicy.settlerAttemptDue(
                gameTime, previousAttempt, config.villageSettlerSpawnIntervalTicks())) {
            return;
        }
        BlockPos center = BlockPos.of(village.centerPos);
        if (!positionColumnLoaded(level, center)) {
            return;
        }
        // Pace the attempt itself, not only successful spawns. Otherwise an ineligible village
        // repeats the entity, bed, food, and threat scans on every construction pulse.
        LAST_SETTLER_TICK.put(village.villageId, gameTime);
        AABB area = new AABB(center).inflate(48.0, 24.0, 48.0);
        int living = level.getEntitiesOfClass(
                        Villager.class,
                        area,
                        villager -> villager.isAlive()
                                && (village.villageId.equals(villageId(villager))
                                        || village.residents.containsKey(villager.getUUID())))
                .size();
        int targetPopulation = Math.min(
                VillageProsperityEngine.MAX_ABSTRACT_POPULATION,
                Math.max(village.population, village.observedPopulation + village.pendingSettlers));
        boolean reconciliationNeeded = living < targetPopulation;
        if (village.pendingSettlers <= 0 && !reconciliationNeeded) {
            return;
        }
        int usableBeds = countBeds(level, center, 24, 6);
        if (VillageProsperityEngine.effectiveHousingCapacity(village) <= living
                || usableBeds <= living
                || village.foodSupply < Math.max(12.0, (living + 1) * 6.0)) {
            return;
        }
        List<Monster> threats = level.getEntitiesOfClass(
                Monster.class, new AABB(center).inflate(24.0, 12.0, 24.0), LivingEntity::isAlive);
        if (!threats.isEmpty()) {
            return;
        }
        BlockPos spawn = findSettlerSpawn(level, center);
        if (spawn == null) {
            return;
        }
        Villager settler = EntityTypes.VILLAGER.create(level, EntitySpawnReason.NATURAL);
        if (settler == null) {
            return;
        }
        settler.teleportTo(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5);
        settler.setPersistenceRequired();
        settler.setHomeTo(
                center,
                VillageMaterializationPolicy.settlerHomeRadius(
                        config.villageDevelopmentRadius()));
        assignVillage(settler, village.villageId);
        if (!level.noCollision(settler)) {
            settler.discard();
            return;
        }
        if (level.addFreshEntity(settler)) {
            DebugFlightRecorder.recordSettler(
                    level, village.villageId, settler.getUUID(), settler.blockPosition());
            // The next loaded-world census is the sole authority that consumes the queue. Doing
            // so here as well would count one arrival twice and could collapse a two-settler
            // recovery into one.
        } else {
            settler.discard();
        }
    }

    private static BlockPos findSettlerSpawn(ServerLevel level, BlockPos center) {
        int[][] offsets = {
                {0, 0}, {2, 0}, {-2, 0}, {0, 2}, {0, -2},
                {3, 3}, {-3, 3}, {3, -3}, {-3, -3},
                {5, 0}, {-5, 0}, {0, 5}, {0, -5}
        };
        for (int[] offset : offsets) {
            int x = center.getX() + offset[0];
            int z = center.getZ() + offset[1];
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
     * Audits one completed authored structure at a low frequency. A missing or replaced authored
     * block immediately removes that project's economic authority. Completed structures are not
     * regenerated, because doing so would turn authored furnishings into a renewable item source;
     * the audit restores authority after the authored blocks are put back in-world. Append-only
     * template upgrades still use the normal guarded construction queue. Solid player blocks,
     * block entities, and unloaded chunks are never overwritten or force-loaded.
     */
    private static void reconcileOneMaterializedProject(
            ServerLevel level,
            EconomyService economy,
            EconomyState.VillageRecord village,
            EmeraldConfig config,
            long gameTime,
            List<EconomyService.VillageProjectLot> excludedProjectLots) {
        if (village == null || village.projects.isEmpty()) {
            return;
        }
        if (!positionColumnLoaded(level, BlockPos.of(village.centerPos))) {
            return;
        }
        long interval = Math.max(1L, config.villageConstructionIntervalTicks());
        long auditPulses = Math.max(1L, 2_400L / interval);
        long pulse = gameTime / interval;
        long staggeredPulse = pulse + village.villageId.hashCode();
        if (Math.floorMod(staggeredPulse, auditPulses) != 0L) {
            return;
        }

        List<EconomyState.VillageProject> candidates = village.projects.stream()
                .filter(project -> project.economicComplete
                        && (project.materializedComplete || project.manualRepairRequired)
                        && !project.abstractOnly
                        && project.originPos != 0L)
                .toList();
        if (candidates.isEmpty()) {
            return;
        }
        EconomyState.VillageProject project = candidates.get(
                VillageMaterializationPolicy.rotatingAuditIndex(
                        staggeredPulse, auditPulses, candidates.size()));
        BlockPos origin = BlockPos.of(project.originPos);
        if (!positionColumnLoaded(level, origin)) {
            return;
        }
        int desiredModularStage = isManagedProject(project)
                ? Math.max(
                        project.designStage,
                        VillageStructureProgression.desiredVisualStage(village.developmentTier))
                : 0;
        boolean pendingQualityMigration = isModular(project)
                && VillageArchitecture.hasQualityRetrofit(project.type)
                && project.designQualityStage < 0;
        List<Placement> expected = isBlueprint(project)
                ? blueprintProjectTemplate(
                        level, origin, village, project, desiredModularStage)
                : isModular(project)
                        ? modularProjectTemplate(
                                level,
                                origin,
                                village,
                                project,
                                desiredModularStage,
                                pendingQualityMigration)
                        : projectTemplate(level, origin, village, project);
        ProjectBounds expectedBounds = bounds(origin, expected);
        boolean templateExpanded = project.totalBlocks > 0
                && project.totalBlocks < expected.size();
        int priorTemplateSize = templateExpanded ? project.totalBlocks : expected.size();
        if (isManagedProject(project)) {
            // Shape properties are neighbor-derived and therefore are not template ownership.
            // Refresh only panes already inside the persisted prefix; an unbuilt suffix cannot
            // adopt or mutate a coincident player pane during preflight.
            normalizeAuthoredModularConnections(
                    level,
                    origin,
                    village.villageId,
                    project.projectId,
                    expected.subList(0, priorTemplateSize));
        }
        ProjectBounds priorBounds = templateExpanded
                ? bounds(origin, expected.subList(0, priorTemplateSize))
                : expectedBounds;
        int verifiedPrefix = priorTemplateSize;
        boolean queuedTemplateUpgrade = false;
        boolean queuedQualityUpgrade = false;
        BlockPos mismatch = null;
        int expectedStructureCells = 0;
        int mismatchedStructureCells = 0;
        for (int index = 0; index < priorTemplateSize; index++) {
            Placement placement = expected.get(index);
            // Trails are shared public infrastructure once authored. Normal terrain updates or a
            // player's landscaping must not suspend the economic authority of the building they
            // originally connected.
            if (placement.isTrail() || placement.isCosmetic()) {
                continue;
            }
            if (!placementColumnLoaded(level, origin, placement)) {
                return;
            }
            BlockPos target = placementTarget(level, origin, placement);
            BlockState current = level.getBlockState(target);
            if (placement.isStructuralAuthority()) {
                expectedStructureCells++;
            }
            if (!placementSatisfied(level, origin, target, current, placement)) {
                if (mismatch == null) {
                    verifiedPrefix = index;
                    mismatch = target;
                }
                if (placement.isStructuralAuthority()) {
                    mismatchedStructureCells++;
                }
            }
        }
        if (mismatch == null && templateExpanded) {
            // A newer release may append detail to a deterministic template. Preflight every new
            // position before suspending the legacy project's benefits. If a player has built in
            // that space, retain the valid older structure instead of turning the upgrade into an
            // obstruction or overwriting their work.
            for (int index = priorTemplateSize; index < expected.size(); index++) {
                Placement placement = expected.get(index);
                if (placement.isCosmetic()) {
                    // Cosmetic suffixes are never repair authority. A fresh construction cursor
                    // gets one safe attempt; an already completed project merely adopts their
                    // expanded bounds/hash without regenerating decoration on later audits.
                    continue;
                }
                if (!placementColumnLoaded(level, origin, placement)) {
                    recordQualityUpgradeRejection(
                            level,
                            village,
                            project,
                            pendingQualityMigration,
                            origin.offset(placement.dx, placement.dy, placement.dz),
                            index - priorTemplateSize,
                            expected.size() - priorTemplateSize,
                            "preflight column is not loaded");
                    return;
                }
                BlockPos target = placementTarget(level, origin, placement);
                BlockState current = level.getBlockState(target);
                if (!placement.isStructuralAuthority()
                        && placementSatisfied(level, origin, target, current, placement)) {
                    continue;
                }
                boolean protectionAllowed = VillageDevelopmentProtection.mayPlace(
                        level,
                        village.villageId,
                        project.projectId,
                        target,
                        current,
                        placement.state);
                boolean hasBlockEntity = level.getBlockEntity(target) != null;
                boolean fluidEmpty = level.getFluidState(target).isEmpty();
                boolean placementAllowed = mayApplyPlacement(current, placement);
                if (!isSafeTemplateUpgradeTarget(
                        current,
                        hasBlockEntity,
                        protectionAllowed,
                        level,
                        target,
                        placement)) {
                    if (placement.isTrail()) {
                        // Trails were not part of legacy project authority. Retrofit every safe
                        // cell, but do not let one claimed/player-built cell veto later upgrades.
                        continue;
                    }
                    recordQualityUpgradeRejection(
                            level,
                            village,
                            project,
                            pendingQualityMigration,
                            target,
                            index - priorTemplateSize,
                            expected.size() - priorTemplateSize,
                            "unsafe suffix target: role="
                                    + placement.role
                                    + ", current="
                                    + current
                                    + ", proposed="
                                    + placement.state
                                    + ", blockEntity="
                                    + hasBlockEntity
                                    + ", protectionAllowed="
                                    + protectionAllowed
                                    + ", fluidEmpty="
                                    + fluidEmpty
                                    + ", placementAllowed="
                                    + placementAllowed);
                    return;
                }
            }
            Placement firstRequiredAddition = expected.subList(priorTemplateSize, expected.size())
                    .stream()
                    .filter(placement -> !placement.isCosmetic())
                    .findFirst()
                    .orElse(null);
            if (firstRequiredAddition != null) {
                if (!placementColumnLoaded(level, origin, firstRequiredAddition)) {
                    return;
                }
                mismatch = placementTarget(level, origin, firstRequiredAddition);
                queuedTemplateUpgrade = true;
                queuedQualityUpgrade = isModular(project)
                        && VillageArchitecture.hasQualityRetrofit(project.type)
                        && project.designQualityStage < 0;
            }
        }
        if (mismatch == null) {
            if (project.totalBlocks != expected.size()
                    || project.boundsMinPos != expectedBounds.minimum.asLong()
                    || project.boundsMaxPos != expectedBounds.maximum.asLong()
                    || project.manualRepairRequired
                    || !project.materializedComplete) {
                if (economy.reconcileVillageProjectMaterializationAndBounds(
                        village.villageId,
                        project.projectId,
                        expected.size(),
                        expected.size(),
                        true,
                        expectedBounds.minimum.asLong(),
                        expectedBounds.maximum.asLong())) {
                    // Keep the current village snapshot authoritative for overlap checks later in
                    // this construction pulse; the service mutation above is already persisted.
                    project.materializedBlocks = expected.size();
                    project.totalBlocks = expected.size();
                    project.boundsMinPos = expectedBounds.minimum.asLong();
                    project.boundsMaxPos = expectedBounds.maximum.asLong();
                    project.materializedComplete = true;
                    project.blocked = false;
                    project.manualRepairRequired = false;
                    project.retryAfterGameTick = 0L;
                    project.materializationFailures = 0;
                }
            }
            return;
        }
        VillageMaterializationPolicy.IntegrityDecision integrityDecision =
                VillageMaterializationPolicy.assessIntegrity(
                        expectedStructureCells, mismatchedStructureCells);
        if (!queuedTemplateUpgrade
                && integrityDecision == VillageMaterializationPolicy.IntegrityDecision.RELOCATE
                && economy.relocateDestroyedVillageProject(
                        village.villageId,
                        project.projectId,
                        gameTime,
                        priorBounds.minimum.asLong(),
                        priorBounds.maximum.asLong())) {
            // The compact service snapshot was taken before this multi-village pulse. Publish the
            // newly retired footprint immediately so a later project in the same pass cannot
            // reserve inside a lot that must remain permanently abandoned.
            excludedProjectLots.add(new EconomyService.VillageProjectLot(
                    priorBounds.minimum.asLong(), priorBounds.maximum.asLong()));
            DebugFlightRecorder.recordConstruction(
                    level,
                    village.villageId,
                    project.projectId,
                    project.type.name(),
                    "destroyed_site_retired",
                    origin,
                    mismatchedStructureCells,
                    expectedStructureCells,
                    "Severe demolition retired this lot; a replacement will use a different safe site without touching the old one");
            return;
        }
        if (!queuedTemplateUpgrade
                && project.manualRepairRequired
                && project.materializedBlocks == verifiedPrefix
                && project.totalBlocks == priorTemplateSize
                && project.boundsMinPos == priorBounds.minimum.asLong()
                && project.boundsMaxPos == priorBounds.maximum.asLong()) {
            return;
        }
        boolean reconciled = queuedTemplateUpgrade
                ? isManagedProject(project)
                        ? queuedQualityUpgrade
                                ? economy.commitVillageProjectQualityUpgrade(
                                        village.villageId,
                                        project.projectId,
                                        desiredModularStage,
                                        desiredModularStage,
                                        verifiedPrefix,
                                        expected.size(),
                                        expectedBounds.minimum.asLong(),
                                        expectedBounds.maximum.asLong())
                                : economy.commitVillageProjectVisualStageUpgrade(
                                        village.villageId,
                                        project.projectId,
                                        desiredModularStage,
                                        verifiedPrefix,
                                        expected.size(),
                                        expectedBounds.minimum.asLong(),
                                        expectedBounds.maximum.asLong())
                        : economy.reconcileVillageProjectMaterializationAndBounds(
                                village.villageId,
                                project.projectId,
                                verifiedPrefix,
                                expected.size(),
                                false,
                                expectedBounds.minimum.asLong(),
                                expectedBounds.maximum.asLong())
                : economy.requireManualVillageProjectRepair(
                        village.villageId,
                        project.projectId,
                        verifiedPrefix,
                        priorTemplateSize,
                        priorBounds.minimum.asLong(),
                        priorBounds.maximum.asLong());
        if (!reconciled) {
            recordQualityUpgradeRejection(
                    level,
                    village,
                    project,
                    queuedQualityUpgrade,
                    mismatch,
                    verifiedPrefix,
                    expected.size(),
                    "atomic quality-upgrade commit rejected after successful suffix preflight"
                            + "; desiredStage="
                            + desiredModularStage
                            + ", persistedStage="
                            + project.designStage
                            + ", persistedQualityStage="
                            + project.designQualityStage);
            return;
        }
        // This object is a snapshot. Mirror the persisted demotion so the repair queue can act in
        // the same pulse instead of waiting for another proximity snapshot.
        project.materializedBlocks = verifiedPrefix;
        project.totalBlocks = queuedTemplateUpgrade ? expected.size() : priorTemplateSize;
        project.boundsMinPos = (queuedTemplateUpgrade ? expectedBounds : priorBounds)
                .minimum.asLong();
        project.boundsMaxPos = (queuedTemplateUpgrade ? expectedBounds : priorBounds)
                .maximum.asLong();
        if (queuedTemplateUpgrade && isManagedProject(project)) {
            project.designStage = desiredModularStage;
            if (queuedQualityUpgrade) {
                project.designQualityStage = desiredModularStage;
            }
        }
        project.materializedComplete = false;
        project.blocked = !queuedTemplateUpgrade;
        project.manualRepairRequired = !queuedTemplateUpgrade;
        project.retryAfterGameTick = 0L;
        project.materializationFailures = 0;
        DebugFlightRecorder.recordConstruction(
                level,
                village.villageId,
                project.projectId,
                project.type.name(),
                "integrity_demoted",
                mismatch,
                verifiedPrefix,
                queuedTemplateUpgrade ? expected.size() : priorTemplateSize,
                queuedTemplateUpgrade
                        ? "A safely preflighted template upgrade entered the guarded repair queue"
                        : "A missing authored block suspended project benefits until it is restored in-world");
    }

    private static void recordQualityUpgradeRejection(
            ServerLevel level,
            EconomyState.VillageRecord village,
            EconomyState.VillageProject project,
            boolean qualityMigration,
            BlockPos position,
            int completedBlocks,
            int totalBlocks,
            String reason) {
        if (!qualityMigration) {
            return;
        }
        DebugFlightRecorder.recordConstruction(
                level,
                village.villageId,
                project.projectId,
                project.type.name(),
                "quality_upgrade_rejected",
                position,
                completedBlocks,
                totalBlocks,
                reason);
    }

    /**
     * Template upgrades never infer ownership from a matching block type. Every appended position
     * must still be empty or replaceable, free of block entities, and accepted by the protection
     * hook before the older project is demoted into the repair queue.
     */
    static boolean isSafeTemplateUpgradeTarget(
            BlockState current, boolean hasBlockEntity, boolean protectionAllowed) {
        return current != null
                && !hasBlockEntity
                && protectionAllowed
                && (current.isAir() || current.canBeReplaced());
    }

    private static boolean isSafeTemplateUpgradeTarget(
            BlockState current,
            boolean hasBlockEntity,
            boolean protectionAllowed,
            ServerLevel level,
            BlockPos target,
            Placement placement) {
        return current != null
                && !hasBlockEntity
                && protectionAllowed
                && !placement.isAccessClearance()
                && (!placement.isTrail() || trailHasClearance(level, target))
                && level.getFluidState(target).isEmpty()
                && mayApplyPlacement(current, placement);
    }

    private static boolean placementColumnLoaded(
            ServerLevel level, BlockPos origin, Placement placement) {
        int x = origin.getX() + placement.dx;
        int z = origin.getZ() + placement.dz;
        return level.hasChunk(Math.floorDiv(x, 16), Math.floorDiv(z, 16));
    }

    private static boolean positionColumnLoaded(ServerLevel level, BlockPos position) {
        return level.hasChunk(
                Math.floorDiv(position.getX(), 16), Math.floorDiv(position.getZ(), 16));
    }

    private static void normalizeAuthoredModularConnections(
            ServerLevel level,
            BlockPos origin,
            UUID villageId,
            long projectId,
            List<Placement> placements) {
        for (Placement placement : placements) {
            if (placement.role == PlacementRole.STRUCTURE
                    && isManagedConnectionBlock(placement.state)) {
                normalizeModularConnectionState(
                        level,
                        origin.offset(placement.dx, placement.dy, placement.dz),
                        placement.state,
                        villageId,
                        projectId);
            }
        }
    }

    /** Recomputes surviving authored panes/bars without recreating or replacing player blocks. */
    static boolean normalizeModularConnectionState(
            ServerLevel level,
            BlockPos position,
            BlockState authored,
            UUID villageId,
            long projectId) {
        if (authored == null
                || !isManagedConnectionBlock(authored)
                || !positionColumnLoaded(level, position)
                || !positionColumnLoaded(level, position.north())
                || !positionColumnLoaded(level, position.east())
                || !positionColumnLoaded(level, position.south())
                || !positionColumnLoaded(level, position.west())) {
            return false;
        }
        BlockState current = level.getBlockState(position);
        if (!current.is(authored.getBlock())) {
            return false;
        }
        BlockState updated = Block.updateFromNeighbourShapes(current, level, position);
        if (updated.equals(current)) {
            return true;
        }
        if (!VillageDevelopmentProtection.mayPlace(
                level, villageId, projectId, position, current, updated)) {
            return false;
        }
        return level.setBlock(
                        position,
                        updated,
                        Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE)
                && level.getBlockState(position).equals(updated);
    }

    private static boolean isManagedConnectionBlock(BlockState state) {
        return state != null && (state.is(Blocks.GLASS_PANE) || state.is(Blocks.IRON_BARS));
    }

    private static BlockPos placementTarget(
            ServerLevel level, BlockPos origin, Placement placement) {
        if (!placement.isTrail()) {
            return origin.offset(placement.dx, placement.dy, placement.dz);
        }
        int x = origin.getX() + placement.dx;
        int z = origin.getZ() + placement.dz;
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        return new BlockPos(x, y, z);
    }

    private static boolean placementSatisfied(
            ServerLevel level,
            BlockPos origin,
            BlockPos target,
            BlockState current,
            Placement placement) {
        if (placement.isAccessClearance()) {
            return current.isAir()
                    && level.getBlockEntity(target) == null
                    && level.getFluidState(target).isEmpty();
        }
        if (placement.isTrail()) {
            // Ordinary trail cells adopt any TES surface so branches can share infrastructure.
            // The one-time center migration accepts dirt path and intentional gravel accents;
            // only legacy coarse-dirt center cells need to be revisited.
            return VillageMaterializationPolicy.trailSurfaceSatisfied(
                    placement.isTrailCenterSurfaceRetrofit(),
                    isVillageTrailGround(current),
                    current.is(Blocks.DIRT_PATH) || current.is(Blocks.GRAVEL));
        }
        if (current.is(placement.state.getBlock())) {
            return (!placement.isStructuralAuthority()
                            && placement.role != PlacementRole.ENTRANCE_STAIR)
                    || structuralStateMatches(current, placement.state);
        }
        if (placement.role != PlacementRole.TERRAIN_SUPPORT
                && placement.role != PlacementRole.COSMETIC_SUPPORT
                && placement.role != PlacementRole.ENTRANCE_SUPPORT) {
            return false;
        }
        if (isSupportGround(current, placement)) {
            return true;
        }
        // Once a support column meets approved ground, deeper authored support cells are no-ops.
        // This prevents a shallow foundation from tunnelling into a cave beneath sound terrain.
        for (int y = placement.dy + 1; y <= 0; y++) {
            BlockPos above = origin.offset(placement.dx, y, placement.dz);
            if (isSupportGround(level.getBlockState(above), placement)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSupportGround(BlockState state, Placement placement) {
        return placement.role == PlacementRole.ENTRANCE_SUPPORT
                ? isEntranceApproachGround(state)
                : isNaturalProjectGround(state);
    }

    private static boolean structuralStateMatches(BlockState current, BlockState expected) {
        Block block = expected.getBlock();
        if (block instanceof DoorBlock
                && (!sameProperty(current, expected, DoorBlock.FACING)
                        || !sameProperty(current, expected, DoorBlock.HALF)
                        || !sameProperty(current, expected, DoorBlock.HINGE))) {
            return false;
        }
        if (block instanceof StairBlock
                && (!sameProperty(current, expected, StairBlock.FACING)
                        || !sameProperty(current, expected, StairBlock.HALF))) {
            return false;
        }
        if (block instanceof BedBlock
                && (!sameProperty(current, expected, BedBlock.FACING)
                        || !sameProperty(current, expected, BedBlock.PART))) {
            return false;
        }
        if (block instanceof LanternBlock
                && !sameProperty(current, expected, LanternBlock.HANGING)) {
            return false;
        }
        if (block instanceof RotatedPillarBlock
                && !sameProperty(current, expected, RotatedPillarBlock.AXIS)) {
            return false;
        }
        if (block instanceof SlabBlock
                && !sameProperty(current, expected, SlabBlock.TYPE)) {
            return false;
        }
        if (block instanceof BaseRailBlock rail
                && !sameProperty(current, expected, rail.getShapeProperty())) {
            return false;
        }
        return !expected.hasProperty(HorizontalDirectionalBlock.FACING)
                || sameProperty(current, expected, HorizontalDirectionalBlock.FACING);
    }

    private static <T extends Comparable<T>> boolean sameProperty(
            BlockState current, BlockState expected, Property<T> property) {
        return current.hasProperty(property)
                && expected.hasProperty(property)
                && current.getValue(property).equals(expected.getValue(property));
    }

    private static boolean mayApplyPlacement(BlockState current, Placement placement) {
        if (placement.isTrail()) {
            return VillageMaterializationPolicy.mayApplyTrailSurface(
                    placement.isTrailCenterSurfaceRetrofit(),
                    isPaveableTrailGround(current),
                    isVillageTrailGround(current),
                    current.is(Blocks.COARSE_DIRT));
        }
        return current.isAir() || current.canBeReplaced();
    }

    /**
     * Keeps optional yard dressing grounded without making its supports economically essential.
     *
     * <p>The authored origin is levelled from the required building lot, while side/rear doodads
     * may extend beyond that lot onto a drop. A cosmetic column is therefore eligible only when
     * natural terrain exists within the same bounded depth used by normal foundations. Each cell
     * must also satisfy vanilla attachment rules and touch a stable adjacent course. Checking all
     * six faces preserves legitimate hanging chains/lanterns and wall-mounted trim without letting
     * a disconnected full block pass merely because its default state can survive in air. If any
     * of those checks fail, the materialization cursor permanently adopts that cell as a gap.</p>
     */
    private static boolean cosmeticPlacementSupported(
            ServerLevel level, BlockPos origin, BlockPos target, Placement placement) {
        boolean groundedColumn = false;
        for (int dy = -1;
                dy >= -(TerrainFoundationPlan.MAX_TERRAIN_DROP + 1);
                dy--) {
            BlockPos ground = origin.offset(placement.dx, dy, placement.dz);
            if (!placementColumnLoaded(level, origin, placement)
                    || level.getBlockEntity(ground) != null
                    || !level.getFluidState(ground).isEmpty()) {
                return false;
            }
            if (isNaturalProjectGround(level.getBlockState(ground))) {
                groundedColumn = true;
                break;
            }
        }
        if (!groundedColumn) {
            return false;
        }
        // Vanilla survival checks may inspect a horizontal attachment across a chunk boundary.
        // Optional dressing must never force-load that neighbor merely to decide whether it stays.
        for (Direction direction : Direction.values()) {
            if (!positionColumnLoaded(level, target.relative(direction))) {
                return false;
            }
        }
        if (!placement.state.canSurvive(level, target)) {
            return false;
        }
        for (Direction direction : Direction.values()) {
            BlockPos attachment = target.relative(direction);
            BlockState support = level.getBlockState(attachment);
            if (level.getBlockEntity(attachment) == null
                    && level.getFluidState(attachment).isEmpty()
                    && !support.isAir()
                    && !support.canBeReplaced()) {
                return true;
            }
        }
        return false;
    }

    private static boolean trailHasClearance(ServerLevel level, BlockPos ground) {
        for (int offset = 1; offset <= 2; offset++) {
            BlockPos above = ground.above(offset);
            BlockState state = level.getBlockState(above);
            if (level.getBlockEntity(above) != null
                    || !level.getFluidState(above).isEmpty()
                    || (!state.isAir() && !state.canBeReplaced())) {
                return false;
            }
        }
        return true;
    }

    private static boolean previousPrimaryTrailColumnLoaded(
            ServerLevel level, BlockPos origin, List<Placement> trail, int index) {
        if (index <= 0 || trail.get(index).role != PlacementRole.TRAIL_PRIMARY) {
            return true;
        }
        Placement previous = trail.get(index - 1);
        return previous.role != PlacementRole.TRAIL_PRIMARY
                || placementColumnLoaded(level, origin, previous);
    }

    private static boolean trailGradeWalkable(
            ServerLevel level,
            BlockPos origin,
            List<Placement> trail,
            int index,
            BlockPos target) {
        if (index <= 0 || trail.get(index).role != PlacementRole.TRAIL_PRIMARY) {
            return true;
        }
        Placement previous = trail.get(index - 1);
        if (previous.role != PlacementRole.TRAIL_PRIMARY) {
            return true;
        }
        BlockPos previousTarget = placementTarget(level, origin, previous);
        return Math.abs(target.getY() - previousTarget.getY()) <= 1;
    }

    private static boolean isVillageTrailGround(BlockState state) {
        return state.is(Blocks.DIRT_PATH)
                || state.is(Blocks.GRAVEL)
                || state.is(Blocks.COARSE_DIRT);
    }

    private static ProjectSiteSearch findProjectOrigin(
            ServerLevel level,
            EconomyService economy,
            EconomyState.VillageRecord village,
            EconomyState.VillageProject project,
            List<EconomyService.VillageProjectLot> excludedProjectLots,
            List<Long> managedBankLots) {
        BlockPos center = BlockPos.of(village.centerPos);
        List<VillageMaterializationPolicy.SiteOffset> offsets =
                VillageMaterializationPolicy.projectSiteOffsets(
                        project.materializationFailures);
        int start = Math.floorMod(
                (int) (project.projectId ^ village.villageId.hashCode()), offsets.size());
        int testedCandidates = Math.min(project.siteSearchCursor, offsets.size());
        boolean sawUnloadedCandidate = project.siteSearchSawUnloadedCandidate;
        String persistedDialect = village.architectureDialect;
        String planningDialect = persistedDialect;
        if (isManagedProject(project) && planningDialect.isBlank()) {
            // The village center is already loaded. Locking this before candidate planning avoids
            // querying an unloaded frontier merely to choose a palette.
            planningDialect = biomeDialect(level, center).id();
        }
        int attempts = Math.min(
                PROJECT_SITE_CANDIDATES_PER_PULSE,
                offsets.size() - testedCandidates);
        for (int attempt = 0; attempt < attempts; attempt++) {
            if (isManagedProject(project)) {
                village.architectureDialect = planningDialect;
            }
            int step = testedCandidates++;
            VillageMaterializationPolicy.SiteOffset offset =
                    offsets.get((start + step) % offsets.size());
            int centerX = center.getX() + offset.x();
            int centerZ = center.getZ() + offset.z();
            BlockPos candidateTrailAnchor = null;
            if (isManagedProject(project)) {
                candidateTrailAnchor = trailAnchor(
                        centerX, center.getY(), centerZ, village, project);
                project.designRotation = VillageArchitecture.rotationToward(
                        centerX,
                        centerZ,
                        candidateTrailAnchor.getX(),
                        candidateTrailAnchor.getZ());
            }
            StructureSize siteSize = rotatedSize(projectSize(project), project.designRotation);
            List<TerrainFoundationPlan.Column> authoritativeGroundContact = List.of();
            if (isBlueprint(project)) {
                BlockPos provisionalOrigin = new BlockPos(
                        centerX - siteSize.width / 2,
                        center.getY(),
                        centerZ - siteSize.depth / 2);
                authoritativeGroundContact = authoritativeGroundContactColumns(
                        blueprintPlacementPlan(level, provisionalOrigin, village, project).base());
            }
            ProjectSiteSearch originSearch = safeOrigin(
                    level,
                    centerX,
                    centerZ,
                    siteSize,
                    authoritativeGroundContact);
            if (originSearch.availability
                    == VillageMaterializationPolicy.SiteAvailability.INCOMPLETE_UNLOADED) {
                sawUnloadedCandidate = true;
                checkpointProjectSiteSearch(
                        economy, village, project, testedCandidates, sawUnloadedCandidate);
                continue;
            }
            if (originSearch.availability
                    != VillageMaterializationPolicy.SiteAvailability.AVAILABLE) {
                checkpointProjectSiteSearch(
                        economy, village, project, testedCandidates, sawUnloadedCandidate);
                continue;
            }
            BlockPos origin = originSearch.origin;
            if (isManagedProject(project)) {
                village.architectureDialect = planningDialect;
            }
            if (village.bankAnchorPos != 0L
                    && origin.distSqr(BlockPos.of(village.bankAnchorPos)) < 18.0 * 18.0) {
                village.architectureDialect = persistedDialect;
                checkpointProjectSiteSearch(
                        economy, village, project, testedCandidates, sawUnloadedCandidate);
                continue;
            }
            EconomyState.VillageProject planningProject = project;
            EntranceApproachSearch entranceApproach = EntranceApproachSearch.flat();
            if (isManagedProject(project)) {
                planningProject = project.copy();
                planningProject.trailAnchorSet = true;
                planningProject.trailAnchorPos = candidateTrailAnchor.asLong();
                entranceApproach = planModularEntranceApproach(
                        level, origin, village, planningProject);
                if (entranceApproach.availability
                        == VillageMaterializationPolicy.SiteAvailability.INCOMPLETE_UNLOADED) {
                    sawUnloadedCandidate = true;
                    village.architectureDialect = persistedDialect;
                    checkpointProjectSiteSearch(
                            economy, village, project, testedCandidates, sawUnloadedCandidate);
                    continue;
                }
                if (entranceApproach.availability
                        != VillageMaterializationPolicy.SiteAvailability.AVAILABLE) {
                    village.architectureDialect = persistedDialect;
                    checkpointProjectSiteSearch(
                            economy, village, project, testedCandidates, sawUnloadedCandidate);
                    continue;
                }
                planningProject.entranceApproachVersion =
                        EconomyState.ENTRANCE_APPROACH_VERSION;
                planningProject.entranceApproachStepCount = entranceApproach.stepCount;
                planningProject.entranceApproachCursor = 0;
                planningProject.entranceApproachTotalCells =
                        entranceApproach.placements.size();
                planningProject.entranceApproachComplete =
                        entranceApproach.placements.isEmpty();
            }
            List<Placement> planned = projectTemplate(
                    level, origin, village, planningProject);
            VillageMaterializationPolicy.SiteAvailability siteAvailability = mayUseProjectSite(
                    level, village.villageId, project.projectId, origin, planned);
            if (siteAvailability
                    == VillageMaterializationPolicy.SiteAvailability.INCOMPLETE_UNLOADED) {
                sawUnloadedCandidate = true;
                village.architectureDialect = persistedDialect;
                checkpointProjectSiteSearch(
                        economy, village, project, testedCandidates, sawUnloadedCandidate);
                continue;
            }
            if (siteAvailability != VillageMaterializationPolicy.SiteAvailability.AVAILABLE) {
                village.architectureDialect = persistedDialect;
                checkpointProjectSiteSearch(
                        economy, village, project, testedCandidates, sawUnloadedCandidate);
                continue;
            }
            ProjectBounds candidateBounds = bounds(origin, planned);
            boolean overlaps = village.projects.stream()
                    .filter(other -> other.originPos != 0L && other.projectId != project.projectId)
                    .map(VillageProsperityManager::projectBounds)
                    .anyMatch(other -> overlaps(candidateBounds, other, 2));
            boolean overlapsExcludedProjectSite = excludedProjectLots.stream()
                    .map(VillageProsperityManager::excludedProjectBounds)
                    .anyMatch(other -> overlapsHorizontally(candidateBounds, other, 4));
            boolean overlapsManagedBank = managedBankLots.stream().anyMatch(anchor ->
                    VillageBankManager.overlapsManagedBankLot(
                            anchor,
                            candidateBounds.minimum.getX(),
                            candidateBounds.maximum.getX(),
                            candidateBounds.minimum.getZ(),
                            candidateBounds.maximum.getZ(),
                            4));
            if (!overlaps && !overlapsExcludedProjectSite && !overlapsManagedBank) {
                return new ProjectSiteSearch(
                        origin,
                        candidateTrailAnchor,
                        entranceApproach.stepCount,
                        entranceApproach.placements.size(),
                        VillageMaterializationPolicy.SiteAvailability.AVAILABLE);
            }
            village.architectureDialect = persistedDialect;
            checkpointProjectSiteSearch(
                    economy, village, project, testedCandidates, sawUnloadedCandidate);
        }
        village.architectureDialect = persistedDialect;
        if (testedCandidates < offsets.size()) {
            return new ProjectSiteSearch(
                    null, VillageMaterializationPolicy.SiteAvailability.SEARCH_INCOMPLETE);
        }
        return new ProjectSiteSearch(
                null,
                VillageMaterializationPolicy.completedSiteSearch(
                        false, sawUnloadedCandidate));
    }

    private static void checkpointProjectSiteSearch(
            EconomyService economy,
            EconomyState.VillageRecord village,
            EconomyState.VillageProject project,
            int testedCandidates,
            boolean sawUnloadedCandidate) {
        project.siteSearchCursor = Math.max(project.siteSearchCursor, testedCandidates);
        project.siteSearchSawUnloadedCandidate |= sawUnloadedCandidate;
        economy.recordVillageProjectSiteSearchProgress(
                village.villageId,
                project.projectId,
                project.siteSearchCursor,
                project.siteSearchSawUnloadedCandidate);
    }

    private static VillageMaterializationPolicy.SiteAvailability mayUseProjectSite(
            ServerLevel level,
            UUID villageId,
            long projectId,
            BlockPos origin,
            List<Placement> placements) {
        // Finish the chunk-only preflight before any height, block-state, or protection read. A
        // partial view cannot prove the lot unsafe and must not trigger persistent failure backoff.
        for (Placement placement : placements) {
            if (!placement.isTrail()
                    && !placement.isCosmetic()
                    && !placementColumnLoaded(level, origin, placement)) {
                return VillageMaterializationPolicy.SiteAvailability.INCOMPLETE_UNLOADED;
            }
        }
        BlockPos previousPrimaryTrail = null;
        for (Placement placement : placements) {
            if ((placement.isTrail() || placement.isCosmetic())
                    && !placementColumnLoaded(level, origin, placement)) {
                continue;
            }
            BlockPos target = placementTarget(level, origin, placement);
            BlockState existing = level.getBlockState(target);
            if (placement.isAccessClearance()) {
                if (!placementSatisfied(level, origin, target, existing, placement)) {
                    return VillageMaterializationPolicy.SiteAvailability.UNSAFE;
                }
                continue;
            }
            if (placement.role == PlacementRole.TRAIL_PRIMARY) {
                if (previousPrimaryTrail != null
                        && Math.abs(target.getY() - previousPrimaryTrail.getY()) > 1) {
                    previousPrimaryTrail = target;
                    continue;
                }
                previousPrimaryTrail = target;
            }
            if (!placement.isStructuralAuthority()
                    && placementSatisfied(level, origin, target, existing, placement)) {
                continue;
            }
            boolean protectionAllowed = VillageDevelopmentProtection.mayPlace(
                            level,
                            villageId,
                            projectId,
                            target,
                            existing,
                            placement.state);
            if (!isSafeTemplateUpgradeTarget(
                    existing,
                    level.getBlockEntity(target) != null,
                    protectionAllowed,
                    level,
                    target,
                    placement)) {
                if (placement.isTrail() || placement.isCosmetic()) {
                    continue;
                }
                return VillageMaterializationPolicy.SiteAvailability.UNSAFE;
            }
        }
        return VillageMaterializationPolicy.SiteAvailability.AVAILABLE;
    }

    private static List<TerrainFoundationPlan.Column> authoritativeGroundContactColumns(
            List<Placement> transformedBase) {
        List<TerrainFoundationPlan.Cell> authoritative = new ArrayList<>();
        for (Placement placement : transformedBase) {
            if (placement.isTrail()
                    || !placement.isGroundAuthoritative()
                    || placement.isAccessClearance()) {
                continue;
            }
            authoritative.add(new TerrainFoundationPlan.Cell(
                    placement.dx, placement.dy, placement.dz));
        }
        return TerrainFoundationPlan.groundContactColumns(authoritative);
    }

    private static ProjectSiteSearch safeOrigin(
            ServerLevel level,
            int centerX,
            int centerZ,
            StructureSize size,
            List<TerrainFoundationPlan.Column> authoritativeGroundContact) {
        // This is deliberately the required building/approach envelope. Blueprint V2 side and
        // rear dressing may extend farther, but reserving only sites with an entirely empty yard
        // would make harmless player landscaping prevent essential village construction. Those
        // outlying COSMETIC cells receive a loaded/protection/ground-depth check at their single
        // materialization attempt and are permanently skipped when the local terrain is unsafe.
        int originX = centerX - size.width / 2;
        int originZ = centerZ - size.depth / 2;
        List<TerrainFoundationPlan.Column> terrainColumns = new ArrayList<>();
        Set<Long> seenColumns = new HashSet<>();
        for (int x = centerX - size.width / 2 - 1;
                x <= centerX + size.width / 2 + 1;
                x++) {
            for (int z = centerZ - size.depth / 2 - 2;
                    z <= centerZ + size.depth / 2 + 1;
                    z++) {
                if (seenColumns.add(packXZ(x, z))) {
                    terrainColumns.add(new TerrainFoundationPlan.Column(x, z));
                }
            }
        }
        for (TerrainFoundationPlan.Column relative : authoritativeGroundContact) {
            int x = originX + relative.x();
            int z = originZ + relative.z();
            if (seenColumns.add(packXZ(x, z))) {
                terrainColumns.add(new TerrainFoundationPlan.Column(x, z));
            }
        }
        int minimumX = terrainColumns.stream().mapToInt(TerrainFoundationPlan.Column::x)
                .min().orElse(originX - 1);
        int maximumX = terrainColumns.stream().mapToInt(TerrainFoundationPlan.Column::x)
                .max().orElse(originX + size.width);
        int minimumZ = terrainColumns.stream().mapToInt(TerrainFoundationPlan.Column::z)
                .min().orElse(originZ - 2);
        int maximumZ = terrainColumns.stream().mapToInt(TerrainFoundationPlan.Column::z)
                .max().orElse(originZ + size.depth);
        if (!areaColumnsLoaded(level, minimumX, maximumX, minimumZ, maximumZ)) {
            return new ProjectSiteSearch(
                    null,
                    VillageMaterializationPolicy.SiteAvailability.INCOMPLETE_UNLOADED);
        }
        int minimum = Integer.MAX_VALUE;
        int maximum = Integer.MIN_VALUE;
        for (TerrainFoundationPlan.Column column : terrainColumns) {
            int surface = level.getHeight(
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.x(), column.z());
            BlockPos ground = new BlockPos(column.x(), surface - 1, column.z());
            BlockState groundState = level.getBlockState(ground);
            if (groundState.isAir()
                    || level.getBlockEntity(ground) != null
                    || !level.getFluidState(ground).isEmpty()
                    || !isNaturalProjectGround(groundState)) {
                return new ProjectSiteSearch(
                        null, VillageMaterializationPolicy.SiteAvailability.UNSAFE);
            }
            minimum = Math.min(minimum, surface);
            maximum = Math.max(maximum, surface);
        }
        if (!TerrainFoundationPlan.supportsTerrainRange(
                minimum, maximum, TerrainFoundationPlan.MAX_TERRAIN_DROP)) {
            return new ProjectSiteSearch(
                    null, VillageMaterializationPolicy.SiteAvailability.UNSAFE);
        }
        // Level at the highest sampled natural surface. The deterministic terrain-support suffix
        // bridges only small drops; natural ground satisfies a support without being replaced.
        BlockPos origin = new BlockPos(
                originX,
                maximum,
                originZ);
        for (TerrainFoundationPlan.Column column : terrainColumns) {
            for (int y = 0; y <= size.height; y++) {
                BlockPos target = new BlockPos(column.x(), maximum + y, column.z());
                BlockState state = level.getBlockState(target);
                if (level.getBlockEntity(target) != null
                        || !level.getFluidState(target).isEmpty()
                        || (!state.isAir() && !state.canBeReplaced())) {
                    return new ProjectSiteSearch(
                            null, VillageMaterializationPolicy.SiteAvailability.UNSAFE);
                }
            }
        }
        return new ProjectSiteSearch(
                origin, VillageMaterializationPolicy.SiteAvailability.AVAILABLE);
    }

    private static BlockPos surfaceVillageProbe(ServerLevel level, BlockPos playerPosition) {
        int surface = level.getHeight(
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                playerPosition.getX(),
                playerPosition.getZ());
        return new BlockPos(playerPosition.getX(), surface, playerPosition.getZ());
    }

    private static boolean areaColumnsLoaded(
            ServerLevel level, int minimumX, int maximumX, int minimumZ, int maximumZ) {
        int minimumChunkX = Math.floorDiv(minimumX, 16);
        int maximumChunkX = Math.floorDiv(maximumX, 16);
        int minimumChunkZ = Math.floorDiv(minimumZ, 16);
        int maximumChunkZ = Math.floorDiv(maximumZ, 16);
        for (int chunkX = minimumChunkX; chunkX <= maximumChunkX; chunkX++) {
            for (int chunkZ = minimumChunkZ; chunkZ <= maximumChunkZ; chunkZ++) {
                if (!level.hasChunk(chunkX, chunkZ)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static ProjectBounds bounds(BlockPos origin, List<Placement> placements) {
        int minimumX = Integer.MAX_VALUE;
        int minimumY = Integer.MAX_VALUE;
        int minimumZ = Integer.MAX_VALUE;
        int maximumX = Integer.MIN_VALUE;
        int maximumY = Integer.MIN_VALUE;
        int maximumZ = Integer.MIN_VALUE;
        for (Placement placement : placements) {
            // Trails deliberately leave the project's exclusive AABB. They are independently
            // guarded at placement time and may be shared by later branches.
            if (placement.isTrail()) {
                continue;
            }
            BlockPos position = origin.offset(placement.dx, placement.dy, placement.dz);
            minimumX = Math.min(minimumX, position.getX());
            minimumY = Math.min(minimumY, position.getY());
            minimumZ = Math.min(minimumZ, position.getZ());
            maximumX = Math.max(maximumX, position.getX());
            maximumY = Math.max(maximumY, position.getY());
            maximumZ = Math.max(maximumZ, position.getZ());
        }
        if (minimumX == Integer.MAX_VALUE) {
            return new ProjectBounds(origin, origin);
        }
        return new ProjectBounds(
                new BlockPos(minimumX, minimumY, minimumZ),
                new BlockPos(maximumX, maximumY, maximumZ));
    }

    private static ProjectBounds projectBounds(EconomyState.VillageProject project) {
        if (project.boundsMinPos != 0L || project.boundsMaxPos != 0L) {
            return new ProjectBounds(
                    BlockPos.of(project.boundsMinPos), BlockPos.of(project.boundsMaxPos));
        }
        BlockPos origin = BlockPos.of(project.originPos);
        StructureSize structure = rotatedSize(projectSize(project), project.designRotation);
        VillageMaterializationPolicy.RelativeBounds relative =
                VillageMaterializationPolicy.conservativeProjectBounds(
                        structure.width,
                        structure.depth,
                        structure.height,
                        TerrainFoundationPlan.MAX_TERRAIN_DROP);
        return new ProjectBounds(
                origin.offset(relative.minimumX(), relative.minimumY(), relative.minimumZ()),
                origin.offset(relative.maximumX(), relative.maximumY(), relative.maximumZ()));
    }

    private static ProjectBounds excludedProjectBounds(EconomyService.VillageProjectLot lot) {
        return new ProjectBounds(
                BlockPos.of(lot.boundsMinPos()),
                BlockPos.of(lot.boundsMaxPos()));
    }

    private static boolean overlaps(
            ProjectBounds first, ProjectBounds second, int horizontalMargin) {
        return first.minimum.getX() - horizontalMargin
                        <= second.maximum.getX() + horizontalMargin
                && first.maximum.getX() + horizontalMargin
                        >= second.minimum.getX() - horizontalMargin
                && first.minimum.getY() <= second.maximum.getY()
                && first.maximum.getY() >= second.minimum.getY()
                && first.minimum.getZ() - horizontalMargin
                        <= second.maximum.getZ() + horizontalMargin
                && first.maximum.getZ() + horizontalMargin
                        >= second.minimum.getZ() - horizontalMargin;
    }

    /** Retired player-edited lots remain excluded even when later terrain selects another Y. */
    private static boolean overlapsHorizontally(
            ProjectBounds first, ProjectBounds second, int horizontalMargin) {
        return first.minimum.getX() - horizontalMargin
                        <= second.maximum.getX() + horizontalMargin
                && first.maximum.getX() + horizontalMargin
                        >= second.minimum.getX() - horizontalMargin
                && first.minimum.getZ() - horizontalMargin
                        <= second.maximum.getZ() + horizontalMargin
                && first.maximum.getZ() + horizontalMargin
                        >= second.minimum.getZ() - horizontalMargin;
    }

    /**
     * Builds an append-only, save-stable project plan. The legacy template is always the exact
     * prefix so projects from older saves can enter the existing guarded upgrade queue.
     */
    private static List<Placement> projectTemplate(
            ServerLevel level,
            BlockPos origin,
            EconomyState.VillageRecord village,
            EconomyState.VillageProject project) {
        if (isBlueprint(project)) {
            return blueprintProjectTemplate(level, origin, village, project);
        }
        if (isModular(project)) {
            return modularProjectTemplate(level, origin, village, project);
        }
        Palette palette = palette(level, origin);
        List<Placement> legacy = template(level, origin, project.type);
        int variant = VillageStructureProgression.variant(
                village.villageId, project.projectId, project.type);
        ProgressionLayers layers = progressionLayers(legacy, palette, project.type, variant);

        List<Placement> result = new ArrayList<>(legacy.size()
                + layers.supports.size()
                + layers.variation.size()
                + layers.stageOne.size()
                + layers.stageTwo.size()
                + 96);
        result.addAll(legacy);
        result.addAll(layers.supports);
        result.addAll(layers.variation);
        // Legacy project roads are embedded before later building stages. Their original narrow
        // list must stay exact; modular roads have an independent cursor for appended retrofits.
        result.addAll(projectTrail(palette, origin, village, project, false));
        int baselineBlocks = result.size();
        int stageOneBlocks = baselineBlocks + layers.stageOne.size();
        int stageTwoBlocks = stageOneBlocks + layers.stageTwo.size();
        int persistedBlocks = project.originPos == 0L ? 0 : Math.max(0, project.totalBlocks);
        int visualStage = VillageStructureProgression.constructionVisualStage(
                village.developmentTier,
                persistedBlocks,
                baselineBlocks,
                stageOneBlocks,
                stageTwoBlocks,
                project.originPos != 0L,
                project.materializedComplete);
        if (visualStage >= 1) {
            result.addAll(layers.stageOne);
        }
        if (visualStage >= 2) {
            result.addAll(layers.stageTwo);
        }
        return List.copyOf(result);
    }

    private static List<Placement> blueprintProjectTemplate(
            ServerLevel level,
            BlockPos origin,
            EconomyState.VillageRecord village,
            EconomyState.VillageProject project) {
        int visualStage = project.originPos == 0L
                ? VillageStructureProgression.desiredVisualStage(village.developmentTier)
                : project.designStage;
        return blueprintProjectTemplate(level, origin, village, project, visualStage);
    }

    private static List<Placement> blueprintProjectTemplate(
            ServerLevel level,
            BlockPos origin,
            EconomyState.VillageRecord village,
            EconomyState.VillageProject project,
            int visualStage) {
        if (visualStage < 0 || visualStage > VillageStructureProgression.MAX_VISUAL_STAGE) {
            throw new IllegalStateException(
                    "Invalid persisted Blueprint V2 visual stage " + visualStage);
        }
        BlueprintPlacementPlan plan;
        try {
            plan = blueprintPlacementPlan(level, origin, village, project);
        } catch (BlueprintPlanMismatchException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new BlueprintPlanMismatchException(
                    village.villageId,
                    project.projectId,
                    "Blueprint V2 template is invalid for project " + project.projectId,
                    exception);
        }
        String canonicalHash = blueprintPlanHash(village, project, plan);
        if (!project.designPlanHash.isBlank()
                && !project.designPlanHash.equals(canonicalHash)) {
            throw new BlueprintPlanMismatchException(
                    village.villageId,
                    project.projectId,
                    "Blueprint V2 plan hash changed for project " + project.projectId);
        }
        return orderedBlueprintLayers(plan.base, plan.stageOne, plan.stageTwo, visualStage);
    }

    private static BlueprintPlacementPlan blueprintPlacementPlan(
            ServerLevel level,
            BlockPos origin,
            EconomyState.VillageRecord village,
            EconomyState.VillageProject project) {
        VillageArchitecture.Character character = village.architectureCharacter.isBlank()
                ? VillageArchitecture.character(village.villageId)
                : VillageArchitecture.Character.fromId(village.architectureCharacter);
        VillageArchitecture.BiomeDialect dialect = village.architectureDialect.isBlank()
                ? biomeDialect(level, origin)
                : VillageArchitecture.BiomeDialect.fromId(village.architectureDialect);
        AuthoredVillageStructures.Blueprint blueprint = AuthoredVillageStructures.plan(
                project.type,
                project.designTemplateId,
                project.designTemplateRevision,
                project.designPaletteId,
                project.designDressingId,
                character,
                dialect,
                project.designSeed);
        StructureSize structure = projectSize(project);
        Set<BlockPos> requiredSafetyFixtures = cumulativeRequiredSafetyFixturePositions(
                blueprint.base(), blueprint.stageOne(), blueprint.stageTwo());

        List<Placement> semanticBase = new ArrayList<>(
                toBlueprintPlacements(
                        blueprint.base(), PlacementRole.STRUCTURE, requiredSafetyFixtures));
        semanticBase.addAll(blueprintAccessClearances(blueprint));
        List<Placement> canonicalBase = withFoundationSupports(
                List.of(), semanticBase, blueprint.materials().foundation());
        List<Placement> canonicalStageOne = withFoundationSupports(
                canonicalBase,
                toBlueprintPlacements(
                        blueprint.stageOne(), PlacementRole.COSMETIC, requiredSafetyFixtures),
                blueprint.materials().foundation());
        List<Placement> throughStageOne = new ArrayList<>(
                canonicalBase.size() + canonicalStageOne.size());
        throughStageOne.addAll(canonicalBase);
        throughStageOne.addAll(canonicalStageOne);
        List<Placement> canonicalStageTwo = withFoundationSupports(
                throughStageOne,
                toBlueprintPlacements(
                        blueprint.stageTwo(), PlacementRole.COSMETIC, requiredSafetyFixtures),
                blueprint.materials().foundation());

        List<Placement> base = rotatePlacements(
                mirrorPlacements(canonicalBase, structure, project.designMirrored),
                structure,
                project.designRotation);
        List<Placement> stageOne = rotatePlacements(
                mirrorPlacements(canonicalStageOne, structure, project.designMirrored),
                structure,
                project.designRotation);
        List<Placement> stageTwo = rotatePlacements(
                mirrorPlacements(canonicalStageTwo, structure, project.designMirrored),
                structure,
                project.designRotation);
        return new BlueprintPlacementPlan(
                List.copyOf(base),
                List.copyOf(stageOne),
                List.copyOf(stageTwo),
                List.copyOf(canonicalBase),
                List.copyOf(canonicalStageOne),
                List.copyOf(canonicalStageTwo),
                VillageArchitecture.requireBlueprint(
                        project.designTemplateId, project.designTemplateRevision),
                character.id(),
                dialect.id(),
                blueprint.materials());
    }

    private static List<Placement> orderedBlueprintLayers(
            List<Placement> base,
            List<Placement> stageOne,
            List<Placement> stageTwo,
            int visualStage) {
        List<Placement> result = new ArrayList<>(
                base.size() + stageOne.size() + stageTwo.size());
        result.addAll(base);
        if (visualStage >= 1) {
            result.addAll(stageOne);
        }
        if (visualStage >= 2) {
            result.addAll(stageTwo);
        }
        return List.copyOf(result);
    }

    private static String blueprintPlanHash(
            EconomyState.VillageRecord village,
            EconomyState.VillageProject project,
            BlueprintPlacementPlan plan) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            updateBlueprintDigest(digest, VillageArchitecture.BLUEPRINT_SCHEMA);
            updateBlueprintDigest(digest, Integer.toString(
                    VillageArchitecture.BLUEPRINT_PLAN_HASH_VERSION));
            updateBlueprintDigest(digest, project.type.name());
            updateBlueprintDigest(digest, project.designTemplateId);
            updateBlueprintDigest(digest, Integer.toString(project.designTemplateRevision));
            updateBlueprintDigest(digest, project.designPaletteId);
            updateBlueprintDigest(digest, project.designDressingId);
            updateBlueprintDigest(digest, Boolean.toString(project.designMirrored));
            updateBlueprintDigest(digest, plan.characterId);
            updateBlueprintDigest(digest, plan.dialectId);
            updateBlueprintDigest(digest, Integer.toString(plan.descriptor.width()));
            updateBlueprintDigest(digest, Integer.toString(plan.descriptor.depth()));
            updateBlueprintDigest(digest, Integer.toString(plan.descriptor.height()));
            updateBlueprintDigest(digest, Boolean.toString(plan.descriptor.mirrorable()));
            updateBlueprintDigest(digest, "base");
            updateBlueprintDigest(digest, Integer.toString(plan.canonicalBase.size()));
            for (Placement placement : plan.canonicalBase) {
                updateBlueprintDigest(
                        digest,
                        placement.dx + "," + placement.dy + "," + placement.dz
                                + ":" + placement.role + ":"
                                + canonicalBlockState(placement.state));
            }
            updateBlueprintDigest(digest, "stage_one");
            updateBlueprintDigest(digest, Integer.toString(plan.canonicalStageOne.size()));
            for (Placement placement : plan.canonicalStageOne) {
                updateBlueprintDigest(
                        digest,
                        placement.dx + "," + placement.dy + "," + placement.dz
                                + ":" + placement.role + ":"
                                + canonicalBlockState(placement.state));
            }
            updateBlueprintDigest(digest, "stage_two");
            updateBlueprintDigest(digest, Integer.toString(plan.canonicalStageTwo.size()));
            for (Placement placement : plan.canonicalStageTwo) {
                updateBlueprintDigest(
                        digest,
                        placement.dx + "," + placement.dy + "," + placement.dz
                                + ":" + placement.role + ":"
                                + canonicalBlockState(placement.state));
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM is missing SHA-256", exception);
        }
    }

    private static void updateBlueprintDigest(MessageDigest digest, String value) {
        digest.update(value.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
    }

    private static String canonicalBlockState(BlockState state) {
        StringBuilder result = new StringBuilder(
                BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
        state.getProperties().stream()
                .sorted(Comparator.comparing(Property::getName))
                .forEach(property -> result.append(';')
                        .append(property.getName())
                        .append('=')
                        .append(propertyValueName(state, property)));
        return result.toString();
    }

    private static <T extends Comparable<T>> String propertyValueName(
            BlockState state, Property<T> property) {
        return property.getName(state.getValue(property));
    }

    private static List<Placement> modularProjectTemplate(
            ServerLevel level,
            BlockPos origin,
            EconomyState.VillageRecord village,
            EconomyState.VillageProject project) {
        int visualStage = project.originPos == 0L
                ? VillageStructureProgression.desiredVisualStage(village.developmentTier)
                : project.designStage;
        return modularProjectTemplate(level, origin, village, project, visualStage);
    }

    private static List<Placement> modularProjectTemplate(
            ServerLevel level,
            BlockPos origin,
            EconomyState.VillageRecord village,
            EconomyState.VillageProject project,
            int visualStage) {
        return modularProjectTemplate(
                level, origin, village, project, visualStage, false);
    }

    private static List<Placement> modularProjectTemplate(
            ServerLevel level,
            BlockPos origin,
            EconomyState.VillageRecord village,
            EconomyState.VillageProject project,
            int visualStage,
            boolean includeQualityMigration) {
        VillageArchitecture.Character character = village.architectureCharacter.isBlank()
                ? VillageArchitecture.character(village.villageId)
                : VillageArchitecture.Character.fromId(village.architectureCharacter);
        VillageArchitecture.BiomeDialect dialect = village.architectureDialect.isBlank()
                ? biomeDialect(level, origin)
                : VillageArchitecture.BiomeDialect.fromId(village.architectureDialect);
        VillageArchitecture.Recipe recipe = new VillageArchitecture.Recipe(
                project.designSeed,
                project.designSilhouette,
                project.designRoof,
                project.designFrontage,
                project.designMirrored,
                project.designSignature);
        StructureSize structure = projectSize(project);
        ModularVillageStructures.Layers layers = ModularVillageStructures.plan(
                project.type,
                recipe,
                character,
                dialect,
                structure.width,
                structure.depth,
                structure.height);

        List<Placement> base = withFoundationSupports(
                List.of(), toPlacements(layers.base()), layers.materials().foundation());
        List<Placement> stageOne = withFoundationSupports(
                base, toPlacements(layers.stageOne()), layers.materials().foundation());
        List<Placement> throughStageOne = new ArrayList<>(base.size() + stageOne.size());
        throughStageOne.addAll(base);
        throughStageOne.addAll(stageOne);
        List<Placement> stageTwo = withFoundationSupports(
                throughStageOne,
                toPlacements(layers.stageTwo()),
                layers.materials().foundation());
        List<Placement> quality = withFoundationSupports(
                base, toPlacements(layers.quality()), layers.materials().foundation());

        int rotation = project.designRotation;
        List<Placement> rotatedBase = rotatePlacements(base, structure, rotation);
        List<Placement> rotatedStageOne = rotatePlacements(stageOne, structure, rotation);
        List<Placement> rotatedStageTwo = rotatePlacements(stageTwo, structure, rotation);
        List<Placement> rotatedQuality = rotatePlacements(quality, structure, rotation);

        if (visualStage < 0 || visualStage > VillageStructureProgression.MAX_VISUAL_STAGE) {
            throw new IllegalStateException("Invalid persisted modular visual stage " + visualStage);
        }
        boolean includeQuality = project.designQualityStage >= 0 || includeQualityMigration;
        int qualityInsertionStage = project.designQualityStage >= 0
                ? project.designQualityStage
                : visualStage;
        if (includeQuality
                && (qualityInsertionStage < 0 || qualityInsertionStage > visualStage)) {
            throw new IllegalStateException(
                    "Invalid persisted modular quality insertion stage "
                            + qualityInsertionStage
                            + " for visual stage "
                            + visualStage);
        }
        return orderedModularLayers(
                rotatedBase,
                rotatedStageOne,
                rotatedStageTwo,
                rotatedQuality,
                visualStage,
                includeQuality ? qualityInsertionStage : -1);
    }

    /**
     * Resolves the exact production Blueprint V2 building for the opt-in structure review gallery.
     *
     * <p>The returned blocks have already passed the same satisfied/support and replaceability
     * checks used by ordinary construction. No economy village or project is registered: gallery
     * fixtures must never participate in settlement simulation, integrity audits, or relocation.
     */
    static List<StructureGalleryBlock> galleryProjectBlueprint(
            ServerLevel level,
            BlockPos origin,
            VillageProsperityEngine.ProjectType type,
            VillageArchitecture.BiomeDialect dialect,
            VillageArchitecture.Character character,
            String templateId,
            int templateRevision,
            String paletteId,
            String dressingId,
            boolean mirrored,
            int visualStage,
            int rotation) {
        if (!StructureGallery.enabled()) {
            throw new IllegalStateException("Structure gallery JVM opt-in is not enabled");
        }
        long signature = VillageArchitecture.blueprintSignature(
                type,
                templateId,
                templateRevision,
                paletteId,
                dressingId,
                mirrored);
        EconomyState.VillageRecord village = new EconomyState.VillageRecord();
        village.villageId = new UUID(
                0x54455347414c4c45L,
                signature ^ ((long) type.ordinal() << 32));
        village.developmentTier = 5;
        village.architectureDialect = dialect.id();
        village.architectureCharacter = character.id();

        EconomyState.VillageProject project = new EconomyState.VillageProject();
        project.projectId = signature;
        project.type = type;
        project.originPos = origin.asLong();
        project.designSchema = VillageArchitecture.BLUEPRINT_SCHEMA;
        project.designTemplateId = templateId;
        project.designTemplateRevision = templateRevision;
        project.designPaletteId = paletteId;
        project.designDressingId = dressingId;
        project.designPlanHashVersion = VillageArchitecture.BLUEPRINT_PLAN_HASH_VERSION;
        project.designMirrored = mirrored;
        project.designRotation = rotation;
        project.designSignature = signature;
        project.designSeed = signature;
        project.designStage = visualStage;
        project.designQualityStage = -1;

        List<Placement> production = blueprintProjectTemplate(
                level, origin, village, project, visualStage);
        List<StructureGalleryBlock> resolved = new ArrayList<>(production.size());
        for (Placement placement : production) {
            BlockPos target = placementTarget(level, origin, placement);
            BlockState current = level.getBlockState(target);
            if (placementSatisfied(level, origin, target, current, placement)) {
                continue;
            }
            if (level.getBlockEntity(target) != null
                    || !level.getFluidState(target).isEmpty()
                    || !mayApplyPlacement(current, placement)) {
                throw new IllegalStateException(
                        "Gallery plot is obstructed at " + target.toShortString());
            }
            resolved.add(new StructureGalleryBlock(target, placement.state));
        }
        return List.copyOf(resolved);
    }

    /** Recomputes pane/bar connections after a complete gallery blueprint has been placed. */
    static void normalizeGalleryConnections(
            ServerLevel level, List<StructureGalleryBlock> blocks) {
        for (StructureGalleryBlock block : blocks) {
            if (!isManagedConnectionBlock(block.state())) {
                continue;
            }
            BlockState current = level.getBlockState(block.position());
            if (!current.is(block.state().getBlock())) {
                continue;
            }
            BlockState updated = Block.updateFromNeighbourShapes(
                    current, level, block.position());
            if (!updated.equals(current)) {
                level.setBlock(
                        block.position(),
                        updated,
                        Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            }
        }
    }

    /** Orders a quality layer around its durable insertion stage without moving any old cells. */
    private static List<Placement> orderedModularLayers(
            List<Placement> base,
            List<Placement> stageOne,
            List<Placement> stageTwo,
            List<Placement> quality,
            int visualStage,
            int qualityInsertionStage) {
        List<Placement> result = new ArrayList<>(
                base.size() + stageOne.size() + stageTwo.size() + quality.size());
        result.addAll(base);
        if (qualityInsertionStage == 0) {
            result.addAll(quality);
        }
        if (visualStage >= 1) {
            result.addAll(stageOne);
            if (qualityInsertionStage == 1) {
                result.addAll(quality);
            }
        }
        if (visualStage >= 2) {
            result.addAll(stageTwo);
            if (qualityInsertionStage == 2) {
                result.addAll(quality);
            }
        }
        return List.copyOf(result);
    }

    private static List<Placement> managedProjectTrail(
            BlockPos origin,
            EconomyState.VillageRecord village,
            EconomyState.VillageProject project) {
        return projectTrail(
                managedProjectPalette(village, project), origin, village, project, true);
    }

    private static Palette managedProjectPalette(
            EconomyState.VillageRecord village,
            EconomyState.VillageProject project) {
        VillageArchitecture.Character character = VillageArchitecture.Character.fromId(
                village.architectureCharacter);
        VillageArchitecture.BiomeDialect dialect = VillageArchitecture.BiomeDialect.fromId(
                village.architectureDialect);
        Palette palette;
        if (isBlueprint(project)) {
            AuthoredVillageStructures.Materials materials = AuthoredVillageStructures.plan(
                    project.type,
                    project.designTemplateId,
                    project.designTemplateRevision,
                    project.designPaletteId,
                    project.designDressingId,
                    character,
                    dialect).materials();
            palette = new Palette(
                    materials.foundation(),
                    materials.wall(),
                    materials.timber(),
                    materials.roofSlab(),
                    materials.fence(),
                    materials.accent(),
                    materials.door(),
                    materials.entryStairs());
        } else {
            ModularVillageStructures.Materials materials =
                    ModularVillageStructures.materials(character, dialect);
            palette = new Palette(
                    materials.foundation(),
                    materials.wall(),
                    materials.timber(),
                    materials.roofSlab(),
                    materials.fence(),
                    materials.accent(),
                    materials.door(),
                    materials.entryStairs());
        }
        return palette;
    }

    /** Exact historical primary cells whose frozen selector emitted coarse dirt. */
    private static List<Placement> modularProjectTrailCenterSurfaceMigration(
            BlockPos origin,
            EconomyState.VillageRecord village,
            EconomyState.VillageProject project) {
        return managedProjectTrail(origin, village, project).stream()
                .filter(placement -> placement.role == PlacementRole.TRAIL_PRIMARY
                        && placement.state.is(Blocks.COARSE_DIRT))
                .map(placement -> Placement.trailCenterSurfaceRetrofit(
                        placement.dx, placement.dz))
                .toList();
    }

    private static List<Placement> toPlacements(
            List<ModularVillageStructures.Cell> cells) {
        return cells.stream()
                .map(cell -> new Placement(cell.x(), cell.y(), cell.z(), cell.state()))
                .toList();
    }

    private static List<Placement> toBlueprintPlacements(
            List<AuthoredVillageStructures.Cell> cells,
            PlacementRole role,
            Set<BlockPos> requiredSafetyFixtures) {
        List<Placement> placements = new ArrayList<>(cells.size());
        int firstDecor = cells.size();
        for (int index = 0; index < cells.size(); index++) {
            AuthoredVillageStructures.Cell cell = cells.get(index);
            if (cell.phase() == AuthoredVillageStructures.Phase.DECOR) {
                firstDecor = Math.min(firstDecor, index);
            }
            PlacementRole persistedRole = role == PlacementRole.STRUCTURE
                            && cell.phase() == AuthoredVillageStructures.Phase.DECOR
                    ? PlacementRole.COSMETIC
                    : role;
            BlockPos position = new BlockPos(cell.x(), cell.y(), cell.z());
            placements.add(new Placement(
                    cell.x(),
                    cell.y(),
                    cell.z(),
                    cell.state(),
                    persistedRole,
                    requiredSafetyFixtures.contains(position)));
        }
        // DECOR is the final authored phase and is otherwise already in stable placement order.
        // Put chain runs top-down before hanging lamps so the attachment exists when vanilla
        // evaluates canSurvive. Required safety fixtures retain their historical COSMETIC role in
        // the immutable v1 blueprint hash, but are never optional at runtime.
        placements.subList(firstDecor, placements.size()).sort(Comparator
                .comparingInt(VillageProsperityManager::cosmeticAttachmentPriority)
                .thenComparingInt(placement -> placement.state.is(Blocks.IRON_CHAIN)
                        ? -placement.dy
                        : 0));
        return List.copyOf(placements);
    }

    /**
     * Computes one immutable safety-authority set from every observable cumulative stage. A later
     * stage fixture may hang from an earlier cosmetic beam, so layer-local scans are insufficient:
     * the emitter, its attachment, every intervening chain, and the terminal authored support all
     * remain required together. Ordinary non-luminous decoration stays optional.
     *
     * <p>The bit is intentionally independent of PlacementRole. Saved Blueprint V2 hashes retain
     * their original v1 role bytes, so strengthening runtime safety does not rewrite plan identity
     * or any frozen geometry.</p>
     */
    private static Set<BlockPos> cumulativeRequiredSafetyFixturePositions(
            List<AuthoredVillageStructures.Cell> base,
            List<AuthoredVillageStructures.Cell> stageOne,
            List<AuthoredVillageStructures.Cell> stageTwo) {
        Map<BlockPos, AuthoredVillageStructures.Cell> authored = new HashMap<>();
        Set<BlockPos> required = new HashSet<>();
        for (List<AuthoredVillageStructures.Cell> layer : List.of(base, stageOne, stageTwo)) {
            for (AuthoredVillageStructures.Cell cell : layer) {
                authored.put(new BlockPos(cell.x(), cell.y(), cell.z()), cell);
            }
            required.addAll(requiredSafetyFixturePositions(authored));
        }
        return Set.copyOf(required);
    }

    private static Set<BlockPos> requiredSafetyFixturePositions(
            Map<BlockPos, AuthoredVillageStructures.Cell> authored) {
        Set<BlockPos> required = new HashSet<>();
        for (Map.Entry<BlockPos, AuthoredVillageStructures.Cell> entry : authored.entrySet()) {
            AuthoredVillageStructures.Cell cell = entry.getValue();
            BlockState state = cell.state();
            if (state.getLightEmission() <= 0) {
                continue;
            }
            BlockPos emitter = entry.getKey();
            required.add(emitter);

            BlockPos support;
            boolean followsHangingChain = false;
            if (state.getBlock() instanceof LanternBlock) {
                followsHangingChain = state.getValue(LanternBlock.HANGING);
                support = followsHangingChain ? emitter.above() : emitter.below();
            } else if (state.is(Blocks.WALL_TORCH) || state.is(Blocks.SOUL_WALL_TORCH)) {
                support = emitter.relative(
                        state.getValue(HorizontalDirectionalBlock.FACING).getOpposite());
            } else if (state.is(Blocks.TORCH) || state.is(Blocks.SOUL_TORCH)) {
                support = emitter.below();
            } else {
                continue;
            }

            while (true) {
                AuthoredVillageStructures.Cell supportCell = authored.get(support);
                if (supportCell == null) {
                    break;
                }
                required.add(support);
                if (!followsHangingChain || !supportCell.state().is(Blocks.IRON_CHAIN)) {
                    break;
                }
                support = support.above();
            }
        }
        return Set.copyOf(required);
    }

    private static int cosmeticAttachmentPriority(Placement placement) {
        if (placement.state.is(Blocks.IRON_CHAIN)) {
            return 0;
        }
        if (placement.state.getBlock() instanceof LanternBlock
                && placement.state.getValue(LanternBlock.HANGING)) {
            return 2;
        }
        return 1;
    }

    /**
     * Converts authored navigation semantics into persistent, non-writing runtime assertions.
     * Reserved aisles and workstation/entrance standing space must remain air for a structure to
     * become or remain operational, but the materializer never clears a player obstruction.
     */
    private static List<Placement> blueprintAccessClearances(
            AuthoredVillageStructures.Blueprint blueprint) {
        Set<BlockPos> occupied = new HashSet<>();
        for (AuthoredVillageStructures.Cell cell : blueprint.base()) {
            occupied.add(new BlockPos(cell.x(), cell.y(), cell.z()));
        }
        Set<BlockPos> clearance = new HashSet<>(blueprint.metadata().reservedAir());
        addStandingClearance(clearance, blueprint.metadata().entranceInside());
        for (BlockPos accessTarget : blueprint.metadata().accessTargets()) {
            addStandingClearance(clearance, accessTarget);
        }
        for (AuthoredVillageStructures.VerticalAccess access
                : blueprint.metadata().verticalAccess()) {
            // The ladder and hatch are authored STRUCTURE cells. Only their upper dismount and
            // headroom are air assertions; treating ladder coordinates as air would erase access.
            addStandingClearance(clearance, access.to().above(2));
        }
        return clearance.stream()
                .filter(position -> !occupied.contains(position))
                .sorted(Comparator.comparingInt((BlockPos position) -> position.getY())
                        .thenComparingInt(position -> position.getZ())
                        .thenComparingInt(position -> position.getX()))
                .map(position -> Placement.accessClearance(
                        position.getX(), position.getY(), position.getZ()))
                .toList();
    }

    private static void addStandingClearance(Set<BlockPos> clearance, BlockPos feet) {
        if (feet != null) {
            clearance.add(feet);
            clearance.add(feet.above());
        }
    }

    private static List<Placement> withFoundationSupports(
            List<Placement> previous,
            List<Placement> layer,
            Block foundation) {
        List<TerrainFoundationPlan.Cell> authored = new ArrayList<>(
                previous.size() + layer.size());
        Set<BlockPos> occupied = new HashSet<>();
        List<TerrainFoundationPlan.Cell> authoritative = new ArrayList<>();
        for (List<Placement> placements : List.of(previous, layer)) {
            for (Placement placement : placements) {
                if (placement.isTrail() || placement.isAccessClearance()) {
                    continue;
                }
                authored.add(new TerrainFoundationPlan.Cell(
                        placement.dx, placement.dy, placement.dz));
                occupied.add(new BlockPos(placement.dx, placement.dy, placement.dz));
                if (placement.isGroundAuthoritative()) {
                    authoritative.add(new TerrainFoundationPlan.Cell(
                            placement.dx, placement.dy, placement.dz));
                }
            }
        }
        Set<Long> authoritativeGroundContactColumns = TerrainFoundationPlan
                .groundContactColumns(authoritative)
                .stream()
                .map(column -> packXZ(column.x(), column.z()))
                .collect(java.util.stream.Collectors.toSet());
        List<Placement> supports = new ArrayList<>();
        for (TerrainFoundationPlan.Cell support
                : TerrainFoundationPlan.appendSupportCells(
                        authored, TerrainFoundationPlan.MAX_TERRAIN_DROP)) {
            BlockPos position = new BlockPos(support.x(), support.y(), support.z());
            if (occupied.add(position)) {
                boolean cosmeticColumn = !authoritativeGroundContactColumns.contains(
                        packXZ(support.x(), support.z()));
                supports.add(cosmeticColumn
                        ? Placement.cosmeticSupport(
                                support.x(),
                                support.y(),
                                support.z(),
                                foundation.defaultBlockState())
                        : Placement.support(
                                support.x(),
                                support.y(),
                                support.z(),
                                foundation.defaultBlockState()));
            }
        }
        // Modular projects are a new schema, so their persisted order can establish foundations
        // bottom-up before any wall or furnishing appears. This avoids visibly floating builds.
        supports.sort(Comparator.comparingInt(Placement::dy)
                .thenComparingInt(Placement::dz)
                .thenComparingInt(Placement::dx));
        List<Placement> result = new ArrayList<>(supports.size() + layer.size());
        result.addAll(supports);
        result.addAll(layer);
        return List.copyOf(result);
    }

    private static List<Placement> rotatePlacements(
            List<Placement> placements, StructureSize structure, int rotation) {
        if (Math.floorMod(rotation, 4) == 0) {
            return placements;
        }
        return placements.stream()
                .map(placement -> rotatePlacement(placement, structure, rotation))
                .toList();
    }

    private static List<Placement> mirrorPlacements(
            List<Placement> placements, StructureSize structure, boolean mirrored) {
        if (!mirrored) {
            return placements;
        }
        return placements.stream()
                .map(placement -> new Placement(
                        structure.width - 1 - placement.dx,
                        placement.dy,
                        placement.dz,
                        placement.state.mirror(Mirror.FRONT_BACK),
                        placement.role,
                        placement.requiredSafetyFixture))
                .toList();
    }

    private static ProgressionLayers progressionLayers(
            List<Placement> legacy,
            Palette palette,
            VillageProsperityEngine.ProjectType type,
            int variant) {
        StructureSize structure = size(type);
        Set<BlockPos> occupied = new HashSet<>();
        for (Placement placement : legacy) {
            occupied.add(new BlockPos(placement.dx, placement.dy, placement.dz));
        }

        List<Placement> variation = new ArrayList<>();
        appendStructureVariation(variation, occupied, palette, structure, type, variant);
        List<Placement> stageOne = new ArrayList<>();
        appendVisualStageOne(stageOne, occupied, palette, structure, type, variant);
        List<Placement> stageTwo = new ArrayList<>();
        appendVisualStageTwo(stageTwo, occupied, palette, structure, type, variant);

        List<TerrainFoundationPlan.Cell> authored = new ArrayList<>();
        for (Placement placement : legacy) {
            authored.add(new TerrainFoundationPlan.Cell(
                    placement.dx, placement.dy, placement.dz));
        }
        for (List<Placement> additions : List.of(variation, stageOne, stageTwo)) {
            for (Placement placement : additions) {
                authored.add(new TerrainFoundationPlan.Cell(
                        placement.dx, placement.dy, placement.dz));
            }
        }
        List<Placement> supports = TerrainFoundationPlan.appendSupportCells(
                        authored, TerrainFoundationPlan.MAX_TERRAIN_DROP)
                .stream()
                .map(cell -> Placement.support(
                        cell.x(), cell.y(), cell.z(), palette.floor.defaultBlockState()))
                .toList();
        return new ProgressionLayers(
                List.copyOf(supports),
                List.copyOf(variation),
                List.copyOf(stageOne),
                List.copyOf(stageTwo));
    }

    private static void appendStructureVariation(
            List<Placement> additions,
            Set<BlockPos> occupied,
            Palette palette,
            StructureSize structure,
            VillageProsperityEngine.ProjectType type,
            int variant) {
        appendEntrance(additions, occupied, palette, structure, type);
        if (type == VillageProsperityEngine.ProjectType.GUARD_POST) {
            // The immutable legacy prefix placed these two standing lanterns at y=2. Ground their
            // columns append-only so existing worlds upgrade safely instead of rewriting history.
            addOpen(additions, occupied, 1, 1, 1, palette.fence.defaultBlockState());
            addOpen(
                    additions,
                    occupied,
                    structure.width - 2,
                    1,
                    1,
                    palette.fence.defaultBlockState());
        }
        if (type == VillageProsperityEngine.ProjectType.MARKET_SQUARE) {
            // Legacy stalls placed their two posts at y=1 and their canopy at y=3. Continue all
            // eight posts to the awning as an append-only repair for existing worlds.
            for (int centerX : new int[] {4, 7}) {
                for (int centerZ : new int[] {1, 9}) {
                    for (int x : new int[] {centerX - 1, centerX + 1}) {
                        addOpen(
                                additions,
                                occupied,
                                x,
                                2,
                                centerZ,
                                palette.fence.defaultBlockState());
                    }
                }
            }
        }
        int left = 0;
        int right = structure.width - 1;
        if (variant == 0) {
            addLampColumn(additions, occupied, left, -1, palette.accent, palette.fence);
        } else if (variant == 1) {
            addLampColumn(additions, occupied, right, -1, palette.accent, palette.fence);
        } else {
            addPlanter(additions, occupied, left, -1, palette.accent, Blocks.POTTED_FERN);
            addPlanter(
                    additions,
                    occupied,
                    right,
                    -1,
                    palette.accent,
                    type == VillageProsperityEngine.ProjectType.MINE_ENTRANCE
                            ? Blocks.POTTED_CACTUS
                            : Blocks.POTTED_DANDELION);
        }
        Block detail = switch (variant) {
            case 0 -> Blocks.CHISELED_BOOKSHELF;
            case 1 -> Blocks.NOTE_BLOCK;
            default -> Blocks.DECORATED_POT;
        };
        addFirstOpenInterior(additions, occupied, structure, detail.defaultBlockState(), 0);
    }

    private static void appendEntrance(
            List<Placement> additions,
            Set<BlockPos> occupied,
            Palette palette,
            StructureSize structure,
            VillageProsperityEngine.ProjectType type) {
        int center = structure.width / 2;
        boolean openStructure = type == VillageProsperityEngine.ProjectType.MARKET_SQUARE
                || type == VillageProsperityEngine.ProjectType.MINE_ENTRANCE;
        if (!openStructure) {
            // Most enclosed templates already have this threshold in their immutable detail
            // prefix; GUARD_POST did not. addOpen makes the repair additive and idempotent.
            addOpen(
                    additions,
                    occupied,
                    center,
                    0,
                    -1,
                    palette.accent.defaultBlockState());
            BlockState lower = palette.door.defaultBlockState()
                    .setValue(DoorBlock.FACING, Direction.NORTH)
                    .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
            addOpen(additions, occupied, center, 1, 0, lower);
            addOpen(
                    additions,
                    occupied,
                    center,
                    2,
                    0,
                    lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        }
        int stairZ = openStructure ? -1 : -2;
        addOpen(
                additions,
                occupied,
                center,
                0,
                stairZ,
                palette.stairs.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH));
    }

    private static void appendVisualStageOne(
            List<Placement> additions,
            Set<BlockPos> occupied,
            Palette palette,
            StructureSize structure,
            VillageProsperityEngine.ProjectType type,
            int variant) {
        int[] lampCandidates = variant == 0
                ? new int[] {structure.width - 1, 1, structure.width - 2}
                : variant == 1
                        ? new int[] {0, structure.width - 2, 1}
                        : new int[] {1, structure.width - 2, structure.width / 2 - 1};
        for (int x : lampCandidates) {
            if (addLampColumn(
                    additions, occupied, x, -1, palette.accent, palette.fence)) {
                break;
            }
        }
        addFirstOpenInterior(
                additions, occupied, structure, Blocks.CHEST.defaultBlockState(), 1);
        addFirstOpenInterior(
                additions, occupied, structure, Blocks.BOOKSHELF.defaultBlockState(), 2);
    }

    private static void appendVisualStageTwo(
            List<Placement> additions,
            Set<BlockPos> occupied,
            Palette palette,
            StructureSize structure,
            VillageProsperityEngine.ProjectType type,
            int variant) {
        // A more formal entrance is the visible city-stage signature. These columns remain off
        // the central two-block-high doorway and use the same grounded suffix as the base plan.
        for (int x : new int[] {structure.width / 2 - 1, structure.width / 2 + 1}) {
            addLampColumn(additions, occupied, x, -1, palette.accent, palette.corner);
        }

        if (type == VillageProsperityEngine.ProjectType.COTTAGE
                || type == VillageProsperityEngine.ProjectType.HOUSE
                || type == VillageProsperityEngine.ProjectType.INN) {
            addFirstOpenBed(additions, occupied, structure);
        }
        addFirstOpenInterior(
                additions, occupied, structure, stageTwoUtility(type).defaultBlockState(), 3);
        addFirstOpenInterior(
                additions, occupied, structure, Blocks.CHEST.defaultBlockState(), 4);
    }

    private static Block stageTwoUtility(VillageProsperityEngine.ProjectType type) {
        return switch (type) {
            case COTTAGE, HOUSE, INN -> Blocks.LOOM;
            case WAREHOUSE -> Blocks.CRAFTING_TABLE;
            case MINE_ENTRANCE -> Blocks.BLAST_FURNACE;
            case MARKET_SQUARE -> Blocks.FLETCHING_TABLE;
            case SMITHY -> Blocks.FURNACE;
            case GRANARY -> Blocks.COMPOSTER;
            case GUARD_POST -> Blocks.GRINDSTONE;
            case EXCHANGE_HALL -> Blocks.ENDER_CHEST;
        };
    }

    private static boolean addLampColumn(
            List<Placement> additions,
            Set<BlockPos> occupied,
            int x,
            int z,
            Block base,
            Block post) {
        if (!positionsOpen(occupied, new BlockPos(x, 0, z), new BlockPos(x, 1, z),
                new BlockPos(x, 2, z))) {
            return false;
        }
        addOpen(additions, occupied, x, 0, z, base.defaultBlockState());
        addOpen(additions, occupied, x, 1, z, post.defaultBlockState());
        addOpen(additions, occupied, x, 2, z, Blocks.LANTERN.defaultBlockState());
        return true;
    }

    private static boolean addPlanter(
            List<Placement> additions,
            Set<BlockPos> occupied,
            int x,
            int z,
            Block base,
            Block plant) {
        if (!positionsOpen(occupied, new BlockPos(x, 0, z), new BlockPos(x, 1, z))) {
            return false;
        }
        addOpen(additions, occupied, x, 0, z, base.defaultBlockState());
        addOpen(additions, occupied, x, 1, z, plant.defaultBlockState());
        return true;
    }

    private static boolean positionsOpen(Set<BlockPos> occupied, BlockPos... positions) {
        for (BlockPos position : positions) {
            if (occupied.contains(position)) {
                return false;
            }
        }
        return true;
    }

    private static void addOpen(
            List<Placement> additions,
            Set<BlockPos> occupied,
            int x,
            int y,
            int z,
            BlockState state) {
        BlockPos position = new BlockPos(x, y, z);
        if (occupied.add(position)) {
            additions.add(new Placement(x, y, z, state));
        }
    }

    private static boolean addFirstOpenInterior(
            List<Placement> additions,
            Set<BlockPos> occupied,
            StructureSize structure,
            BlockState state,
            int rotation) {
        List<BlockPos> candidates = new ArrayList<>();
        for (int z = 1; z < structure.depth - 1; z++) {
            for (int x = 1; x < structure.width - 1; x++) {
                candidates.add(new BlockPos(x, 1, z));
            }
        }
        if (candidates.isEmpty()) {
            return false;
        }
        int start = Math.floorMod(rotation * 17, candidates.size());
        for (int index = 0; index < candidates.size(); index++) {
            BlockPos candidate = candidates.get((start + index) % candidates.size());
            // Preserve the clear aisle immediately behind every north-facing entrance.
            if (candidate.getX() == structure.width / 2 && candidate.getZ() == 1) {
                continue;
            }
            if (!occupied.contains(candidate)) {
                addOpen(
                        additions,
                        occupied,
                        candidate.getX(),
                        candidate.getY(),
                        candidate.getZ(),
                        state);
                return true;
            }
        }
        return false;
    }

    private static boolean addFirstOpenBed(
            List<Placement> additions,
            Set<BlockPos> occupied,
            StructureSize structure) {
        BlockState foot = Blocks.BED.white().defaultBlockState()
                .setValue(BedBlock.FACING, Direction.SOUTH);
        BlockState head = foot.setValue(BedBlock.PART, BedPart.HEAD);
        for (int z = 2; z < structure.depth - 2; z++) {
            for (int x = 1; x < structure.width - 1; x++) {
                BlockPos footPosition = new BlockPos(x, 1, z);
                BlockPos headPosition = new BlockPos(x, 1, z + 1);
                if (positionsOpen(occupied, footPosition, headPosition)) {
                    addOpen(additions, occupied, x, 1, z, foot);
                    addOpen(additions, occupied, x, 1, z + 1, head);
                    return true;
                }
            }
        }
        return false;
    }

    private static List<Placement> projectTrail(
            Palette palette,
            BlockPos origin,
            EconomyState.VillageRecord village,
            EconomyState.VillageProject project,
            boolean includeModularRetrofits) {
        StructureSize structure = projectSize(project);
        int rotation = isManagedProject(project) ? project.designRotation : 0;
        int startX = structure.width / 2;
        int startZ = trailEntranceZ(project);
        BlockPos anchor = project.trailAnchorSet
                ? BlockPos.of(project.trailAnchorPos)
                : trailAnchor(origin, village, project, structure);
        BlockPos localTarget = inverseRotateRelative(
                anchor.getX() - origin.getX(),
                0,
                anchor.getZ() - origin.getZ(),
                structure,
                rotation);
        int targetX = localTarget.getX();
        int targetZ = localTarget.getZ();
        long seed = VillageStructureProgression.mix64(project.projectId
                ^ village.villageId.getMostSignificantBits()
                ^ Long.rotateLeft(village.villageId.getLeastSignificantBits(), 17));

        List<VillageStructureProgression.TrailCell> primary = new ArrayList<>();
        List<VillageStructureProgression.TrailCell> shoulders = new ArrayList<>();
        List<VillageStructureProgression.TrailCell> widenedShoulders = new ArrayList<>();
        if (targetZ <= startZ) {
            appendTrailSegment(
                    primary,
                    shoulders,
                    widenedShoulders,
                    startX,
                    startZ,
                    targetX,
                    targetZ,
                    seed);
        } else if (targetZ >= structure.depth + 1) {
            int sideX = trailSide(structure, targetX, seed);
            appendTrailSegment(
                    primary,
                    shoulders,
                    widenedShoulders,
                    startX,
                    startZ,
                    sideX,
                    startZ,
                    seed);
            appendTrailSegment(
                    primary,
                    shoulders,
                    widenedShoulders,
                    sideX,
                    startZ,
                    sideX,
                    structure.depth + 2,
                    seed + 1L);
            appendTrailSegment(
                    primary,
                    shoulders,
                    widenedShoulders,
                    sideX,
                    structure.depth + 2,
                    targetX,
                    targetZ,
                    seed + 2L);
        } else {
            int sideX = targetX < 0 ? -3 : structure.width + 2;
            appendTrailSegment(
                    primary,
                    shoulders,
                    widenedShoulders,
                    startX,
                    startZ,
                    sideX,
                    startZ,
                    seed);
            appendTrailSegment(
                    primary,
                    shoulders,
                    widenedShoulders,
                    sideX,
                    startZ,
                    sideX,
                    targetZ,
                    seed + 1L);
            appendTrailSegment(
                    primary,
                    shoulders,
                    widenedShoulders,
                    sideX,
                    targetZ,
                    targetX,
                    targetZ,
                    seed + 2L);
        }
        if (primary.isEmpty() || primary.size() > 192) {
            return List.of();
        }

        Set<Long> primaryCells = new HashSet<>();
        List<Placement> result = new ArrayList<>(
                primary.size() * 2 + shoulders.size() + widenedShoulders.size());
        for (VillageStructureProgression.TrailCell cell : primary) {
            if (insideStructureEnvelope(cell.x(), cell.z(), structure)
                    || !primaryCells.add(packXZ(cell.x(), cell.z()))) {
                continue;
            }
            BlockPos rotated = rotateRelative(cell.x(), 0, cell.z(), structure, rotation);
            result.add(Placement.trail(
                    rotated.getX(),
                    rotated.getZ(),
                    trailState(
                            palette,
                            seed,
                            rotated.getX(),
                            rotated.getZ(),
                            false,
                            isManagedProject(project)
                                    && project.trailCenterSurfaceVersion
                                            >= EconomyState.TRAIL_CENTER_SURFACE_VERSION),
                    false));
        }
        Set<Long> emitted = new HashSet<>(primaryCells);
        for (VillageStructureProgression.TrailCell cell : shoulders) {
            long packed = packXZ(cell.x(), cell.z());
            if (!insideStructureEnvelope(cell.x(), cell.z(), structure) && emitted.add(packed)) {
                BlockPos rotated = rotateRelative(cell.x(), 0, cell.z(), structure, rotation);
                result.add(Placement.trail(
                        rotated.getX(),
                        rotated.getZ(),
                        trailState(
                                palette,
                                seed,
                                rotated.getX(),
                                rotated.getZ(),
                                true,
                                isManagedProject(project)
                                        && project.trailCenterSurfaceVersion
                                                >= EconomyState.TRAIL_CENTER_SURFACE_VERSION),
                        true));
            }
        }
        if (!includeModularRetrofits) {
            return List.copyOf(result);
        }
        // The primary lane and legacy sparse shoulders above are a frozen compatibility prefix.
        // Append both complete side lanes afterward, excluding the building envelope, so an old
        // persisted cursor can safely grow into the wider road without rerolling earlier cells.
        for (VillageStructureProgression.TrailCell cell : widenedShoulders) {
            long packed = packXZ(cell.x(), cell.z());
            if (!insideStructureEnvelope(cell.x(), cell.z(), structure) && emitted.add(packed)) {
                BlockPos rotated = rotateRelative(cell.x(), 0, cell.z(), structure, rotation);
                result.add(Placement.trail(
                        rotated.getX(),
                        rotated.getZ(),
                        trailState(
                                palette,
                                seed,
                                rotated.getX(),
                                rotated.getZ(),
                                true,
                                isManagedProject(project)
                                        && project.trailCenterSurfaceVersion
                                                >= EconomyState.TRAIL_CENTER_SURFACE_VERSION),
                        true));
            }
        }
        return List.copyOf(result);
    }

    private static BlockPos trailAnchor(
            BlockPos origin,
            EconomyState.VillageRecord village,
            EconomyState.VillageProject project,
            StructureSize structure) {
        BlockPos reference;
        if (isManagedProject(project)) {
            BlockPos centerOffset = rotateRelative(
                    structure.width / 2,
                    0,
                    structure.depth / 2,
                    structure,
                    project.designRotation);
            reference = origin.offset(
                    centerOffset.getX(), centerOffset.getY(), centerOffset.getZ());
        } else {
            BlockPos entranceOffset = rotateRelative(
                    structure.width / 2,
                    0,
                    trailEntranceZ(project),
                    structure,
                    0);
            reference = origin.offset(
                    entranceOffset.getX(), entranceOffset.getY(), entranceOffset.getZ());
        }
        return trailAnchor(
                reference.getX(), reference.getY(), reference.getZ(), village, project);
    }

    private static BlockPos trailAnchor(
            int referenceX,
            int referenceY,
            int referenceZ,
            EconomyState.VillageRecord village,
            EconomyState.VillageProject project) {
        BlockPos reference = new BlockPos(referenceX, referenceY, referenceZ);
        EconomyState.VillageProject branch = village.projects.stream()
                .filter(candidate -> candidate != null
                        && candidate != project
                        && candidate.projectId < project.projectId
                        && candidate.originPos != 0L
                        && !candidate.abstractOnly
                        && candidate.economicComplete
                        && candidate.materializedComplete
                        && !candidate.blocked
                        && !candidate.manualRepairRequired
                        && !candidate.relocationPending)
                .min(Comparator.<EconomyState.VillageProject>comparingLong(candidate -> {
                    BlockPos candidateOrigin = BlockPos.of(candidate.originPos);
                    BlockPos entrance = projectEntrance(candidateOrigin, candidate);
                    long dx = (long) entrance.getX() - reference.getX();
                    long dz = (long) entrance.getZ() - reference.getZ();
                    return dx * dx + dz * dz;
                }).thenComparingLong(candidate -> candidate.projectId))
                .orElse(null);
        if (branch != null) {
            BlockPos branchOrigin = BlockPos.of(branch.originPos);
            return projectEntrance(branchOrigin, branch);
        }

        BlockPos center = BlockPos.of(village.centerPos);
        long dx = (long) reference.getX() - center.getX();
        long dz = (long) reference.getZ() - center.getZ();
        double length = Math.max(1.0, StrictMath.sqrt((double) dx * dx + (double) dz * dz));
        // The first connector joins a stable outskirts hub rather than cutting through the bell
        // square. Later structures branch from the nearest completed connector endpoint.
        return new BlockPos(
                center.getX() + (int) StrictMath.round(dx / length * 14.0),
                center.getY(),
                center.getZ() + (int) StrictMath.round(dz / length * 14.0));
    }

    private static int trailEntranceZ(EconomyState.VillageProject project) {
        if (isManagedProject(project)) {
            return -2;
        }
        return project.type == VillageProsperityEngine.ProjectType.MARKET_SQUARE
                        || project.type == VillageProsperityEngine.ProjectType.MINE_ENTRANCE
                ? -2
                : -3;
    }

    private static BlockPos projectEntrance(
            BlockPos origin, EconomyState.VillageProject project) {
        StructureSize structure = projectSize(project);
        BlockPos offset = rotateRelative(
                structure.width / 2,
                0,
                trailEntranceZ(project),
                structure,
                isManagedProject(project) ? project.designRotation : 0);
        return origin.offset(offset.getX(), offset.getY(), offset.getZ());
    }

    private static int trailSide(StructureSize structure, int targetX, long seed) {
        int left = -3;
        int right = structure.width + 2;
        int leftDistance = Math.abs(targetX - left);
        int rightDistance = Math.abs(targetX - right);
        if (leftDistance != rightDistance) {
            return leftDistance < rightDistance ? left : right;
        }
        return (seed & 1L) == 0L ? left : right;
    }

    private static void appendTrailSegment(
            List<VillageStructureProgression.TrailCell> primary,
            List<VillageStructureProgression.TrailCell> shoulders,
            List<VillageStructureProgression.TrailCell> widenedShoulders,
            int startX,
            int startZ,
            int targetX,
            int targetZ,
            long seed) {
        List<VillageStructureProgression.TrailCell> segment = VillageStructureProgression.trail(
                startX, startZ, targetX, targetZ, seed, 192);
        widenedShoulders.addAll(VillageStructureProgression.threeWideShoulders(segment));
        for (VillageStructureProgression.TrailCell cell : segment) {
            if (cell.shoulder()) {
                shoulders.add(cell);
            } else if (primary.isEmpty()
                    || primary.get(primary.size() - 1).x() != cell.x()
                    || primary.get(primary.size() - 1).z() != cell.z()) {
                primary.add(cell);
            }
        }
    }

    private static boolean insideStructureEnvelope(int x, int z, StructureSize structure) {
        return x >= -1 && x <= structure.width && z >= -1 && z <= structure.depth;
    }

    private static BlockState trailState(
            Palette palette,
            long seed,
            int x,
            int z,
            boolean shoulder,
            boolean useCurrentCenterSurface) {
        long detail = VillageStructureProgression.trailDetail(seed, x, z);
        boolean desertPalette = isDesertTrailPalette(palette);
        VillageMaterializationPolicy.TrailSurface surface = useCurrentCenterSurface
                ? VillageMaterializationPolicy.plannedTrailSurface(
                        desertPalette, shoulder, detail)
                : VillageMaterializationPolicy.frozenTrailSurface(
                        desertPalette, shoulder, detail);
        return switch (surface) {
            case DIRT_PATH -> Blocks.DIRT_PATH.defaultBlockState();
            case GRAVEL -> Blocks.GRAVEL.defaultBlockState();
            case COARSE_DIRT -> Blocks.COARSE_DIRT.defaultBlockState();
        };
    }

    private static boolean isDesertTrailPalette(Palette palette) {
        return palette.floor == Blocks.SMOOTH_SANDSTONE
                || palette.floor == Blocks.SANDSTONE
                || palette.wall == Blocks.CUT_SANDSTONE;
    }

    private static long packXZ(int x, int z) {
        return ((long) x << 32) ^ Integer.toUnsignedLong(z);
    }

    private static List<Placement> template(
            ServerLevel level, BlockPos origin, VillageProsperityEngine.ProjectType type) {
        Palette palette = palette(level, origin);
        return switch (type) {
            case COTTAGE -> cottage(palette);
            case HOUSE -> house(palette);
            case INN -> inn(palette);
            case WAREHOUSE -> warehouse(palette);
            case MINE_ENTRANCE -> mineEntrance(palette);
            case MARKET_SQUARE -> marketSquare(palette);
            case SMITHY -> smithy(palette);
            case GRANARY -> granary(palette);
            case GUARD_POST -> guardPost(palette);
            case EXCHANGE_HALL -> exchangeHall(palette);
        };
    }

    /** Validates every authored template during the live server smoke test. */
    static void validateProjectTemplates(ServerLevel level) {
        long validationStarted = System.nanoTime();
        BlockPos origin = new BlockPos(0, 64, 0);
        for (VillageProsperityEngine.ProjectType type
                : VillageProsperityEngine.ProjectType.values()) {
            List<Placement> placements = template(level, origin, type);
            if (placements.isEmpty() || placements.size() > 600) {
                throw new IllegalStateException(
                        "Invalid physical template size for " + type + ": " + placements.size());
            }
            Set<BlockPos> occupied = new HashSet<>();
            Map<BlockPos, BlockState> authored = new HashMap<>();
            StructureSize declaredSize = size(type);
            int lights = 0;
            int beds = 0;
            int utilityBlocks = 0;
            int exchangeDesks = 0;
            for (Placement placement : placements) {
                BlockPos relative = new BlockPos(placement.dx, placement.dy, placement.dz);
                if (!occupied.add(relative)) {
                    throw new IllegalStateException(
                            "Duplicate physical placement for " + type + " at " + relative);
                }
                authored.put(relative, placement.state);
                if (relative.getX() < -1
                        || relative.getX() > declaredSize.width
                        || relative.getY() < 0
                        || relative.getY() > declaredSize.height
                        || relative.getZ() < -1
                        || relative.getZ() > declaredSize.depth) {
                    throw new IllegalStateException(
                            "Physical template " + type
                                    + " escaped its preflight volume at " + relative);
                }
                if (placement.state.is(Blocks.BARREL)
                        || placement.state.is(Blocks.LECTERN)
                        || placement.state.is(Blocks.CARTOGRAPHY_TABLE)
                        || placement.state.is(Blocks.EMERALD_BLOCK)
                        || placement.state.is(Blocks.DIAMOND_BLOCK)
                        || placement.state.is(Blocks.GOLD_BLOCK)
                        || placement.state.is(Blocks.NETHERITE_BLOCK)) {
                    throw new IllegalStateException(
                            "Prosperity template " + type
                                    + " contains an unintended workstation or currency block at "
                                    + relative);
                }
                if (placement.state.is(Blocks.LANTERN)) {
                    lights++;
                }
                if (placement.state.is(BlockTags.BEDS)) {
                    beds++;
                }
                if (isUsefulProjectBlock(placement.state)) {
                    utilityBlocks++;
                }
                if (BankerProfessionSupport.isExchangeDesk(placement.state)) {
                    exchangeDesks++;
                }
            }
            if (lights < 2 || utilityBlocks < 1) {
                throw new IllegalStateException(
                        "Physical template " + type + " lost its lighting or functional interior");
            }
            if ((type == VillageProsperityEngine.ProjectType.COTTAGE
                            || type == VillageProsperityEngine.ProjectType.HOUSE
                            || type == VillageProsperityEngine.ProjectType.INN)
                    && beds < 2) {
                throw new IllegalStateException(
                        "Residential template " + type + " no longer provides housing");
            }
            if (type == VillageProsperityEngine.ProjectType.EXCHANGE_HALL
                    && exchangeDesks != 1) {
                throw new IllegalStateException(
                        "Exchange Hall must contain exactly one Exchange Desk");
            }
            validateProjectRoof(type, palette(level, origin), authored);
            if ((type == VillageProsperityEngine.ProjectType.COTTAGE
                            || type == VillageProsperityEngine.ProjectType.HOUSE
                            || type == VillageProsperityEngine.ProjectType.INN)
                    && (authored.containsKey(new BlockPos(declaredSize.width / 2, 1, 1))
                            || authored.containsKey(
                                    new BlockPos(declaredSize.width / 2, 2, 1)))) {
                throw new IllegalStateException(
                        "Residential template " + type + " obstructs its only entrance path");
            }
            validateProgressionLayers(type, palette(level, origin), placements);
        }
        long legacyFinished = System.nanoTime();
        AuthoredVillageStructures.validateCatalog();
        long authoredFinished = System.nanoTime();
        validateModularProjectTemplates();
        long modularFinished = System.nanoTime();
        validateModularEntranceApproachRecipes();
        long approachesFinished = System.nanoTime();
        LOGGER.info(
                "Project-template admission passed in {} ms (legacy={} ms, authored={} ms, modular={} ms, approaches={} ms)",
                elapsedMillis(validationStarted, approachesFinished),
                elapsedMillis(validationStarted, legacyFinished),
                elapsedMillis(legacyFinished, authoredFinished),
                elapsedMillis(authoredFinished, modularFinished),
                elapsedMillis(modularFinished, approachesFinished));
    }

    private static long elapsedMillis(long started, long finished) {
        return Math.max(0L, (finished - started) / 1_000_000L);
    }

    /** Exercises every material family, project footprint, rotation, and supported stair depth. */
    private static void validateModularEntranceApproachRecipes() {
        BlockPos origin = new BlockPos(0, 64, 0);
        UUID villageId = UUID.fromString("98ad11cc-8e15-4e2a-a3d3-7f07874605a1");
        for (VillageArchitecture.BiomeDialect dialect
                : VillageArchitecture.BiomeDialect.values()) {
            for (VillageArchitecture.Character character
                    : VillageArchitecture.Character.values()) {
                EconomyState.VillageRecord village = new EconomyState.VillageRecord();
                village.villageId = villageId;
                village.architectureDialect = dialect.id();
                village.architectureCharacter = character.id();
                for (VillageProsperityEngine.ProjectType type
                        : VillageProsperityEngine.ProjectType.values()) {
                    StructureSize structure = modularSize(type);
                    for (int rotation = 0; rotation < 4; rotation++) {
                        EconomyState.VillageProject project = new EconomyState.VillageProject();
                        project.projectId = 1L + type.ordinal() * 4L + rotation;
                        project.type = type;
                        project.designSchema = VillageArchitecture.MODULAR_SCHEMA;
                        project.designRotation = rotation;
                        project.trailAnchorSet = true;
                        BlockPos rotatedAnchor = rotateRelative(
                                structure.width / 2,
                                0,
                                -10,
                                structure,
                                rotation);
                        project.trailAnchorPos = origin.offset(rotatedAnchor).asLong();

                        List<Placement> route = modularPrimaryTrailPrefix(
                                origin,
                                village,
                                project,
                                TerrainFoundationPlan.MAX_TERRAIN_DROP + 1);
                        if (route.size() <= TerrainFoundationPlan.MAX_TERRAIN_DROP) {
                            throw new IllegalStateException(
                                    "Modular entrance route is too short for "
                                            + dialect + " " + character + " " + type
                                            + " rotation " + rotation);
                        }
                        for (int stepCount = 0;
                                stepCount <= TerrainFoundationPlan.MAX_TERRAIN_DROP;
                                stepCount++) {
                            List<Placement> approach = modularEntranceApproachPlacements(
                                    origin, village, project, stepCount);
                            List<Placement> repeated = modularEntranceApproachPlacements(
                                    origin, village, project, stepCount);
                            if (!approach.equals(repeated)
                                    || approach.size() > EconomyState.MAX_ENTRANCE_APPROACH_CELLS) {
                                throw new IllegalStateException(
                                        "Modular entrance approach is unstable or unbounded for "
                                                + type + " rotation " + rotation);
                            }
                            validateModularEntranceApproach(
                                    approach, route, structure, rotation, stepCount);
                        }
                    }
                }
            }
        }
    }

    private static void validateModularEntranceApproach(
            List<Placement> approach,
            List<Placement> route,
            StructureSize structure,
            int rotation,
            int stepCount) {
        Set<BlockPos> occupied = new HashSet<>();
        Map<Long, Placement> stairsByColumn = new HashMap<>();
        int stairCount = 0;
        int clearanceCount = 0;
        for (Placement placement : approach) {
            BlockPos position = new BlockPos(placement.dx, placement.dy, placement.dz);
            if (!occupied.add(position)) {
                throw new IllegalStateException(
                        "Duplicate modular entrance placement at " + position);
            }
            BlockPos local = inverseRotateRelative(
                    placement.dx, placement.dy, placement.dz, structure, rotation);
            if (insideStructureEnvelope(local.getX(), local.getZ(), structure)) {
                throw new IllegalStateException(
                        "Modular entrance approach entered its building envelope at " + position);
            }
            if (placement.role == PlacementRole.ENTRANCE_STAIR) {
                stairsByColumn.put(packXZ(placement.dx, placement.dz), placement);
                stairCount++;
            } else if (placement.role == PlacementRole.ENTRANCE_CLEARANCE) {
                clearanceCount++;
            } else if (placement.role != PlacementRole.ENTRANCE_SUPPORT) {
                throw new IllegalStateException(
                        "Unexpected modular entrance role " + placement.role);
            }
        }
        if (stairCount != stepCount || clearanceCount != stepCount * 2) {
            throw new IllegalStateException(
                    "Modular entrance approach lost stairs or headroom at depth " + stepCount);
        }

        BlockPos previous = rotateRelative(
                structure.width / 2, 0, -1, structure, rotation);
        for (int index = 0; index < stepCount; index++) {
            Placement routeCell = route.get(index);
            Placement stair = stairsByColumn.get(packXZ(routeCell.dx, routeCell.dz));
            Direction expectedFacing = horizontalDirection(
                    previous.getX() - routeCell.dx,
                    previous.getZ() - routeCell.dz);
            if (stair == null
                    || stair.dy != -index - 1
                    || expectedFacing == null
                    || !stair.state.hasProperty(StairBlock.FACING)
                    || stair.state.getValue(StairBlock.FACING) != expectedFacing) {
                throw new IllegalStateException(
                        "Modular entrance stair lost route continuity at step " + index);
            }
            BlockState oppositeFacing = stair.state.setValue(
                    StairBlock.FACING, expectedFacing.getOpposite());
            if (!structuralStateMatches(stair.state, stair.state)
                    || structuralStateMatches(oppositeFacing, stair.state)) {
                throw new IllegalStateException(
                        "Modular entrance post-write verification lost stair facing at step "
                                + index);
            }
            previous = new BlockPos(stair.dx, stair.dy, stair.dz);
        }
        for (Placement placement : approach) {
            if (placement.role != PlacementRole.ENTRANCE_SUPPORT
                    && placement.role != PlacementRole.ENTRANCE_CLEARANCE) {
                continue;
            }
            Placement stair = stairsByColumn.get(packXZ(placement.dx, placement.dz));
            boolean valid = stair != null
                    && (placement.role == PlacementRole.ENTRANCE_SUPPORT
                            ? placement.dy < stair.dy
                            : placement.dy == stair.dy + 1 || placement.dy == stair.dy + 2);
            if (!valid) {
                throw new IllegalStateException(
                        "Modular entrance support/headroom detached at "
                                + new BlockPos(placement.dx, placement.dy, placement.dz));
            }
        }
    }

    private static void validateModularProjectTemplates() {
        for (VillageArchitecture.BiomeDialect dialect
                : VillageArchitecture.BiomeDialect.values()) {
            for (VillageProsperityEngine.ProjectType type
                    : VillageProsperityEngine.ProjectType.values()) {
                StructureSize declared = modularSize(type);
                Set<ModularPositionSignature> silhouetteSignatures = new HashSet<>();
                for (VillageArchitecture.Character character
                        : VillageArchitecture.Character.values()) {
                    Set<ModularCellSignature> baseRecipeSignatures = new HashSet<>();
                    Set<ModularLayerSignature> completeRecipeSignatures = new HashSet<>();
                    Set<ModularCellSignature> exteriorFamilySignatures = new HashSet<>();
                    for (int silhouette = 0;
                            silhouette < VillageArchitecture.SILHOUETTE_COUNT;
                            silhouette++) {
                        for (int roof = 0; roof < VillageArchitecture.ROOF_COUNT; roof++) {
                            for (int frontage = 0;
                                    frontage < VillageArchitecture.FRONTAGE_COUNT;
                                    frontage++) {
                                for (boolean mirrored : new boolean[] {false, true}) {
                                    VillageArchitecture.Recipe recipe =
                                            new VillageArchitecture.Recipe(
                                                    1L,
                                                    silhouette,
                                                    roof,
                                                    frontage,
                                                    mirrored,
                                                    VillageArchitecture.signature(
                                                            type,
                                                            silhouette,
                                                            roof,
                                                            frontage,
                                                            mirrored));
                                    ModularVillageStructures.Layers layers =
                                            ModularVillageStructures.plan(
                                                    type,
                                                    recipe,
                                                    character,
                                                    dialect,
                                                    declared.width,
                                                    declared.depth,
                                                    declared.height);
                                    if (VillageArchitecture.hasQualityRetrofit(type)) {
                                        ModularVillageStructures.Layers repeated =
                                                ModularVillageStructures.plan(
                                                        type,
                                                        recipe,
                                                        character,
                                                        dialect,
                                                        declared.width,
                                                        declared.depth,
                                                        declared.height);
                                        if (!layers.quality().equals(repeated.quality())) {
                                            throw new IllegalStateException(
                                                    "Modular quality layer rerolled for "
                                                            + dialect
                                                            + " "
                                                            + type
                                                            + " recipe "
                                                            + recipe.signature());
                                        }
                                    }
                                    validateModularLayers(
                                            type, dialect, recipe, layers, declared);
                                    validateModularQualityPrefix(type, layers);
                                    baseRecipeSignatures.add(
                                            modularCellSignature(layers.base()));
                                    completeRecipeSignatures.add(
                                            modularLayerSignature(layers));
                                    if (!mirrored) {
                                        exteriorFamilySignatures.add(
                                                modularExteriorSignature(layers.base(), declared));
                                    }
                                    if (character == VillageArchitecture.Character.FORMAL
                                            && roof == 0
                                            && frontage == 0
                                            && !mirrored) {
                                        silhouetteSignatures.add(
                                                modularPositionSignature(layers.base()));
                                    }
                                }
                            }
                        }
                    }
                    int expectedRecipes = VillageArchitecture.SILHOUETTE_COUNT
                            * VillageArchitecture.ROOF_COUNT
                            * VillageArchitecture.FRONTAGE_COUNT
                            * 2;
                    if (baseRecipeSignatures.size() != expectedRecipes
                            || completeRecipeSignatures.size() != expectedRecipes) {
                        throw new IllegalStateException(
                                "Modular recipe families collapsed to clones for "
                                        + dialect
                                        + " "
                                        + character
                                        + " "
                                        + type
                                        + ": base="
                                        + baseRecipeSignatures.size()
                                        + ", complete="
                                        + completeRecipeSignatures.size()
                                        + ", expected="
                                        + expectedRecipes);
                    }
                    int expectedExteriorFamilies = VillageArchitecture.SILHOUETTE_COUNT
                            * VillageArchitecture.ROOF_COUNT
                            * VillageArchitecture.FRONTAGE_COUNT;
                    if (exteriorFamilySignatures.size() != expectedExteriorFamilies) {
                        throw new IllegalStateException(
                                "Modular exterior families collapsed without mirror markers for "
                                        + dialect
                                        + " "
                                        + character
                                        + " "
                                        + type
                                        + ": exterior="
                                        + exteriorFamilySignatures.size()
                                        + ", expected="
                                        + expectedExteriorFamilies);
                    }
                }
                if (silhouetteSignatures.size() != VillageArchitecture.SILHOUETTE_COUNT) {
                    throw new IllegalStateException(
                            "Modular silhouettes collapsed to clones for " + dialect + " " + type);
                }
            }
            validateTradeBuildingExteriorIdentities(dialect);
        }
    }

    private static void validateTradeBuildingExteriorIdentities(
            VillageArchitecture.BiomeDialect dialect) {
        for (VillageArchitecture.Character character : VillageArchitecture.Character.values()) {
            Set<ModularCellSignature> signatures = new HashSet<>();
            for (VillageProsperityEngine.ProjectType type : List.of(
                    VillageProsperityEngine.ProjectType.WAREHOUSE,
                    VillageProsperityEngine.ProjectType.SMITHY,
                    VillageProsperityEngine.ProjectType.GRANARY)) {
                StructureSize declared = modularSize(type);
                VillageArchitecture.Recipe recipe = new VillageArchitecture.Recipe(
                        1L,
                        0,
                        0,
                        0,
                        false,
                        VillageArchitecture.signature(type, 0, 0, 0, false));
                ModularVillageStructures.Layers layers = ModularVillageStructures.plan(
                        type,
                        recipe,
                        character,
                        dialect,
                        declared.width,
                        declared.depth,
                        declared.height);
                signatures.add(modularExteriorSignature(layers.base(), declared));
            }
            if (signatures.size() != 3) {
                throw new IllegalStateException(
                        "Warehouse, Smithy, and Granary exterior identities collapsed for "
                                + dialect
                                + " "
                                + character);
            }
        }
    }

    private static ModularLayerSignature modularLayerSignature(
            ModularVillageStructures.Layers layers) {
        return new ModularLayerSignature(
                modularCellSignature(layers.base()),
                modularCellSignature(layers.stageOne()),
                modularCellSignature(layers.stageTwo()),
                modularCellSignature(layers.quality()));
    }

    private static ModularCellSignature modularCellSignature(
            List<ModularVillageStructures.Cell> cells) {
        ArrayList<ModularVillageStructures.Cell> canonical = new ArrayList<>(cells);
        canonical.sort(MODULAR_CELL_ORDER);
        return new ModularCellSignature(List.copyOf(canonical));
    }

    private static ModularPositionSignature modularPositionSignature(
            List<ModularVillageStructures.Cell> cells) {
        ArrayList<ModularPosition> canonical = new ArrayList<>(cells.size());
        for (ModularVillageStructures.Cell cell : cells) {
            canonical.add(new ModularPosition(cell.x(), cell.y(), cell.z()));
        }
        canonical.sort(MODULAR_POSITION_ORDER);
        return new ModularPositionSignature(List.copyOf(canonical));
    }

    private static ModularCellSignature modularExteriorSignature(
            List<ModularVillageStructures.Cell> cells, StructureSize declared) {
        List<ModularVillageStructures.Cell> exterior = cells.stream()
                .filter(cell -> cell.y() > 0)
                .filter(cell -> cell.y() >= 3
                        || cell.x() <= 1
                        || cell.x() >= declared.width - 2
                        || cell.z() <= 1
                        || cell.z() >= declared.depth - 2)
                .filter(cell -> !(cell.z() == 1
                        && (cell.x() == 0 || cell.x() == declared.width - 1)
                        && cell.y() <= 2))
                .filter(cell -> !cell.state().is(Blocks.LANTERN)
                        && !cell.state().is(BlockTags.FLOWER_POTS)
                        && !cell.state().is(BlockTags.BEDS)
                        && !cell.state().is(Blocks.CHEST)
                        && !isUsefulProjectBlock(cell.state()))
                .toList();
        return modularCellSignature(exterior);
    }

    private static final Comparator<ModularVillageStructures.Cell> MODULAR_CELL_ORDER =
            Comparator.comparingInt(ModularVillageStructures.Cell::y)
                    .thenComparingInt(ModularVillageStructures.Cell::z)
                    .thenComparingInt(ModularVillageStructures.Cell::x);

    private static final Comparator<ModularPosition> MODULAR_POSITION_ORDER =
            Comparator.comparingInt(ModularPosition::y)
                    .thenComparingInt(ModularPosition::z)
                    .thenComparingInt(ModularPosition::x);

    /**
     * Exact structural keys for exhaustive modular-v1 admission.
     *
     * <p>These deliberately retain canonical cells rather than a textual rendering or a digest.
     * {@link HashSet} may use a hash to find a bucket, but record/list equality still compares every
     * coordinate and canonical {@link BlockState}; a hash collision therefore cannot hide a clone.
     * Avoiding {@code BlockState.toString()} also keeps the integration self-test below the server
     * watchdog budget.</p>
     */
    private record ModularCellSignature(List<ModularVillageStructures.Cell> cells) {
    }

    private record ModularLayerSignature(
            ModularCellSignature base,
            ModularCellSignature stageOne,
            ModularCellSignature stageTwo,
            ModularCellSignature quality) {
    }

    private record ModularPosition(int x, int y, int z) {
    }

    private record ModularPositionSignature(List<ModularPosition> positions) {
    }

    private static void validateModularLayers(
            VillageProsperityEngine.ProjectType type,
            VillageArchitecture.BiomeDialect dialect,
            VillageArchitecture.Recipe recipe,
            ModularVillageStructures.Layers layers,
            StructureSize declared) {
        if (layers.base().isEmpty()
                || layers.stageOne().isEmpty()
                || layers.stageTwo().isEmpty()) {
            throw new IllegalStateException(
                    "Modular plan lost an authored stage for " + dialect + " " + type);
        }
        List<ModularVillageStructures.Cell> complete = new ArrayList<>();
        complete.addAll(layers.base());
        complete.addAll(layers.stageOne());
        complete.addAll(layers.stageTwo());
        complete.addAll(layers.quality());
        if (VillageArchitecture.hasQualityRetrofit(type) != !layers.quality().isEmpty()) {
            throw new IllegalStateException(
                    "Modular quality layer coverage changed for " + dialect + " " + type);
        }
        Set<BlockPos> legacyPositions = new HashSet<>();
        for (List<ModularVillageStructures.Cell> legacyLayer
                : List.of(layers.base(), layers.stageOne(), layers.stageTwo())) {
            for (ModularVillageStructures.Cell cell : legacyLayer) {
                legacyPositions.add(new BlockPos(cell.x(), cell.y(), cell.z()));
            }
        }
        for (ModularVillageStructures.Cell cell : layers.quality()) {
            BlockPos position = new BlockPos(cell.x(), cell.y(), cell.z());
            if (cell.y() <= 0 && !legacyPositions.contains(position)) {
                throw new IllegalStateException(
                        "Quality retrofit introduced a ground-plane structure cell outside the"
                                + " legacy prefix for "
                                + dialect
                                + " "
                                + type
                                + " at "
                                + position);
            }
        }
        if (complete.size() > 2_000) {
            throw new IllegalStateException(
                    "Modular plan exceeded its bounded cell budget for " + type);
        }
        Set<BlockPos> occupied = new HashSet<>();
        Map<BlockPos, BlockState> authored = new HashMap<>();
        int lights = 0;
        int beds = 0;
        int utilities = 0;
        int exchangeDesks = 0;
        for (ModularVillageStructures.Cell cell : complete) {
            BlockPos position = new BlockPos(cell.x(), cell.y(), cell.z());
            if (!occupied.add(position)) {
                throw new IllegalStateException(
                        "Modular stage overlap for " + type + " at " + position);
            }
            authored.put(position, cell.state());
            if (cell.x() < -1
                    || cell.x() > declared.width
                    || cell.y() < 0
                    || cell.y() > declared.height
                    || cell.z() < -1
                    || cell.z() > declared.depth) {
                throw new IllegalStateException(
                        "Modular plan escaped its envelope for " + type + " at " + position);
            }
            if (cell.state().is(Blocks.BARREL)
                    || cell.state().is(Blocks.LECTERN)
                    || cell.state().is(Blocks.CARTOGRAPHY_TABLE)
                    || cell.state().is(Blocks.EMERALD_BLOCK)
                    || cell.state().is(Blocks.DIAMOND_BLOCK)
                    || cell.state().is(Blocks.GOLD_BLOCK)
                    || cell.state().is(Blocks.NETHERITE_BLOCK)) {
                throw new IllegalStateException(
                        "Modular plan contains a forbidden renewable block for " + type);
            }
            lights += cell.state().is(Blocks.LANTERN) ? 1 : 0;
            beds += cell.state().is(BlockTags.BEDS) ? 1 : 0;
            utilities += isUsefulProjectBlock(cell.state()) ? 1 : 0;
            exchangeDesks += BankerProfessionSupport.isExchangeDesk(cell.state()) ? 1 : 0;
        }
        if (lights < 2 || utilities < 1) {
            throw new IllegalStateException(
                    "Modular plan lost light or function for " + dialect + " " + type);
        }
        if ((type == VillageProsperityEngine.ProjectType.COTTAGE
                        || type == VillageProsperityEngine.ProjectType.HOUSE
                        || type == VillageProsperityEngine.ProjectType.INN)
                && beds < 2) {
            throw new IllegalStateException("Modular residence lost its beds for " + type);
        }
        if (type == VillageProsperityEngine.ProjectType.EXCHANGE_HALL
                && exchangeDesks != 1) {
            throw new IllegalStateException(
                    "Modular Exchange Hall must contain exactly one Exchange Desk");
        }
        for (Map.Entry<BlockPos, BlockState> entry : authored.entrySet()) {
            if (!entry.getValue().is(Blocks.GLASS_PANE)) {
                continue;
            }
            BlockPos position = entry.getKey();
            int connections = authoredPaneConnections(authored, position, entry.getValue());
            if (connections == 0) {
                throw new IllegalStateException(
                        "Modular plan has an orphaned pane for "
                                + dialect
                                + " "
                                + type
                                + " recipe "
                                + recipe.signature()
                                + " at "
                                + position
                                + ": horizontal connections="
                                + connections);
            }
        }
        for (Map.Entry<BlockPos, BlockState> entry : authored.entrySet()) {
            if (!entry.getValue().is(Blocks.LANTERN)) {
                continue;
            }
            boolean hanging = entry.getValue().getValue(LanternBlock.HANGING);
            BlockPos support = hanging ? entry.getKey().above() : entry.getKey().below();
            if (!authored.containsKey(support)) {
                throw new IllegalStateException(
                        "Modular plan has an unsupported lantern for " + type + " at "
                                + entry.getKey());
            }
        }
        for (int rotation = 0; rotation < 4; rotation++) {
            Set<BlockPos> rotated = new HashSet<>();
            StructureSize rotatedEnvelope = rotatedSize(declared, rotation);
            for (ModularVillageStructures.Cell cell : complete) {
                Placement placement = rotatePlacement(
                        new Placement(cell.x(), cell.y(), cell.z(), cell.state()),
                        declared,
                        rotation);
                BlockPos position = new BlockPos(placement.dx, placement.dy, placement.dz);
                if (!rotated.add(position)
                        || position.getX() < -1
                        || position.getX() > rotatedEnvelope.width
                        || position.getZ() < -1
                        || position.getZ() > rotatedEnvelope.depth) {
                    throw new IllegalStateException(
                            "Modular rotation escaped or collapsed for " + type + " recipe "
                                    + recipe.signature());
                }
            }
        }
        if (type == VillageProsperityEngine.ProjectType.MINE_ENTRANCE) {
            validateMineClearance(recipe, authored, declared);
        } else if (type == VillageProsperityEngine.ProjectType.GUARD_POST) {
            validateGuardQualitySupport(dialect, recipe, layers, authored, declared);
        }
    }

    /** Mirrors the vanilla pane attachment predicate against the completed authored template. */
    private static int authoredPaneConnections(
            Map<BlockPos, BlockState> authored, BlockPos panePosition, BlockState paneState) {
        if (!(paneState.getBlock() instanceof IronBarsBlock pane)) {
            return 0;
        }
        int connections = 0;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos neighborPosition = panePosition.relative(direction);
            BlockState neighbor = authored.getOrDefault(
                    neighborPosition, Blocks.AIR.defaultBlockState());
            boolean sturdyFace = neighbor.isFaceSturdy(
                    EmptyBlockGetter.INSTANCE,
                    neighborPosition,
                    direction.getOpposite());
            if (pane.attachsTo(neighbor, sturdyFace)) {
                connections++;
            }
        }
        return connections;
    }

    private static void validateModularQualityPrefix(
            VillageProsperityEngine.ProjectType type,
            ModularVillageStructures.Layers layers) {
        if (!VillageArchitecture.hasQualityRetrofit(type)) {
            return;
        }
        List<Placement> base = withFoundationSupports(
                List.of(), toPlacements(layers.base()), layers.materials().foundation());
        List<Placement> stageOne = withFoundationSupports(
                base, toPlacements(layers.stageOne()), layers.materials().foundation());
        List<Placement> throughStageOne = new ArrayList<>(base);
        throughStageOne.addAll(stageOne);
        List<Placement> stageTwo = withFoundationSupports(
                throughStageOne,
                toPlacements(layers.stageTwo()),
                layers.materials().foundation());
        List<Placement> quality = withFoundationSupports(
                base, toPlacements(layers.quality()), layers.materials().foundation());

        if (type == VillageProsperityEngine.ProjectType.MINE_ENTRANCE) {
            for (Placement placement : quality) {
                if (placement.role == PlacementRole.TERRAIN_SUPPORT
                        || placement.dy < 3) {
                    throw new IllegalStateException(
                            "Mine quality retrofit must remain an upper-only suffix; found "
                                    + placement.role
                                    + " at "
                                    + new BlockPos(
                                            placement.dx, placement.dy, placement.dz));
                }
            }
        }

        for (int insertionStage = 0;
                insertionStage <= VillageStructureProgression.MAX_VISUAL_STAGE;
                insertionStage++) {
            List<Placement> old = orderedModularLayers(
                    base, stageOne, stageTwo, quality, insertionStage, -1);
            List<Placement> migrated = orderedModularLayers(
                    base, stageOne, stageTwo, quality, insertionStage, insertionStage);
            if (migrated.size() <= old.size()
                    || !migrated.subList(0, old.size()).equals(old)) {
                throw new IllegalStateException(
                        "Quality retrofit moved the saved "
                                + type
                                + " stage-"
                                + insertionStage
                                + " prefix");
            }
            for (int futureStage = insertionStage + 1;
                    futureStage <= VillageStructureProgression.MAX_VISUAL_STAGE;
                    futureStage++) {
                List<Placement> future = orderedModularLayers(
                        base,
                        stageOne,
                        stageTwo,
                        quality,
                        futureStage,
                        insertionStage);
                if (!future.subList(0, migrated.size()).equals(migrated)) {
                    throw new IllegalStateException(
                            "A future "
                                    + type
                                    + " stage reordered its quality-retrofit prefix");
                }
            }
        }
    }

    private static void validateMineClearance(
            VillageArchitecture.Recipe recipe,
            Map<BlockPos, BlockState> authored,
            StructureSize declared) {
        int center = declared.width / 2;
        BlockPos entrance = new BlockPos(center, 0, 0);
        BlockPos portal = new BlockPos(center, 0, declared.depth - 2);
        List<BlockPos> queue = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();
        queue.add(entrance);
        visited.add(entrance);
        for (int index = 0; index < queue.size(); index++) {
            BlockPos current = queue.get(index);
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos next = current.relative(direction);
                if (next.getX() < center - 2
                        || next.getX() > center + 2
                        || next.getZ() < 0
                        || next.getZ() > declared.depth - 2
                        || visited.contains(next)) {
                    continue;
                }
                BlockState feet = authored.get(next.above());
                boolean walkableFeet = feet == null
                        || feet.getBlock() instanceof BaseRailBlock;
                if (walkableFeet
                        && !authored.containsKey(next.above(2))
                        && authored.containsKey(next)) {
                    visited.add(next);
                    queue.add(next);
                }
            }
        }
        if (!visited.contains(portal)) {
            throw new IllegalStateException(
                    "Mine quality pass obstructed the two-high route from entrance to rail portal"
                            + (recipe.mirrored() ? " (mirrored)" : ""));
        }
    }

    private static void validateGuardQualitySupport(
            VillageArchitecture.BiomeDialect dialect,
            VillageArchitecture.Recipe recipe,
            ModularVillageStructures.Layers layers,
            Map<BlockPos, BlockState> authored,
            StructureSize declared) {
        int roofY = layers.quality().stream()
                .filter(cell -> cell.state().is(layers.materials().roofSlab()))
                .mapToInt(ModularVillageStructures.Cell::y)
                .max()
                .orElse(-1);
        if (roofY < 0 || roofY > declared.height) {
            throw new IllegalStateException(
                    "Guard quality canopy is missing or out of bounds for "
                            + dialect
                            + " recipe "
                            + recipe.signature()
                            + " (silhouette="
                            + recipe.silhouette()
                            + ", roof="
                            + recipe.roof()
                            + ", frontage="
                            + recipe.frontage()
                            + ", mirrored="
                            + recipe.mirrored()
                            + ", qualityRoofY="
                            + roofY
                            + ", height="
                            + declared.height
                            + ")");
        }
        for (int x : new int[] {2, declared.width - 3}) {
            for (int z : new int[] {2, declared.depth - 3}) {
                BlockPos post = new BlockPos(x, roofY - 1, z);
                if (!authored.containsKey(post)
                        || !authored.containsKey(post.below())
                        || !authored.containsKey(post.above())) {
                    throw new IllegalStateException(
                            "Guard quality canopy lost a supported corner at " + post);
                }
            }
        }
        for (ModularVillageStructures.Cell cell : layers.quality()) {
            if (!cell.state().is(Blocks.IRON_BARS)) {
                continue;
            }
            BlockPos position = new BlockPos(cell.x(), cell.y(), cell.z());
            boolean eastWest = authored.containsKey(position.east())
                    && authored.containsKey(position.west());
            boolean northSouth = authored.containsKey(position.north())
                    && authored.containsKey(position.south());
            if (!eastWest && !northSouth) {
                throw new IllegalStateException(
                        "Guard quality rail has a disconnected bar at " + position);
            }
        }
    }

    private static void validateProgressionLayers(
            VillageProsperityEngine.ProjectType type,
            Palette palette,
            List<Placement> legacy) {
        Set<String> variantSignatures = new HashSet<>();
        StructureSize declared = size(type);
        for (int variant = 0; variant < VillageStructureProgression.VARIANT_COUNT; variant++) {
            ProgressionLayers layers = progressionLayers(legacy, palette, type, variant);
            if (layers.variation.isEmpty()
                    || layers.stageOne.isEmpty()
                    || layers.stageTwo.isEmpty()) {
                throw new IllegalStateException(
                        "Physical progression stage is empty for " + type + " variant " + variant);
            }
            List<Placement> complete = new ArrayList<>(legacy);
            complete.addAll(layers.supports);
            complete.addAll(layers.variation);
            complete.addAll(layers.stageOne);
            complete.addAll(layers.stageTwo);
            if (complete.size() > 1_200) {
                throw new IllegalStateException(
                        "Progressive physical template is unbounded for " + type + ": "
                                + complete.size());
            }

            Set<BlockPos> occupied = new HashSet<>();
            Map<BlockPos, BlockState> authored = new HashMap<>();
            for (Placement placement : complete) {
                if (placement.isTrail()) {
                    throw new IllegalStateException("Static progression unexpectedly contains a trail");
                }
                BlockPos relative = new BlockPos(placement.dx, placement.dy, placement.dz);
                if (!occupied.add(relative)) {
                    throw new IllegalStateException(
                            "Duplicate progressive placement for " + type + " at " + relative);
                }
                authored.put(relative, placement.state);
                if (relative.getX() < -1
                        || relative.getX() > declared.width
                        || relative.getY() < -TerrainFoundationPlan.MAX_TERRAIN_DROP
                        || relative.getY() > declared.height
                        || relative.getZ() < -2
                        || relative.getZ() > declared.depth) {
                    throw new IllegalStateException(
                            "Progressive template " + type
                                    + " escaped its guarded volume at " + relative);
                }
            }
            List<TerrainFoundationPlan.Cell> completeCells = complete.stream()
                    .map(placement -> new TerrainFoundationPlan.Cell(
                            placement.dx, placement.dy, placement.dz))
                    .toList();
            if (!TerrainFoundationPlan.appendSupportCells(completeCells, 0).isEmpty()) {
                throw new IllegalStateException(
                        "Progressive template " + type
                                + " contains an authored y=1 column without a y=0 footing");
            }

            for (Map.Entry<BlockPos, BlockState> entry : authored.entrySet()) {
                BlockState state = entry.getValue();
                if (!state.is(Blocks.LANTERN)) {
                    continue;
                }
                boolean hanging = state.getValue(LanternBlock.HANGING);
                BlockPos support = hanging ? entry.getKey().above() : entry.getKey().below();
                if (!authored.containsKey(support)) {
                    throw new IllegalStateException(
                            "Progressive template " + type
                                    + " has an unsupported lantern at " + entry.getKey());
                }
            }

            int expectedDoors = type == VillageProsperityEngine.ProjectType.MARKET_SQUARE
                            || type == VillageProsperityEngine.ProjectType.MINE_ENTRANCE
                    ? 0
                    : 2;
            List<Placement> doors = layers.variation.stream()
                    .filter(placement -> placement.state.is(palette.door))
                    .toList();
            List<Placement> stairs = layers.variation.stream()
                    .filter(placement -> placement.state.is(palette.stairs))
                    .toList();
            if (doors.size() != expectedDoors || stairs.size() != 1) {
                throw new IllegalStateException(
                        "Progressive entrance is incomplete for " + type + " variant " + variant);
            }
            if (!doors.isEmpty()
                    && (doors.get(0).state.getValue(DoorBlock.FACING) != Direction.NORTH
                            || doors.stream()
                                    .map(placement -> placement.state.getValue(DoorBlock.HALF))
                                    .collect(java.util.stream.Collectors.toSet())
                                    .size()
                            != 2)) {
                throw new IllegalStateException(
                        "Progressive door orientation/halves are invalid for " + type);
            }
            if (stairs.get(0).state.getValue(StairBlock.FACING) != Direction.SOUTH) {
                throw new IllegalStateException(
                        "Progressive entrance stair faces the wrong way for " + type);
            }
            if (type == VillageProsperityEngine.ProjectType.MARKET_SQUARE) {
                for (int centerX : new int[] {4, 7}) {
                    for (int centerZ : new int[] {1, 9}) {
                        for (int x : new int[] {centerX - 1, centerX + 1}) {
                            BlockPos upperPost = new BlockPos(x, 2, centerZ);
                            BlockState post = authored.get(upperPost);
                            BlockState canopy = authored.get(upperPost.above());
                            if (post == null
                                    || !post.is(palette.fence)
                                    || canopy == null
                                    || !canopy.is(palette.roof)) {
                                throw new IllegalStateException(
                                        "Market stall canopy lost its support at " + upperPost);
                            }
                        }
                    }
                }
            }
            if (layers.stageOne.stream().noneMatch(p -> p.state.is(Blocks.CHEST))
                    || layers.stageTwo.stream().noneMatch(p -> p.state.is(Blocks.CHEST))) {
                throw new IllegalStateException(
                        "Progressive storage capacity did not increase for " + type);
            }
            String signature = layers.variation.stream()
                    .map(placement -> placement.dx + ":" + placement.dy + ":" + placement.dz + ":"
                            + BuiltInRegistries.BLOCK.getKey(placement.state.getBlock()))
                    .sorted()
                    .reduce("", (left, right) -> left + "|" + right);
            variantSignatures.add(signature);
        }
        if (variantSignatures.size() != VillageStructureProgression.VARIANT_COUNT) {
            throw new IllegalStateException(
                    "Structure presets collapsed to repetitive clones for " + type);
        }
    }

    private static void validateProjectRoof(
            VillageProsperityEngine.ProjectType type,
            Palette palette,
            Map<BlockPos, BlockState> authored) {
        int width;
        int depth;
        int roofY;
        Block roofBlock;
        switch (type) {
            case COTTAGE -> {
                width = 7;
                depth = 7;
                roofY = 4;
                roofBlock = palette.roof;
            }
            case HOUSE -> {
                width = 9;
                depth = 9;
                roofY = 5;
                roofBlock = palette.roof;
            }
            case INN -> {
                width = 11;
                depth = 9;
                roofY = 5;
                roofBlock = palette.roof;
            }
            case WAREHOUSE -> {
                width = 9;
                depth = 7;
                roofY = 4;
                roofBlock = palette.roof;
            }
            case SMITHY, GRANARY -> {
                width = 9;
                depth = 7;
                roofY = 5;
                roofBlock = palette.roof;
            }
            case GUARD_POST -> {
                width = 7;
                depth = 7;
                roofY = 5;
                roofBlock = Blocks.STONE_BRICKS;
            }
            case EXCHANGE_HALL -> {
                width = 13;
                depth = 9;
                roofY = 6;
                roofBlock = palette.roof;
            }
            case MINE_ENTRANCE, MARKET_SQUARE -> {
                return;
            }
            default -> throw new IllegalStateException("Unhandled project roof: " + type);
        }
        for (int x = -1; x <= width; x++) {
            for (int z = -1; z <= depth; z++) {
                BlockPos roofPosition = new BlockPos(x, roofY, z);
                BlockState state = authored.get(roofPosition);
                if (state == null || !state.is(roofBlock)) {
                    throw new IllegalStateException(
                            "Physical template " + type
                                    + " has an open roof at " + roofPosition);
                }
            }
        }
        if (type == VillageProsperityEngine.ProjectType.GUARD_POST) {
            return;
        }
        int fasciaY = roofY - 1;
        for (int x = -1; x <= width; x++) {
            requireRoofFascia(type, authored, new BlockPos(x, fasciaY, -1));
            requireRoofFascia(type, authored, new BlockPos(x, fasciaY, depth));
        }
        for (int z = 0; z < depth; z++) {
            requireRoofFascia(type, authored, new BlockPos(-1, fasciaY, z));
            requireRoofFascia(type, authored, new BlockPos(width, fasciaY, z));
        }
    }

    private static void requireRoofFascia(
            VillageProsperityEngine.ProjectType type,
            Map<BlockPos, BlockState> authored,
            BlockPos position) {
        if (!authored.containsKey(position)) {
            throw new IllegalStateException(
                    "Physical template " + type
                            + " has a floating roof edge at " + position);
        }
    }

    private static boolean isUsefulProjectBlock(BlockState state) {
        return state.is(Blocks.CRAFTING_TABLE)
                || state.is(Blocks.COMPOSTER)
                || state.is(Blocks.LOOM)
                || state.is(Blocks.FLETCHING_TABLE)
                || state.is(Blocks.STONECUTTER)
                || state.is(Blocks.SMOKER)
                || state.is(Blocks.BREWING_STAND)
                || state.is(Blocks.FURNACE)
                || state.is(Blocks.BLAST_FURNACE)
                || state.is(Blocks.SMITHING_TABLE)
                || state.is(Blocks.GRINDSTONE)
                || BankerProfessionSupport.isExchangeDesk(state);
    }

    private static List<Placement> cottage(Palette palette) {
        int width = 7;
        int depth = 7;
        List<Placement> placements = new ArrayList<>();
        floor(placements, width, depth, palette.floor);
        shell(placements, width, depth, 3, palette.wall, palette.corner, true);
        roof(placements, width, depth, 4, palette.roof);
        placements.add(new Placement(1, 1, depth - 2, Blocks.CHEST.defaultBlockState()));
        placements.add(new Placement(width - 2, 1, depth - 2, Blocks.BOOKSHELF.defaultBlockState()));
        BlockState bedFoot = Blocks.BED.white().defaultBlockState()
                .setValue(BedBlock.FACING, Direction.SOUTH);
        BlockState bedHead = bedFoot.setValue(BedBlock.PART, BedPart.HEAD);
        for (int bedX : new int[] {1, 2, 4, 5}) {
            placements.add(new Placement(bedX, 1, 2, bedFoot));
            placements.add(new Placement(bedX, 1, 3, bedHead));
        }
        // Keep all legacy placements above as an immutable prefix. Additive details let already
        // materialized projects receive the richer template through the normal safe repair queue.
        addBuildingDetails(placements, palette, width, depth, 3, true);
        addOffPathCeilingLight(placements, palette, width / 2 - 2, 1, 3);
        placements.add(new Placement(width / 2, 1, depth - 2, Blocks.COMPOSTER.defaultBlockState()));
        return placements;
    }

    private static List<Placement> house(Palette palette) {
        List<Placement> placements = simpleBuilding(palette, 9, 9, 4);
        addBeds(placements, 9, 9, 6);
        placements.add(new Placement(2, 1, 6, Blocks.BOOKSHELF.defaultBlockState()));
        placements.add(new Placement(6, 1, 6, Blocks.CHEST.defaultBlockState()));
        addBuildingDetails(placements, palette, 9, 9, 4, true);
        addOffPathCeilingLight(placements, palette, 2, 1, 4);
        placements.add(new Placement(4, 1, 6, Blocks.CRAFTING_TABLE.defaultBlockState()));
        placements.add(new Placement(7, 1, 7, Blocks.COMPOSTER.defaultBlockState()));
        return placements;
    }

    private static List<Placement> inn(Palette palette) {
        List<Placement> placements = simpleBuilding(palette, 11, 9, 4);
        addBeds(placements, 11, 9, 8);
        for (int x = 2; x <= 8; x += 2) {
            placements.add(new Placement(x, 1, 7, Blocks.CHEST.defaultBlockState()));
        }
        placements.add(new Placement(5, 1, 4, Blocks.CRAFTING_TABLE.defaultBlockState()));
        addBuildingDetails(placements, palette, 11, 9, 4, true);
        addOffPathCeilingLight(placements, palette, 2, 1, 4);
        placements.add(new Placement(1, 1, 7, Blocks.BREWING_STAND.defaultBlockState()));
        placements.add(new Placement(5, 1, 7, Blocks.CAKE.defaultBlockState()));
        placements.add(new Placement(9, 1, 7, Blocks.SMOKER.defaultBlockState()));
        return placements;
    }

    private static List<Placement> warehouse(Palette palette) {
        int width = 9;
        int depth = 7;
        List<Placement> placements = new ArrayList<>();
        floor(placements, width, depth, palette.floor);
        shell(placements, width, depth, 3, palette.wall, palette.corner, true);
        roof(placements, width, depth, 4, palette.roof);
        for (int x = 1; x < width - 1; x += 2) {
            placements.add(new Placement(x, 1, depth - 2, Blocks.CHEST.defaultBlockState()));
        }
        placements.add(new Placement(width / 2, 1, 2, Blocks.CRAFTING_TABLE.defaultBlockState()));
        addBuildingDetails(placements, palette, width, depth, 3, false);
        placements.add(new Placement(2, 1, 2, Blocks.LOOM.defaultBlockState()));
        placements.add(new Placement(6, 1, 2, Blocks.STONECUTTER.defaultBlockState()));
        placements.add(new Placement(2, 1, 4, Blocks.LANTERN.defaultBlockState()));
        placements.add(new Placement(6, 1, 4, Blocks.LANTERN.defaultBlockState()));
        return placements;
    }

    private static List<Placement> mineEntrance(Palette palette) {
        int width = 7;
        int depth = 7;
        List<Placement> placements = new ArrayList<>();
        floor(placements, width, depth, Blocks.COBBLESTONE);
        for (int z = 1; z < depth; z++) {
            placements.add(new Placement(1, 1, z, Blocks.COBBLESTONE.defaultBlockState()));
            placements.add(new Placement(width - 2, 1, z, Blocks.COBBLESTONE.defaultBlockState()));
            placements.add(new Placement(1, 2, z, palette.corner.defaultBlockState()));
            placements.add(new Placement(width - 2, 2, z, palette.corner.defaultBlockState()));
            placements.add(new Placement(2, 3, z, Blocks.COBBLESTONE.defaultBlockState()));
            placements.add(new Placement(3, 3, z, Blocks.COBBLESTONE.defaultBlockState()));
            placements.add(new Placement(4, 3, z, Blocks.COBBLESTONE.defaultBlockState()));
        }
        for (int z = 1; z < depth - 1; z++) {
            placements.add(new Placement(width / 2, 1, z, Blocks.RAIL.defaultBlockState()));
        }
        placements.add(new Placement(2, 1, 1, Blocks.LANTERN.defaultBlockState()));
        placements.add(new Placement(4, 1, 1, Blocks.LANTERN.defaultBlockState()));
        for (int x : new int[] {0, width - 1}) {
            placements.add(new Placement(x, 1, 0, palette.corner.defaultBlockState()));
            placements.add(new Placement(x, 2, 0, palette.corner.defaultBlockState()));
            placements.add(new Placement(x, 3, 0, palette.corner.defaultBlockState()));
        }
        for (int x = 1; x < width - 1; x++) {
            placements.add(new Placement(x, 3, 0, Blocks.COBBLESTONE.defaultBlockState()));
        }
        placements.add(new Placement(2, 1, 5, Blocks.FURNACE.defaultBlockState()));
        placements.add(new Placement(4, 1, 5, Blocks.CHEST.defaultBlockState()));
        placements.add(new Placement(2, 1, 3, Blocks.STONECUTTER.defaultBlockState()));
        return placements;
    }

    private static List<Placement> marketSquare(Palette palette) {
        int width = 11;
        int depth = 11;
        List<Placement> placements = new ArrayList<>();
        floor(placements, width, depth, Blocks.STONE_BRICKS);
        for (int x : new int[] {1, 4, 7, 9}) {
            placements.add(new Placement(x, 1, 2, Blocks.CHEST.defaultBlockState()));
            placements.add(new Placement(x, 1, 8, Blocks.CHEST.defaultBlockState()));
        }
        placements.add(new Placement(5, 1, 5, Blocks.BELL.defaultBlockState()));
        for (int[] corner : new int[][] {{1,1},{9,1},{1,9},{9,9}}) {
            placements.add(new Placement(corner[0], 1, corner[1], palette.corner.defaultBlockState()));
            placements.add(new Placement(corner[0], 2, corner[1], Blocks.LANTERN.defaultBlockState()));
        }
        addMarketStall(placements, palette, 4, 1);
        addMarketStall(placements, palette, 7, 1);
        addMarketStall(placements, palette, 4, 9);
        addMarketStall(placements, palette, 7, 9);
        placements.add(new Placement(2, 1, 5, Blocks.COMPOSTER.defaultBlockState()));
        placements.add(new Placement(4, 1, 5, Blocks.LOOM.defaultBlockState()));
        placements.add(new Placement(6, 1, 5, Blocks.FLETCHING_TABLE.defaultBlockState()));
        placements.add(new Placement(8, 1, 5, Blocks.STONECUTTER.defaultBlockState()));
        return placements;
    }

    private static List<Placement> smithy(Palette palette) {
        List<Placement> placements = simpleBuilding(palette, 9, 7, 4);
        placements.add(new Placement(2, 1, 4, Blocks.ANVIL.defaultBlockState()));
        placements.add(new Placement(4, 1, 4, Blocks.BLAST_FURNACE.defaultBlockState()));
        placements.add(new Placement(6, 1, 4, Blocks.SMITHING_TABLE.defaultBlockState()));
        placements.add(new Placement(7, 1, 2, Blocks.CHEST.defaultBlockState()));
        addBuildingDetails(placements, palette, 9, 7, 4, true);
        placements.add(new Placement(1, 1, 2, Blocks.GRINDSTONE.defaultBlockState()));
        placements.add(new Placement(4, 1, 2, Blocks.STONECUTTER.defaultBlockState()));
        placements.add(new Placement(1, 1, 4, Blocks.CAULDRON.defaultBlockState()));
        return placements;
    }

    private static List<Placement> granary(Palette palette) {
        List<Placement> placements = simpleBuilding(palette, 9, 7, 4);
        for (int x = 1; x <= 7; x += 2) {
            placements.add(new Placement(x, 1, 4, Blocks.CHEST.defaultBlockState()));
            placements.add(new Placement(x, 2, 4, Blocks.HAY_BLOCK.defaultBlockState()));
        }
        addBuildingDetails(placements, palette, 9, 7, 4, false);
        placements.add(new Placement(2, 1, 2, Blocks.SMOKER.defaultBlockState()));
        placements.add(new Placement(4, 1, 2, Blocks.COMPOSTER.defaultBlockState()));
        placements.add(new Placement(2, 1, 5, Blocks.HAY_BLOCK.defaultBlockState()));
        placements.add(new Placement(6, 1, 5, Blocks.HAY_BLOCK.defaultBlockState()));
        return placements;
    }

    private static List<Placement> guardPost(Palette palette) {
        int width = 7;
        int depth = 7;
        List<Placement> placements = new ArrayList<>();
        floor(placements, width, depth, Blocks.STONE_BRICKS);
        shell(placements, width, depth, 4, palette.wall, palette.corner, false);
        roof(placements, width, depth, 5, Blocks.STONE_BRICKS);
        placements.add(new Placement(2, 1, 4, Blocks.CHEST.defaultBlockState()));
        placements.add(new Placement(4, 1, 4, Blocks.IRON_BARS.defaultBlockState()));
        placements.add(new Placement(1, 2, 1, Blocks.LANTERN.defaultBlockState()));
        placements.add(new Placement(5, 2, 1, Blocks.LANTERN.defaultBlockState()));
        for (int x = 0; x < width; x += 2) {
            placements.add(new Placement(x, 6, 0, Blocks.STONE_BRICK_WALL.defaultBlockState()));
            placements.add(new Placement(x, 6, depth - 1, Blocks.STONE_BRICK_WALL.defaultBlockState()));
        }
        for (int z = 2; z < depth - 1; z += 2) {
            placements.add(new Placement(0, 6, z, Blocks.STONE_BRICK_WALL.defaultBlockState()));
            placements.add(new Placement(width - 1, 6, z, Blocks.STONE_BRICK_WALL.defaultBlockState()));
        }
        placements.add(new Placement(2, 1, 2, Blocks.FLETCHING_TABLE.defaultBlockState()));
        placements.add(new Placement(4, 1, 2, Blocks.GRINDSTONE.defaultBlockState()));
        return placements;
    }

    private static List<Placement> exchangeHall(Palette palette) {
        List<Placement> placements = simpleBuilding(palette, 13, 9, 5);
        for (int x = 2; x <= 10; x += 2) {
            placements.add(new Placement(x, 1, 5, Blocks.BOOKSHELF.defaultBlockState()));
        }
        placements.add(new Placement(3, 1, 7, Blocks.ENDER_CHEST.defaultBlockState()));
        placements.add(new Placement(9, 1, 7, Blocks.BELL.defaultBlockState()));
        placements.add(new Placement(6, 1, 7, Blocks.BOOKSHELF.defaultBlockState()));
        addBuildingDetails(placements, palette, 13, 9, 5, false);
        placements.add(new Placement(
                6, 1, 3, BankerProfessionSupport.exchangeDeskOrLectern().defaultBlockState()));
        for (int x : new int[] {4, 5, 7, 8}) {
            placements.add(new Placement(x, 1, 6, palette.accent.defaultBlockState()));
            placements.add(new Placement(x, 2, 6, Blocks.IRON_BARS.defaultBlockState()));
        }
        placements.add(new Placement(2, 1, 3, Blocks.LANTERN.defaultBlockState()));
        placements.add(new Placement(10, 1, 3, Blocks.LANTERN.defaultBlockState()));
        return placements;
    }

    private static void addBuildingDetails(
            List<Placement> placements,
            Palette palette,
            int width,
            int depth,
            int wallHeight,
            boolean masonryFlue) {
        int center = width / 2;
        placements.add(new Placement(center, 0, -1, palette.accent.defaultBlockState()));
        for (int x : new int[] {center - 2, center + 2}) {
            placements.add(new Placement(x, 1, -1, palette.fence.defaultBlockState()));
            placements.add(new Placement(x, 2, -1, Blocks.LANTERN.defaultBlockState()));
        }
        // The legacy bottom-slab roof is already a complete weather plane. A full-block fascia
        // immediately below its perimeter seals and supports that plane without adding a floating
        // ridge. The optional masonry flue is attached to the outer wall and terminates under the
        // roof instead of hovering above it.
        for (int x = -1; x <= width; x++) {
            placements.add(new Placement(
                    x, wallHeight, -1, palette.accent.defaultBlockState()));
            placements.add(new Placement(
                    x, wallHeight, depth, palette.accent.defaultBlockState()));
        }
        for (int z = 0; z < depth; z++) {
            placements.add(new Placement(
                    -1, wallHeight, z, palette.accent.defaultBlockState()));
            placements.add(new Placement(
                    width,
                    wallHeight,
                    z,
                    masonryFlue && z == depth - 2
                            ? Blocks.BRICKS.defaultBlockState()
                            : palette.accent.defaultBlockState()));
        }
        if (masonryFlue) {
            for (int y = 1; y < wallHeight; y++) {
                placements.add(new Placement(
                        width, y, depth - 2, Blocks.BRICKS.defaultBlockState()));
            }
        }
    }

    private static void addOffPathCeilingLight(
            List<Placement> placements,
            Palette palette,
            int x,
            int z,
            int supportY) {
        placements.add(new Placement(x, supportY, z, palette.corner.defaultBlockState()));
        placements.add(new Placement(
                x,
                supportY - 1,
                z,
                Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true)));
    }

    private static void addMarketStall(
            List<Placement> placements, Palette palette, int centerX, int centerZ) {
        int direction = centerZ < 5 ? 1 : -1;
        for (int x : new int[] {centerX - 1, centerX + 1}) {
            placements.add(new Placement(x, 1, centerZ, palette.fence.defaultBlockState()));
        }
        for (int x = centerX - 1; x <= centerX + 1; x++) {
            for (int z = centerZ; z != centerZ + direction * 3; z += direction) {
                placements.add(new Placement(x, 3, z, palette.roof.defaultBlockState()));
            }
        }
        placements.add(new Placement(
                centerX,
                2,
                centerZ,
                Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true)));
    }

    private static List<Placement> simpleBuilding(Palette palette, int width, int depth, int height) {
        List<Placement> placements = new ArrayList<>();
        floor(placements, width, depth, palette.floor);
        shell(placements, width, depth, height, palette.wall, palette.corner, true);
        roof(placements, width, depth, height + 1, palette.roof);
        return placements;
    }

    private static void addBeds(List<Placement> placements, int width, int depth, int count) {
        BlockState foot = Blocks.BED.white().defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH);
        BlockState head = foot.setValue(BedBlock.PART, BedPart.HEAD);
        int placed = 0;
        for (int x = 1; x < width - 1 && placed < count; x += 2) {
            placements.add(new Placement(x, 1, 2, foot));
            placements.add(new Placement(x, 1, 3, head));
            placed++;
        }
        for (int x = 1; x < width - 1 && placed < count; x += 2) {
            placements.add(new Placement(x, 1, depth - 4, foot));
            placements.add(new Placement(x, 1, depth - 3, head));
            placed++;
        }
    }

    private static void floor(List<Placement> placements, int width, int depth, Block block) {
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                placements.add(new Placement(x, 0, z, block.defaultBlockState()));
            }
        }
    }

    private static void shell(
            List<Placement> placements,
            int width,
            int depth,
            int height,
            Block wall,
            Block corner,
            boolean windows) {
        for (int y = 1; y <= height; y++) {
            for (int x = 0; x < width; x++) {
                for (int z = 0; z < depth; z++) {
                    boolean edge = x == 0 || x == width - 1 || z == 0 || z == depth - 1;
                    if (!edge) {
                        continue;
                    }
                    boolean entrance = z == 0 && x == width / 2 && y <= 2;
                    if (entrance) {
                        continue;
                    }
                    boolean isCorner = (x == 0 || x == width - 1)
                            && (z == 0 || z == depth - 1);
                    boolean isWindow = windows
                            && y == 2
                            && !isCorner
                            && ((z == 0 || z == depth - 1) && (x == 1 || x == width - 2));
                    placements.add(new Placement(
                            x,
                            y,
                            z,
                            isWindow
                                    ? Blocks.GLASS_PANE.defaultBlockState()
                                    : (isCorner ? corner : wall).defaultBlockState()));
                }
            }
        }
    }

    private static void roof(
            List<Placement> placements, int width, int depth, int y, Block roof) {
        for (int x = -1; x <= width; x++) {
            for (int z = -1; z <= depth; z++) {
                placements.add(new Placement(x, y, z, roof.defaultBlockState()));
            }
        }
    }

    private static Palette palette(ServerLevel level, BlockPos origin) {
        var biome = level.getBiome(origin);
        if (biome.is(BiomeTags.HAS_VILLAGE_DESERT)) {
            return new Palette(
                    Blocks.SMOOTH_SANDSTONE,
                    Blocks.CUT_SANDSTONE,
                    Blocks.STRIPPED_ACACIA_LOG,
                    Blocks.SANDSTONE_SLAB,
                    Blocks.ACACIA_FENCE,
                    Blocks.CHISELED_SANDSTONE,
                    Blocks.ACACIA_DOOR,
                    Blocks.SANDSTONE_STAIRS);
        }
        if (biome.is(BiomeTags.HAS_VILLAGE_SAVANNA)) {
            return new Palette(
                    Blocks.STONE_BRICKS,
                    Blocks.ACACIA_PLANKS,
                    Blocks.STRIPPED_ACACIA_LOG,
                    Blocks.ACACIA_SLAB,
                    Blocks.ACACIA_FENCE,
                    Blocks.SMOOTH_STONE,
                    Blocks.ACACIA_DOOR,
                    Blocks.STONE_BRICK_STAIRS);
        }
        if (biome.is(BiomeTags.HAS_VILLAGE_SNOWY)
                || biome.is(BiomeTags.HAS_VILLAGE_TAIGA)) {
            return new Palette(
                    Blocks.STONE_BRICKS,
                    Blocks.SPRUCE_PLANKS,
                    Blocks.STRIPPED_SPRUCE_LOG,
                    Blocks.SPRUCE_SLAB,
                    Blocks.SPRUCE_FENCE,
                    Blocks.CHISELED_STONE_BRICKS,
                    Blocks.SPRUCE_DOOR,
                    Blocks.STONE_BRICK_STAIRS);
        }
        return new Palette(
                Blocks.STONE_BRICKS,
                Blocks.OAK_PLANKS,
                Blocks.STRIPPED_OAK_LOG,
                Blocks.DARK_OAK_SLAB,
                Blocks.OAK_FENCE,
                Blocks.CHISELED_STONE_BRICKS,
                Blocks.OAK_DOOR,
                Blocks.STONE_BRICK_STAIRS);
    }

    private static StructureSize size(VillageProsperityEngine.ProjectType type) {
        return switch (type) {
            case COTTAGE, MINE_ENTRANCE, GUARD_POST -> new StructureSize(7, 7, 6);
            case HOUSE -> new StructureSize(9, 9, 6);
            case INN -> new StructureSize(11, 9, 6);
            case WAREHOUSE, SMITHY, GRANARY -> new StructureSize(9, 7, 6);
            case MARKET_SQUARE -> new StructureSize(11, 11, 4);
            case EXCHANGE_HALL -> new StructureSize(13, 9, 6);
        };
    }

    private static boolean isModular(EconomyState.VillageProject project) {
        return project != null
                && VillageArchitecture.MODULAR_SCHEMA.equals(project.designSchema);
    }

    private static boolean isBlueprint(EconomyState.VillageProject project) {
        return project != null
                && VillageArchitecture.BLUEPRINT_SCHEMA.equals(project.designSchema);
    }

    private static boolean isManagedProject(EconomyState.VillageProject project) {
        return project != null
                && VillageArchitecture.isManagedStructureSchema(project.designSchema);
    }

    private static StructureSize projectSize(EconomyState.VillageProject project) {
        if (isBlueprint(project)) {
            VillageArchitecture.BlueprintDescriptor descriptor =
                    VillageArchitecture.requireBlueprint(
                            project.designTemplateId, project.designTemplateRevision);
            return new StructureSize(
                    descriptor.width(), descriptor.depth(), descriptor.height());
        }
        return isModular(project) ? modularSize(project.type) : size(project.type);
    }

    private static StructureSize modularSize(VillageProsperityEngine.ProjectType type) {
        return switch (type) {
            case COTTAGE -> new StructureSize(9, 9, 10);
            case HOUSE -> new StructureSize(11, 11, 12);
            case INN -> new StructureSize(13, 11, 13);
            case WAREHOUSE, SMITHY, GRANARY -> new StructureSize(11, 9, 12);
            case MINE_ENTRANCE -> new StructureSize(9, 9, 10);
            case MARKET_SQUARE -> new StructureSize(13, 13, 7);
            case GUARD_POST -> new StructureSize(9, 9, 16);
            case EXCHANGE_HALL -> new StructureSize(15, 11, 14);
        };
    }

    private static StructureSize rotatedSize(StructureSize size, int quarterTurns) {
        return Math.floorMod(quarterTurns, 2) == 0
                ? size
                : new StructureSize(size.depth, size.width, size.height);
    }

    private static BlockPos rotateRelative(
            int x, int y, int z, StructureSize size, int quarterTurns) {
        return switch (Math.floorMod(quarterTurns, 4)) {
            case 1 -> new BlockPos(size.depth - 1 - z, y, x);
            case 2 -> new BlockPos(size.width - 1 - x, y, size.depth - 1 - z);
            case 3 -> new BlockPos(z, y, size.width - 1 - x);
            default -> new BlockPos(x, y, z);
        };
    }

    private static BlockPos inverseRotateRelative(
            int x, int y, int z, StructureSize size, int quarterTurns) {
        return switch (Math.floorMod(quarterTurns, 4)) {
            case 1 -> new BlockPos(z, y, size.depth - 1 - x);
            case 2 -> new BlockPos(size.width - 1 - x, y, size.depth - 1 - z);
            case 3 -> new BlockPos(size.width - 1 - z, y, x);
            default -> new BlockPos(x, y, z);
        };
    }

    private static Placement rotatePlacement(
            Placement placement, StructureSize size, int quarterTurns) {
        int normalized = Math.floorMod(quarterTurns, 4);
        if (normalized == 0) {
            return placement;
        }
        BlockPos position = rotateRelative(
                placement.dx, placement.dy, placement.dz, size, normalized);
        Rotation rotation = switch (normalized) {
            case 1 -> Rotation.CLOCKWISE_90;
            case 2 -> Rotation.CLOCKWISE_180;
            case 3 -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
        return new Placement(
                position.getX(),
                position.getY(),
                position.getZ(),
                placement.state.rotate(rotation),
                placement.role,
                placement.requiredSafetyFixture);
    }

    private static VillageArchitecture.BiomeDialect biomeDialect(
            ServerLevel level, BlockPos origin) {
        var biome = level.getBiome(origin);
        if (biome.is(BiomeTags.HAS_VILLAGE_DESERT)) {
            return VillageArchitecture.BiomeDialect.DESERT;
        }
        if (biome.is(BiomeTags.HAS_VILLAGE_SAVANNA)) {
            return VillageArchitecture.BiomeDialect.SAVANNA;
        }
        if (biome.is(BiomeTags.HAS_VILLAGE_SNOWY)) {
            return VillageArchitecture.BiomeDialect.SNOWY;
        }
        if (biome.is(BiomeTags.HAS_VILLAGE_TAIGA)) {
            return VillageArchitecture.BiomeDialect.TAIGA;
        }
        return VillageArchitecture.BiomeDialect.PLAINS;
    }

    private static BlockPos stableCenter(
            ServerLevel level, List<Villager> villagers, BlockPos fallback) {
        BlockPos approximate = centerOf(villagers, fallback);
        BlockPos bestBell = null;
        double bestDistance = Double.MAX_VALUE;
        int radius = 32;
        int vertical = 6;
        for (int x = approximate.getX() - radius; x <= approximate.getX() + radius; x++) {
            for (int z = approximate.getZ() - radius; z <= approximate.getZ() + radius; z++) {
                if (!level.hasChunk(Math.floorDiv(x, 16), Math.floorDiv(z, 16))) {
                    continue;
                }
                for (int y = approximate.getY() - vertical; y <= approximate.getY() + vertical; y++) {
                    BlockPos candidate = new BlockPos(x, y, z);
                    if (!level.getBlockState(candidate).is(Blocks.BELL)) {
                        continue;
                    }
                    double distance = candidate.distSqr(approximate);
                    if (distance < bestDistance) {
                        bestBell = candidate;
                        bestDistance = distance;
                    }
                }
            }
        }
        return bestBell == null ? approximate : bestBell;
    }

    private static Entity responsibleEntity(DamageSource source) {
        if (source == null) {
            return null;
        }
        Entity attacker = source.getEntity();
        if (attacker == null) {
            attacker = source.getDirectEntity();
        }
        if (attacker instanceof Projectile projectile && projectile.getOwner() != null) {
            attacker = projectile.getOwner();
        }
        return attacker;
    }

    /** Resolves direct attacks, projectile owners, and vanilla's bounded recent-player memory. */
    private static ServerPlayer responsiblePlayer(LivingEntity victim, DamageSource source) {
        if (source != null) {
            Entity causing = source.getEntity();
            if (causing instanceof ServerPlayer player) {
                return player;
            }
            Entity direct = source.getDirectEntity();
            if (direct instanceof ServerPlayer player) {
                return player;
            }
            if (direct instanceof Projectile projectile
                    && projectile.getOwner() instanceof ServerPlayer player) {
                return player;
            }
        }
        return victim.getLastHurtByPlayer() instanceof ServerPlayer player ? player : null;
    }

    private static UUID preferredTaggedVillage(List<Villager> villagers) {
        Map<UUID, Integer> counts = new HashMap<>();
        for (Villager villager : villagers) {
            UUID tagged = villageId(villager);
            if (tagged != null) {
                counts.merge(tagged, 1, Integer::sum);
            }
        }
        return counts.entrySet().stream()
                .max(Map.Entry.<UUID, Integer>comparingByValue()
                        .thenComparing(entry -> entry.getKey().toString()))
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private static boolean isMatchingVillage(
            EconomyService.VillageSnapshot snapshot,
            String dimensionKey,
            BlockPos center,
            double maximumDistance) {
        return snapshot != null
                && dimensionKey.equals(snapshot.village().dimensionKey)
                && BlockPos.of(snapshot.village().centerPos).distSqr(center)
                        <= maximumDistance * maximumDistance;
    }

    static boolean isNaturalProjectGround(BlockState state) {
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
                || state.is(Blocks.SNOW_BLOCK);
    }

    /**
     * Limits public-trail paving to soft, terrain-like soil.
     *
     * <p>The broader site/foundation predicate deliberately accepts natural stone and snow, but a
     * trail must not rewrite those common full construction blocks. Dirt and sand still have no
     * vanilla placement provenance, so protection integrations remain authoritative for them.</p>
     */
    static boolean isPaveableTrailGround(BlockState state) {
        return state.is(BlockTags.DIRT)
                || state.is(Blocks.GRASS_BLOCK)
                || state.is(Blocks.PODZOL)
                || state.is(Blocks.MYCELIUM)
                || state.is(Blocks.SAND)
                || state.is(Blocks.RED_SAND);
    }

    private static BlockPos centerOf(List<Villager> villagers, BlockPos fallback) {
        if (villagers.isEmpty()) {
            return fallback;
        }
        long x = 0L;
        long y = 0L;
        long z = 0L;
        for (Villager villager : villagers) {
            BlockPos position = villager.blockPosition();
            x += position.getX();
            y += position.getY();
            z += position.getZ();
        }
        return new BlockPos(
                (int) Math.round(x / (double) villagers.size()),
                (int) Math.round(y / (double) villagers.size()),
                (int) Math.round(z / (double) villagers.size()));
    }

    private static int countBeds(ServerLevel level, BlockPos center, int radius, int vertical) {
        int beds = 0;
        for (int x = center.getX() - radius; x <= center.getX() + radius; x++) {
            for (int z = center.getZ() - radius; z <= center.getZ() + radius; z++) {
                if (!level.hasChunk(Math.floorDiv(x, 16), Math.floorDiv(z, 16))) {
                    continue;
                }
                for (int y = center.getY() - vertical; y <= center.getY() + vertical; y++) {
                    if (level.getBlockState(new BlockPos(x, y, z)).is(BlockTags.BEDS)) {
                        beds++;
                    }
                }
            }
        }
        return (beds + 1) / 2;
    }

    private static void assignVillage(Villager villager, UUID villageId) {
        for (String tag : List.copyOf(villager.entityTags())) {
            if (tag.startsWith(VILLAGE_TAG_PREFIX)) {
                villager.removeTag(tag);
            }
        }
        villager.addTag(VILLAGE_TAG_PREFIX + villageId.toString().replace("-", ""));
    }

    private static VillageProsperityEngine.IncidentCause classifyCause(Entity killer) {
        if (killer instanceof ServerPlayer) {
            return VillageProsperityEngine.IncidentCause.PLAYER;
        }
        if (killer == null) {
            return VillageProsperityEngine.IncidentCause.ENVIRONMENT;
        }
        return isRaider(killer)
                ? VillageProsperityEngine.IncidentCause.PILLAGER
                : killer instanceof Monster
                        ? VillageProsperityEngine.IncidentCause.HOSTILE
                        : VillageProsperityEngine.IncidentCause.UNKNOWN;
    }

    private static boolean isRaider(Entity entity) {
        String id = String.valueOf(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()));
        return id.contains("pillager")
                || id.contains("vindicator")
                || id.contains("evoker")
                || id.contains("ravager")
                || id.contains("illusioner");
    }

    private static String dimensionKey(ServerLevel level) {
        return level.dimension().identifier().toString();
    }

    static String professionId(Holder<VillagerProfession> profession) {
        return profession.unwrapKey()
                .map(key -> key.identifier().toString())
                .orElse("minecraft:none");
    }

    private static long regionKey(BlockPos position, int regionSize, String dimensionKey) {
        int regionX = Math.floorDiv(position.getX(), regionSize);
        int regionZ = Math.floorDiv(position.getZ(), regionSize);
        long spatialKey = ((long) regionX << 32) ^ (regionZ & 0xFFFFFFFFL);
        if ("minecraft:overworld".equals(dimensionKey)) {
            return spatialKey;
        }
        long dimensionSalt = 0x9E3779B97F4A7C15L * dimensionKey.hashCode();
        return spatialKey ^ Long.rotateLeft(dimensionSalt, 23);
    }

    private enum PlacementRole {
        STRUCTURE,
        COSMETIC,
        COSMETIC_SUPPORT,
        TERRAIN_SUPPORT,
        ACCESS_CLEARANCE,
        ENTRANCE_SUPPORT,
        ENTRANCE_CLEARANCE,
        ENTRANCE_STAIR,
        TRAIL_PRIMARY,
        TRAIL_SHOULDER,
        TRAIL_CENTER_SURFACE_RETROFIT
    }

    private record Placement(
            int dx,
            int dy,
            int dz,
            BlockState state,
            PlacementRole role,
            boolean requiredSafetyFixture) {
        private Placement(int dx, int dy, int dz, BlockState state) {
            this(dx, dy, dz, state, PlacementRole.STRUCTURE, false);
        }

        private Placement(
                int dx, int dy, int dz, BlockState state, PlacementRole role) {
            this(dx, dy, dz, state, role, false);
        }

        private static Placement support(int dx, int dy, int dz, BlockState state) {
            return new Placement(dx, dy, dz, state, PlacementRole.TERRAIN_SUPPORT);
        }

        private static Placement cosmeticSupport(int dx, int dy, int dz, BlockState state) {
            return new Placement(dx, dy, dz, state, PlacementRole.COSMETIC_SUPPORT);
        }

        private static Placement accessClearance(int dx, int dy, int dz) {
            return new Placement(
                    dx,
                    dy,
                    dz,
                    Blocks.AIR.defaultBlockState(),
                    PlacementRole.ACCESS_CLEARANCE);
        }

        private static Placement entranceSupport(
                int dx, int dy, int dz, BlockState state) {
            return new Placement(dx, dy, dz, state, PlacementRole.ENTRANCE_SUPPORT);
        }

        private static Placement entranceClearance(int dx, int dy, int dz) {
            return new Placement(
                    dx,
                    dy,
                    dz,
                    Blocks.AIR.defaultBlockState(),
                    PlacementRole.ENTRANCE_CLEARANCE);
        }

        private static Placement entranceStair(
                int dx, int dy, int dz, BlockState state) {
            return new Placement(dx, dy, dz, state, PlacementRole.ENTRANCE_STAIR);
        }

        private static Placement trail(
                int dx, int dz, BlockState state, boolean shoulder) {
            return new Placement(
                    dx,
                    0,
                    dz,
                    state,
                    shoulder ? PlacementRole.TRAIL_SHOULDER : PlacementRole.TRAIL_PRIMARY);
        }

        private static Placement trailCenterSurfaceRetrofit(int dx, int dz) {
            return new Placement(
                    dx,
                    0,
                    dz,
                    Blocks.DIRT_PATH.defaultBlockState(),
                    PlacementRole.TRAIL_CENTER_SURFACE_RETROFIT);
        }

        private boolean isTrail() {
            return role == PlacementRole.TRAIL_PRIMARY
                    || role == PlacementRole.TRAIL_SHOULDER
                    || role == PlacementRole.TRAIL_CENTER_SURFACE_RETROFIT;
        }

        private boolean isTrailCenterSurfaceRetrofit() {
            return role == PlacementRole.TRAIL_CENTER_SURFACE_RETROFIT;
        }

        private boolean isCosmetic() {
            return !requiredSafetyFixture
                    && (role == PlacementRole.COSMETIC
                            || role == PlacementRole.COSMETIC_SUPPORT);
        }

        private boolean isStructuralAuthority() {
            return role == PlacementRole.STRUCTURE || requiredSafetyFixture;
        }

        /** Retains the historical terrain-footprint boundary used by v1 blueprints. */
        private boolean isGroundAuthoritative() {
            return role != PlacementRole.COSMETIC
                    && role != PlacementRole.COSMETIC_SUPPORT;
        }

        private boolean isAccessClearance() {
            return role == PlacementRole.ACCESS_CLEARANCE;
        }
    }

    private record StructureSize(int width, int depth, int height) {
    }

    private record BlueprintPlacementPlan(
            List<Placement> base,
            List<Placement> stageOne,
            List<Placement> stageTwo,
            List<Placement> canonicalBase,
            List<Placement> canonicalStageOne,
            List<Placement> canonicalStageTwo,
            VillageArchitecture.BlueprintDescriptor descriptor,
            String characterId,
            String dialectId,
            AuthoredVillageStructures.Materials materials) {
    }

    private record BlueprintMismatchKey(UUID villageId, long projectId) {
    }

    private static final class BlueprintPlanMismatchException extends IllegalStateException {
        private final UUID villageId;
        private final long projectId;
        private MaterializationBudget remainingBudget;

        private BlueprintPlanMismatchException(
                UUID villageId, long projectId, String message) {
            super(message);
            this.villageId = villageId;
            this.projectId = projectId;
        }

        private BlueprintPlanMismatchException(
                UUID villageId, long projectId, String message, RuntimeException cause) {
            super(message, cause);
            this.villageId = villageId;
            this.projectId = projectId;
        }

        private BlueprintPlanMismatchException withRemainingBudget(
                MaterializationBudget remainingBudget) {
            this.remainingBudget = remainingBudget;
            return this;
        }
    }

    private record ProjectBounds(BlockPos minimum, BlockPos maximum) {
    }

    private record ProjectSiteSearch(
            BlockPos origin,
            BlockPos trailAnchor,
            int entranceApproachStepCount,
            int entranceApproachTotalCells,
            VillageMaterializationPolicy.SiteAvailability availability) {
        private ProjectSiteSearch(
                BlockPos origin, VillageMaterializationPolicy.SiteAvailability availability) {
            this(origin, null, 0, 0, availability);
        }
    }

    private record EntranceApproachSearch(
            List<Placement> placements,
            int stepCount,
            VillageMaterializationPolicy.SiteAvailability availability) {
        private static EntranceApproachSearch flat() {
            return new EntranceApproachSearch(
                    List.of(),
                    0,
                    VillageMaterializationPolicy.SiteAvailability.AVAILABLE);
        }

        private static EntranceApproachSearch unsafe() {
            return new EntranceApproachSearch(
                    List.of(),
                    0,
                    VillageMaterializationPolicy.SiteAvailability.UNSAFE);
        }

        private static EntranceApproachSearch unloaded() {
            return new EntranceApproachSearch(
                    List.of(),
                    0,
                    VillageMaterializationPolicy.SiteAvailability.INCOMPLETE_UNLOADED);
        }
    }

    private record TrailWork(
            EconomyState.VillageProject project, List<Placement> trail) {
    }

    private record TrailCenterSurfaceWork(
            EconomyState.VillageProject project, List<Placement> trail) {
    }

    private record MaterializationBudget(int remainingBlocks, int remainingVillages) {
    }

    private record ProgressionLayers(
            List<Placement> supports,
            List<Placement> variation,
            List<Placement> stageOne,
            List<Placement> stageTwo) {
    }

    private record Palette(
            Block floor,
            Block wall,
            Block corner,
            Block roof,
            Block fence,
            Block accent,
            Block door,
            Block stairs) {
    }
}
