package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.EconomyService;
import com.chedidandrew.emeraldstandard.core.EconomyState;
import com.chedidandrew.emeraldstandard.core.VillageProsperityEngine;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
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
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

/**
 * Connects the loader-neutral Village Prosperity System to loaded Minecraft villages.
 *
 * <p>Offline progression is data-only. This manager never force-loads chunks, never lets villagers
 * mine arbitrary terrain, and materializes only a small bounded number of blocks while players are
 * nearby.</p>
 */
public final class VillageProsperityManager {
    private static final String VILLAGE_TAG_PREFIX = "the_emerald_standard_village_";
    private static final Map<UUID, Long> LAST_SETTLER_TICK = new HashMap<>();
    private static final Map<UUID, Long> LAST_WORKER_VISUAL_TICK = new HashMap<>();

    private VillageProsperityManager() {
    }

    /** Clears world-session-only presentation and pacing state between server instances. */
    public static void resetRuntimeState() {
        LAST_SETTLER_TICK.clear();
        LAST_WORKER_VISUAL_TICK.clear();
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
                budget = materializeDevelopment(level, economy, config, gameTime, budget);
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
            if (player.level() != level || !level.isVillage(player.blockPosition())) {
                continue;
            }
            int sampleX = Math.floorDiv(player.blockPosition().getX(), 64);
            int sampleZ = Math.floorDiv(player.blockPosition().getZ(), 64);
            long sampleKey = ((long) sampleX << 32) ^ (sampleZ & 0xFFFFFFFFL);
            if (!sampledAreas.add(sampleKey)) {
                continue;
            }

            AABB area = new AABB(player.blockPosition()).inflate(48.0, 24.0, 48.0);
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
            BlockPos approximateCenter = centerOf(villagers, player.blockPosition());
            EconomyService.VillageSnapshot taggedVillage = preferredVillageId == null
                    ? null
                    : economy.villageSnapshot(preferredVillageId);
            boolean trustedTaggedVillage =
                    isMatchingVillage(taggedVillage, dimensionKey, approximateCenter, 96.0);
            BlockPos center = trustedTaggedVillage
                    ? BlockPos.of(taggedVillage.village().centerPos)
                    : stableCenter(level, villagers, player.blockPosition());
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
        int villagesToProcess = VillageMaterializationPolicy.villagesToProcess(
                snapshots.size(), budget.remainingVillages);
        int firstVillage = snapshots.isEmpty()
                ? 0
                : Math.floorMod(
                        gameTime / config.villageConstructionIntervalTicks()
                                + dimensionKey.hashCode(),
                        snapshots.size());
        for (int step = 0; step < villagesToProcess; step++) {
            EconomyService.VillageSnapshot snapshot = snapshots.get(
                    VillageMaterializationPolicy.rotatingIndex(
                            firstVillage, step, snapshots.size()));
            EconomyState.VillageRecord village = snapshot.village();
            BlockPos villageCenter = BlockPos.of(village.centerPos);
            if (!positionColumnLoaded(level, villageCenter)) {
                continue;
            }
            spawnPendingSettler(level, economy, village, config, gameTime);
            reconcileOneMaterializedProject(level, economy, village, config, gameTime);
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
            EconomyState.VillageProject project = village.nextVisualProject(gameTime);
            if (project == null) {
                continue;
            }
            if (project.originPos == 0L) {
                ProjectSiteSearch siteSearch = findProjectOrigin(level, village, project);
                if (siteSearch.availability
                        == VillageMaterializationPolicy.SiteAvailability.INCOMPLETE_UNLOADED) {
                    DebugFlightRecorder.recordConstruction(
                            level,
                            village.villageId,
                            project.projectId,
                            project.type.name(),
                            "site_waiting_for_chunks",
                            villageCenter,
                            project.materializedBlocks,
                            project.totalBlocks,
                            "Candidate project lots were not fully loaded; no failure backoff was applied");
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
                List<Placement> template = projectTemplate(level, origin, village, project);
                ProjectBounds bounds = bounds(origin, template);
                if (!economy.reserveVillageProjectSite(
                        village.villageId,
                        project.projectId,
                        origin.asLong(),
                        bounds.minimum.asLong(),
                        bounds.maximum.asLong(),
                        template.size())) {
                    continue;
                }
                project.originPos = origin.asLong();
                project.boundsMinPos = bounds.minimum.asLong();
                project.boundsMaxPos = bounds.maximum.asLong();
                project.totalBlocks = template.size();
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
            int placedThisTick = 0;
            boolean blocked = false;
            boolean unloaded = false;
            while (index < placements.size() && remainingBlockBudget > 0) {
                Placement placement = placements.get(index);
                if (!placementColumnLoaded(level, origin, placement)) {
                    // Persist the same bounded backoff used for unsafe placements instead of
                    // probing an unloaded boundary on every village pulse.
                    blocked = true;
                    unloaded = true;
                    break;
                }
                BlockPos target = placementTarget(level, origin, placement);
                BlockState current = level.getBlockState(target);
                if (placementSatisfied(level, origin, target, current, placement)) {
                    if (placement.role == PlacementRole.STRUCTURE
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
                if (!VillageDevelopmentProtection.mayPlace(
                        level,
                        village.villageId,
                        project.projectId,
                        target,
                        current,
                        placement.state)) {
                    if (placement.isTrail()) {
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
                        && mayApplyPlacement(current, placement);
                if (!safe) {
                    if (placement.isTrail()) {
                        index++;
                        continue;
                    }
                    blocked = true;
                    break;
                }
                if (!level.setBlock(target, placement.state, 3)
                        || !level.getBlockState(target).is(placement.state.getBlock())) {
                    blocked = true;
                    break;
                }
                placedThisTick++;
                remainingBlockBudget--;
                index++;
            }
            boolean complete = index >= placements.size();
            if (index > project.materializedBlocks || complete) {
                economy.updateVillageProjectMaterialization(
                        village.villageId, project.projectId, index, complete, false);
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
                showWorkerActivity(level, village, project.type, origin, gameTime);
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
        return new MaterializationBudget(
                remainingBlockBudget, budget.remainingVillages - villagesToProcess);
    }

    /** Displays bounded worker theatre without creating persistent AI or economic authority. */
    private static void showWorkerActivity(
            ServerLevel level,
            EconomyState.VillageRecord village,
            VillageProsperityEngine.ProjectType projectType,
            BlockPos projectOrigin,
            long gameTime) {
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
        BlockPos waypoint = workerWaypoint(level, projectOrigin, projectType);
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
            VillageProsperityEngine.ProjectType projectType) {
        StructureSize dimensions = size(projectType);
        int[][] offsets = {
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
            long gameTime) {
        if (village == null || village.projects.isEmpty()) {
            return;
        }
        if (!positionColumnLoaded(level, BlockPos.of(village.centerPos))) {
            return;
        }
        long interval = Math.max(1L, config.villageConstructionIntervalTicks());
        long auditPulses = Math.max(1L, 2_400L / interval);
        long pulse = gameTime / interval;
        if (Math.floorMod(pulse + village.villageId.hashCode(), auditPulses) != 0L) {
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
        EconomyState.VillageProject project = candidates.get(Math.floorMod(
                (int) (pulse + village.villageId.hashCode()), candidates.size()));
        BlockPos origin = BlockPos.of(project.originPos);
        if (!positionColumnLoaded(level, origin)) {
            return;
        }
        List<Placement> expected = projectTemplate(level, origin, village, project);
        ProjectBounds expectedBounds = bounds(origin, expected);
        boolean templateExpanded = project.totalBlocks > 0
                && project.totalBlocks < expected.size();
        int priorTemplateSize = templateExpanded ? project.totalBlocks : expected.size();
        ProjectBounds priorBounds = templateExpanded
                ? bounds(origin, expected.subList(0, priorTemplateSize))
                : expectedBounds;
        int verifiedPrefix = priorTemplateSize;
        boolean queuedTemplateUpgrade = false;
        BlockPos mismatch = null;
        for (int index = 0; index < priorTemplateSize; index++) {
            Placement placement = expected.get(index);
            // Trails are shared public infrastructure once authored. Normal terrain updates or a
            // player's landscaping must not suspend the economic authority of the building they
            // originally connected.
            if (placement.isTrail()) {
                continue;
            }
            if (!placementColumnLoaded(level, origin, placement)) {
                return;
            }
            BlockPos target = placementTarget(level, origin, placement);
            BlockState current = level.getBlockState(target);
            if (!placementSatisfied(level, origin, target, current, placement)) {
                verifiedPrefix = index;
                mismatch = target;
                break;
            }
        }
        if (mismatch == null && templateExpanded) {
            // A newer release may append detail to a deterministic template. Preflight every new
            // position before suspending the legacy project's benefits. If a player has built in
            // that space, retain the valid older structure instead of turning the upgrade into an
            // obstruction or overwriting their work.
            for (int index = priorTemplateSize; index < expected.size(); index++) {
                Placement placement = expected.get(index);
                if (!placementColumnLoaded(level, origin, placement)) {
                    return;
                }
                BlockPos target = placementTarget(level, origin, placement);
                BlockState current = level.getBlockState(target);
                if (placement.role != PlacementRole.STRUCTURE
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
                if (!isSafeTemplateUpgradeTarget(
                        current,
                        level.getBlockEntity(target) != null,
                        protectionAllowed,
                        level,
                        target,
                        placement)) {
                    if (placement.isTrail()) {
                        // Trails were not part of legacy project authority. Retrofit every safe
                        // cell, but do not let one claimed/player-built cell veto later upgrades.
                        continue;
                    }
                    return;
                }
            }
            Placement firstAddition = expected.get(priorTemplateSize);
            if (!placementColumnLoaded(level, origin, firstAddition)) {
                return;
            }
            mismatch = placementTarget(level, origin, firstAddition);
            queuedTemplateUpgrade = true;
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
        if (!queuedTemplateUpgrade
                && project.manualRepairRequired
                && project.materializedBlocks == verifiedPrefix
                && project.totalBlocks == priorTemplateSize
                && project.boundsMinPos == priorBounds.minimum.asLong()
                && project.boundsMaxPos == priorBounds.maximum.asLong()) {
            return;
        }
        boolean reconciled = queuedTemplateUpgrade
                ? economy.reconcileVillageProjectMaterializationAndBounds(
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
        if (current.is(placement.state.getBlock())) {
            return true;
        }
        if (placement.isTrail()) {
            // A later branch adopts, rather than repaves, an earlier TES trail.
            return isVillageTrailGround(current);
        }
        if (placement.role != PlacementRole.TERRAIN_SUPPORT) {
            return false;
        }
        if (isNaturalProjectGround(current)) {
            return true;
        }
        // Once a support column meets natural ground, deeper authored support cells are no-ops.
        // This prevents a shallow foundation from tunnelling into a cave beneath sound terrain.
        for (int y = placement.dy + 1; y <= 0; y++) {
            BlockPos above = origin.offset(placement.dx, y, placement.dz);
            if (isNaturalProjectGround(level.getBlockState(above))) {
                return true;
            }
        }
        return false;
    }

    private static boolean mayApplyPlacement(BlockState current, Placement placement) {
        if (placement.isTrail()) {
            return isNaturalProjectGround(current) || isVillageTrailGround(current);
        }
        return current.isAir() || current.canBeReplaced();
    }

    private static boolean trailHasClearance(ServerLevel level, BlockPos ground) {
        BlockPos above = ground.above();
        BlockState state = level.getBlockState(above);
        return level.getBlockEntity(above) == null
                && level.getFluidState(above).isEmpty()
                && (state.isAir() || state.canBeReplaced());
    }

    private static boolean isVillageTrailGround(BlockState state) {
        return state.is(Blocks.DIRT_PATH)
                || state.is(Blocks.GRAVEL)
                || state.is(Blocks.COARSE_DIRT);
    }

    private static ProjectSiteSearch findProjectOrigin(
            ServerLevel level,
            EconomyState.VillageRecord village,
            EconomyState.VillageProject project) {
        BlockPos center = BlockPos.of(village.centerPos);
        int[][] offsets = {
                {30, 0}, {-30, 0}, {0, 30}, {0, -30},
                {30, 18}, {-30, 18}, {30, -18}, {-30, -18},
                {38, 38}, {-38, 38}, {38, -38}, {-38, -38},
                {48, 0}, {-48, 0}, {0, 48}, {0, -48},
                {52, 26}, {-52, 26}, {52, -26}, {-52, -26}
        };
        int start = Math.floorMod((int) (project.projectId ^ village.villageId.hashCode()), offsets.length);
        StructureSize size = size(project.type);
        boolean sawUnloadedCandidate = false;
        for (int step = 0; step < offsets.length; step++) {
            int[] offset = offsets[(start + step) % offsets.length];
            int centerX = center.getX() + offset[0];
            int centerZ = center.getZ() + offset[1];
            ProjectSiteSearch originSearch = safeOrigin(level, centerX, centerZ, size);
            if (originSearch.availability
                    == VillageMaterializationPolicy.SiteAvailability.INCOMPLETE_UNLOADED) {
                sawUnloadedCandidate = true;
                continue;
            }
            if (originSearch.availability
                    != VillageMaterializationPolicy.SiteAvailability.AVAILABLE) {
                continue;
            }
            BlockPos origin = originSearch.origin;
            if (village.bankAnchorPos != 0L
                    && origin.distSqr(BlockPos.of(village.bankAnchorPos)) < 18.0 * 18.0) {
                continue;
            }
            List<Placement> planned = projectTemplate(level, origin, village, project);
            VillageMaterializationPolicy.SiteAvailability siteAvailability = mayUseProjectSite(
                    level, village.villageId, project.projectId, origin, planned);
            if (siteAvailability
                    == VillageMaterializationPolicy.SiteAvailability.INCOMPLETE_UNLOADED) {
                sawUnloadedCandidate = true;
                continue;
            }
            if (siteAvailability != VillageMaterializationPolicy.SiteAvailability.AVAILABLE) {
                continue;
            }
            ProjectBounds candidateBounds = bounds(origin, planned);
            boolean overlaps = village.projects.stream()
                    .filter(other -> other.originPos != 0L && other.projectId != project.projectId)
                    .map(VillageProsperityManager::projectBounds)
                    .anyMatch(other -> overlaps(candidateBounds, other, 2));
            if (!overlaps) {
                return new ProjectSiteSearch(
                        origin, VillageMaterializationPolicy.SiteAvailability.AVAILABLE);
            }
        }
        return new ProjectSiteSearch(
                null,
                VillageMaterializationPolicy.completedSiteSearch(
                        false, sawUnloadedCandidate));
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
            if (!placementColumnLoaded(level, origin, placement)) {
                return VillageMaterializationPolicy.SiteAvailability.INCOMPLETE_UNLOADED;
            }
        }
        BlockPos previousPrimaryTrail = null;
        for (Placement placement : placements) {
            BlockPos target = placementTarget(level, origin, placement);
            BlockState existing = level.getBlockState(target);
            if (placement.role == PlacementRole.TRAIL_PRIMARY) {
                if (previousPrimaryTrail != null
                        && Math.abs(target.getY() - previousPrimaryTrail.getY()) > 1) {
                    return VillageMaterializationPolicy.SiteAvailability.UNSAFE;
                }
                previousPrimaryTrail = target;
            }
            if (placement.role != PlacementRole.STRUCTURE
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
                return VillageMaterializationPolicy.SiteAvailability.UNSAFE;
            }
        }
        return VillageMaterializationPolicy.SiteAvailability.AVAILABLE;
    }

    private static ProjectSiteSearch safeOrigin(
            ServerLevel level, int centerX, int centerZ, StructureSize size) {
        int minimumX = centerX - size.width / 2 - 1;
        int maximumX = centerX + size.width / 2 + 1;
        int minimumZ = centerZ - size.depth / 2 - 2;
        int maximumZ = centerZ + size.depth / 2 + 1;
        if (!areaColumnsLoaded(level, minimumX, maximumX, minimumZ, maximumZ)) {
            return new ProjectSiteSearch(
                    null,
                    VillageMaterializationPolicy.SiteAvailability.INCOMPLETE_UNLOADED);
        }
        int minimum = Integer.MAX_VALUE;
        int maximum = Integer.MIN_VALUE;
        for (int x = minimumX; x <= maximumX; x++) {
            for (int z = minimumZ; z <= maximumZ; z++) {
                int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                BlockPos ground = new BlockPos(x, surface - 1, z);
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
        }
        if (!TerrainFoundationPlan.supportsTerrainRange(
                minimum, maximum, TerrainFoundationPlan.MAX_TERRAIN_DROP)) {
            return new ProjectSiteSearch(
                    null, VillageMaterializationPolicy.SiteAvailability.UNSAFE);
        }
        // Level at the highest sampled natural surface. The deterministic terrain-support suffix
        // bridges only small drops; natural ground satisfies a support without being replaced.
        BlockPos origin = new BlockPos(
                centerX - size.width / 2,
                maximum,
                centerZ - size.depth / 2);
        for (int x = -1; x <= size.width; x++) {
            for (int z = -2; z <= size.depth; z++) {
                for (int y = 0; y <= size.height; y++) {
                    BlockPos target = origin.offset(x, y, z);
                    BlockState state = level.getBlockState(target);
                    if (level.getBlockEntity(target) != null
                            || (!state.isAir() && !state.canBeReplaced())) {
                        return new ProjectSiteSearch(
                                null, VillageMaterializationPolicy.SiteAvailability.UNSAFE);
                    }
                }
            }
        }
        return new ProjectSiteSearch(
                origin, VillageMaterializationPolicy.SiteAvailability.AVAILABLE);
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
        StructureSize structure = size(project.type);
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

    /**
     * Builds an append-only, save-stable project plan. The legacy template is always the exact
     * prefix so projects from older saves can enter the existing guarded upgrade queue.
     */
    private static List<Placement> projectTemplate(
            ServerLevel level,
            BlockPos origin,
            EconomyState.VillageRecord village,
            EconomyState.VillageProject project) {
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
        result.addAll(projectTrail(palette, origin, village, project));
        int baselineBlocks = result.size();
        int stageOneBlocks = baselineBlocks + layers.stageOne.size();
        int stageTwoBlocks = stageOneBlocks + layers.stageTwo.size();
        int persistedBlocks = project.originPos == 0L ? 0 : Math.max(0, project.totalBlocks);
        int visualStage = VillageStructureProgression.targetVisualStage(
                village.developmentTier,
                persistedBlocks,
                baselineBlocks,
                stageOneBlocks,
                stageTwoBlocks);
        if (visualStage >= 1) {
            result.addAll(layers.stageOne);
        }
        if (visualStage >= 2) {
            result.addAll(layers.stageTwo);
        }
        return List.copyOf(result);
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
            EconomyState.VillageProject project) {
        StructureSize structure = size(project.type);
        int startX = structure.width / 2;
        int startZ = trailEntranceZ(project.type);
        BlockPos anchor = trailAnchor(origin, village, project, structure);
        int targetX = anchor.getX() - origin.getX();
        int targetZ = anchor.getZ() - origin.getZ();
        long seed = VillageStructureProgression.mix64(project.projectId
                ^ village.villageId.getMostSignificantBits()
                ^ Long.rotateLeft(village.villageId.getLeastSignificantBits(), 17));

        List<VillageStructureProgression.TrailCell> primary = new ArrayList<>();
        List<VillageStructureProgression.TrailCell> shoulders = new ArrayList<>();
        if (targetZ <= startZ) {
            appendTrailSegment(primary, shoulders, startX, startZ, targetX, targetZ, seed);
        } else if (targetZ >= structure.depth + 1) {
            int sideX = trailSide(structure, targetX, seed);
            appendTrailSegment(primary, shoulders, startX, startZ, sideX, startZ, seed);
            appendTrailSegment(
                    primary, shoulders, sideX, startZ, sideX, structure.depth + 2, seed + 1L);
            appendTrailSegment(
                    primary,
                    shoulders,
                    sideX,
                    structure.depth + 2,
                    targetX,
                    targetZ,
                    seed + 2L);
        } else {
            int sideX = targetX < 0 ? -3 : structure.width + 2;
            appendTrailSegment(primary, shoulders, startX, startZ, sideX, startZ, seed);
            appendTrailSegment(primary, shoulders, sideX, startZ, sideX, targetZ, seed + 1L);
            appendTrailSegment(primary, shoulders, sideX, targetZ, targetX, targetZ, seed + 2L);
        }
        if (primary.isEmpty() || primary.size() > 192) {
            return List.of();
        }

        Set<Long> primaryCells = new HashSet<>();
        List<Placement> result = new ArrayList<>(primary.size() + shoulders.size());
        for (VillageStructureProgression.TrailCell cell : primary) {
            if (insideStructureEnvelope(cell.x(), cell.z(), structure)
                    || !primaryCells.add(packXZ(cell.x(), cell.z()))) {
                continue;
            }
            result.add(Placement.trail(
                    cell.x(), cell.z(), trailState(palette, seed, cell.x(), cell.z(), false), false));
        }
        Set<Long> emitted = new HashSet<>(primaryCells);
        for (VillageStructureProgression.TrailCell cell : shoulders) {
            long packed = packXZ(cell.x(), cell.z());
            if (!insideStructureEnvelope(cell.x(), cell.z(), structure) && emitted.add(packed)) {
                result.add(Placement.trail(
                        cell.x(), cell.z(), trailState(palette, seed, cell.x(), cell.z(), true), true));
            }
        }
        return List.copyOf(result);
    }

    private static BlockPos trailAnchor(
            BlockPos origin,
            EconomyState.VillageRecord village,
            EconomyState.VillageProject project,
            StructureSize structure) {
        int entranceZ = trailEntranceZ(project.type);
        BlockPos start = origin.offset(structure.width / 2, 0, entranceZ);
        EconomyState.VillageProject branch = village.projects.stream()
                .filter(candidate -> candidate != null
                        && candidate != project
                        && candidate.projectId < project.projectId
                        && candidate.originPos != 0L
                        && !candidate.abstractOnly
                        && candidate.economicComplete)
                .min(Comparator.<EconomyState.VillageProject>comparingLong(candidate -> {
                    BlockPos candidateOrigin = BlockPos.of(candidate.originPos);
                    BlockPos entrance = candidateOrigin.offset(
                            size(candidate.type).width / 2, 0, trailEntranceZ(candidate.type));
                    long dx = (long) entrance.getX() - start.getX();
                    long dz = (long) entrance.getZ() - start.getZ();
                    return dx * dx + dz * dz;
                }).thenComparingLong(candidate -> candidate.projectId))
                .orElse(null);
        if (branch != null) {
            BlockPos branchOrigin = BlockPos.of(branch.originPos);
            return branchOrigin.offset(
                    size(branch.type).width / 2, 0, trailEntranceZ(branch.type));
        }

        BlockPos center = BlockPos.of(village.centerPos);
        long dx = (long) start.getX() - center.getX();
        long dz = (long) start.getZ() - center.getZ();
        double length = Math.max(1.0, StrictMath.sqrt((double) dx * dx + (double) dz * dz));
        // The first connector joins a stable outskirts hub rather than cutting through the bell
        // square. Later structures branch from the nearest completed connector endpoint.
        return new BlockPos(
                center.getX() + (int) StrictMath.round(dx / length * 14.0),
                center.getY(),
                center.getZ() + (int) StrictMath.round(dz / length * 14.0));
    }

    private static int trailEntranceZ(VillageProsperityEngine.ProjectType type) {
        return type == VillageProsperityEngine.ProjectType.MARKET_SQUARE
                        || type == VillageProsperityEngine.ProjectType.MINE_ENTRANCE
                ? -2
                : -3;
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
            int startX,
            int startZ,
            int targetX,
            int targetZ,
            long seed) {
        List<VillageStructureProgression.TrailCell> segment = VillageStructureProgression.trail(
                startX, startZ, targetX, targetZ, seed, 192);
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
            Palette palette, long seed, int x, int z, boolean shoulder) {
        long detail = VillageStructureProgression.mix64(
                seed ^ (long) x * 0x9E3779B97F4A7C15L ^ (long) z * 0xC2B2AE3D27D4EB4FL);
        if (palette.floor == Blocks.SMOOTH_SANDSTONE) {
            return (shoulder || Math.floorMod(detail, 5L) == 0L
                            ? Blocks.COARSE_DIRT
                            : Blocks.GRAVEL)
                    .defaultBlockState();
        }
        if (shoulder || Math.floorMod(detail, 11L) == 0L) {
            return Blocks.COARSE_DIRT.defaultBlockState();
        }
        return Math.floorMod(detail, 7L) == 0L
                ? Blocks.GRAVEL.defaultBlockState()
                : Blocks.DIRT_PATH.defaultBlockState();
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
        TERRAIN_SUPPORT,
        TRAIL_PRIMARY,
        TRAIL_SHOULDER
    }

    private record Placement(int dx, int dy, int dz, BlockState state, PlacementRole role) {
        private Placement(int dx, int dy, int dz, BlockState state) {
            this(dx, dy, dz, state, PlacementRole.STRUCTURE);
        }

        private static Placement support(int dx, int dy, int dz, BlockState state) {
            return new Placement(dx, dy, dz, state, PlacementRole.TERRAIN_SUPPORT);
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

        private boolean isTrail() {
            return role == PlacementRole.TRAIL_PRIMARY || role == PlacementRole.TRAIL_SHOULDER;
        }
    }

    private record StructureSize(int width, int depth, int height) {
    }

    private record ProjectBounds(BlockPos minimum, BlockPos maximum) {
    }

    private record ProjectSiteSearch(
            BlockPos origin, VillageMaterializationPolicy.SiteAvailability availability) {
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
