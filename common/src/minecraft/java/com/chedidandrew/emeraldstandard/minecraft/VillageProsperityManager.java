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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
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
            int remainingBudget = config.villageConstructionBlocksPerTick();
            int firstLevel = Math.floorMod(
                    gameTime / config.villageConstructionIntervalTicks(), levels.size());
            for (int step = 0; step < levels.size(); step++) {
                ServerLevel level = levels.get((firstLevel + step) % levels.size());
                remainingBudget = materializeDevelopment(
                        level, economy, config, gameTime, remainingBudget);
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

    private static int materializeDevelopment(
            ServerLevel level,
            EconomyService economy,
            EmeraldConfig config,
            long gameTime,
            int remainingBudget) {
        String dimensionKey = dimensionKey(level);
        List<Long> playerPositions = level.players().stream()
                .map(player -> player.blockPosition().asLong())
                .toList();
        if (playerPositions.isEmpty()) {
            return remainingBudget;
        }
        List<EconomyService.VillageSnapshot> snapshots = economy.villageSnapshotsNear(
                dimensionKey, playerPositions, config.villageDevelopmentRadius());
        int firstVillage = snapshots.isEmpty()
                ? 0
                : Math.floorMod(
                        gameTime / config.villageConstructionIntervalTicks()
                                + dimensionKey.hashCode(),
                        snapshots.size());
        for (int step = 0; step < snapshots.size(); step++) {
            EconomyService.VillageSnapshot snapshot =
                    snapshots.get((firstVillage + step) % snapshots.size());
            EconomyState.VillageRecord village = snapshot.village();
            spawnPendingSettler(level, economy, village, config, gameTime);
            reconcileOneMaterializedProject(level, economy, village, config, gameTime);
            if (remainingBudget <= 0) {
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
                BlockPos origin = findProjectOrigin(level, village, project, config);
                if (origin == null) {
                    DebugFlightRecorder.recordConstruction(
                            level,
                            village.villageId,
                            project.projectId,
                            project.type.name(),
                            "site_unavailable",
                            BlockPos.of(village.centerPos),
                            project.materializedBlocks,
                            project.totalBlocks,
                            "No loaded, safe, unoccupied project lot was available");
                    economy.deferVillageProjectMaterialization(
                            village.villageId, project.projectId, gameTime);
                    continue;
                }
                List<Placement> template = template(level, origin, project.type);
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
            List<Placement> placements = template(level, origin, project.type);
            int index = Math.min(project.materializedBlocks, placements.size());
            int placedThisTick = 0;
            boolean blocked = false;
            boolean unloaded = false;
            while (index < placements.size() && remainingBudget > 0) {
                Placement placement = placements.get(index);
                BlockPos target = origin.offset(placement.dx, placement.dy, placement.dz);
                if (!level.hasChunk(Math.floorDiv(target.getX(), 16), Math.floorDiv(target.getZ(), 16))) {
                    // Persist the same bounded backoff used for unsafe placements instead of
                    // probing an unloaded boundary on every village pulse.
                    blocked = true;
                    unloaded = true;
                    break;
                }
                BlockState current = level.getBlockState(target);
                if (!VillageDevelopmentProtection.mayPlace(
                        level,
                        village.villageId,
                        project.projectId,
                        target,
                        current,
                        placement.state)) {
                    blocked = true;
                    break;
                }
                if (!current.is(placement.state.getBlock())) {
                    boolean safe = level.getBlockEntity(target) == null
                            && (current.isAir() || current.canBeReplaced());
                    if (!safe
                            || !level.setBlock(target, placement.state, 3)
                            || !level.getBlockState(target).is(placement.state.getBlock())) {
                        blocked = true;
                        break;
                    }
                    placedThisTick++;
                    remainingBudget--;
                }
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
        return remainingBudget;
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
        long previous = LAST_SETTLER_TICK.getOrDefault(village.villageId, Long.MIN_VALUE / 2L);
        if (gameTime - previous < config.villageSettlerSpawnIntervalTicks()) {
            return;
        }
        BlockPos center = BlockPos.of(village.centerPos);
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
        settler.setHomeTo(center, Math.max(8, config.villageDevelopmentRadius() / 3));
        assignVillage(settler, village.villageId);
        if (!level.noCollision(settler)) {
            settler.discard();
            return;
        }
        if (level.addFreshEntity(settler)) {
            LAST_SETTLER_TICK.put(village.villageId, gameTime);
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
     * block immediately removes that project's economic authority, then the normal construction
     * queue may repair only air/replaceable positions under the existing protection hooks. Solid
     * player blocks, block entities, and unloaded chunks are never overwritten or force-loaded.
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
        long interval = Math.max(1L, config.villageConstructionIntervalTicks());
        long auditPulses = Math.max(1L, 2_400L / interval);
        long pulse = gameTime / interval;
        if (Math.floorMod(pulse + village.villageId.hashCode(), auditPulses) != 0L) {
            return;
        }

        List<EconomyState.VillageProject> candidates = village.projects.stream()
                .filter(project -> project.economicComplete
                        && project.materializedComplete
                        && !project.abstractOnly
                        && project.originPos != 0L)
                .toList();
        if (candidates.isEmpty()) {
            return;
        }
        EconomyState.VillageProject project = candidates.get(Math.floorMod(
                (int) (pulse + village.villageId.hashCode()), candidates.size()));
        BlockPos origin = BlockPos.of(project.originPos);
        List<Placement> expected = template(level, origin, project.type);
        int verifiedPrefix = expected.size();
        BlockPos mismatch = null;
        for (int index = 0; index < expected.size(); index++) {
            Placement placement = expected.get(index);
            BlockPos target = origin.offset(placement.dx, placement.dy, placement.dz);
            if (!level.hasChunk(
                    Math.floorDiv(target.getX(), 16), Math.floorDiv(target.getZ(), 16))) {
                return;
            }
            if (!level.getBlockState(target).is(placement.state.getBlock())) {
                verifiedPrefix = index;
                mismatch = target;
                break;
            }
        }
        if (mismatch == null) {
            return;
        }
        if (!economy.reconcileVillageProjectMaterialization(
                village.villageId,
                project.projectId,
                verifiedPrefix,
                expected.size(),
                false)) {
            return;
        }
        // This object is a snapshot. Mirror the persisted demotion so the repair queue can act in
        // the same pulse instead of waiting for another proximity snapshot.
        project.materializedBlocks = verifiedPrefix;
        project.totalBlocks = expected.size();
        project.materializedComplete = false;
        project.blocked = false;
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
                expected.size(),
                "A missing authored block suspended project benefits until safe repair completes");
    }

    private static BlockPos findProjectOrigin(
            ServerLevel level,
            EconomyState.VillageRecord village,
            EconomyState.VillageProject project,
            EmeraldConfig config) {
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
        for (int step = 0; step < offsets.length; step++) {
            int[] offset = offsets[(start + step) % offsets.length];
            int centerX = center.getX() + offset[0];
            int centerZ = center.getZ() + offset[1];
            BlockPos origin = safeOrigin(level, centerX, centerZ, size);
            if (origin == null) {
                continue;
            }
            if (village.bankAnchorPos != 0L
                    && origin.distSqr(BlockPos.of(village.bankAnchorPos)) < 18.0 * 18.0) {
                continue;
            }
            List<Placement> planned = template(level, origin, project.type);
            if (!mayUseProjectSite(level, village.villageId, project.projectId, origin, planned)) {
                continue;
            }
            ProjectBounds candidateBounds = bounds(origin, planned);
            boolean overlaps = village.projects.stream()
                    .filter(other -> other.originPos != 0L && other.projectId != project.projectId)
                    .map(other -> projectBounds(level, other))
                    .anyMatch(other -> overlaps(candidateBounds, other, 2));
            if (!overlaps) {
                return origin;
            }
        }
        return null;
    }

    private static boolean mayUseProjectSite(
            ServerLevel level,
            UUID villageId,
            long projectId,
            BlockPos origin,
            List<Placement> placements) {
        for (Placement placement : placements) {
            BlockPos target = origin.offset(placement.dx, placement.dy, placement.dz);
            if (!level.hasChunk(
                    Math.floorDiv(target.getX(), 16), Math.floorDiv(target.getZ(), 16))) {
                return false;
            }
            BlockState existing = level.getBlockState(target);
            if (level.getBlockEntity(target) != null
                    || (!existing.isAir() && !existing.canBeReplaced())
                    || !VillageDevelopmentProtection.mayPlace(
                            level,
                            villageId,
                            projectId,
                            target,
                            existing,
                            placement.state)) {
                return false;
            }
        }
        return true;
    }

    private static BlockPos safeOrigin(
            ServerLevel level, int centerX, int centerZ, StructureSize size) {
        int minimum = Integer.MAX_VALUE;
        int maximum = Integer.MIN_VALUE;
        for (int x = centerX - size.width / 2 - 1;
                x <= centerX + size.width / 2 + 1;
                x++) {
            for (int z = centerZ - size.depth / 2 - 1;
                    z <= centerZ + size.depth / 2 + 1;
                    z++) {
                if (!level.hasChunk(Math.floorDiv(x, 16), Math.floorDiv(z, 16))) {
                    return null;
                }
                int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                BlockPos ground = new BlockPos(x, surface - 1, z);
                BlockState groundState = level.getBlockState(ground);
                if (groundState.isAir()
                        || !level.getFluidState(ground).isEmpty()
                        || !isNaturalProjectGround(groundState)) {
                    return null;
                }
                minimum = Math.min(minimum, surface);
                maximum = Math.max(maximum, surface);
            }
        }
        if (maximum != minimum) {
            return null;
        }
        // The floor is placed in air directly above the natural surface. We never replace the
        // ground layer, which keeps paths, farmland, player floors, and protected blocks intact.
        BlockPos origin = new BlockPos(
                centerX - size.width / 2,
                maximum,
                centerZ - size.depth / 2);
        for (int x = -1; x <= size.width; x++) {
            for (int z = -1; z <= size.depth; z++) {
                for (int y = 0; y <= size.height; y++) {
                    BlockPos target = origin.offset(x, y, z);
                    BlockState state = level.getBlockState(target);
                    if (level.getBlockEntity(target) != null
                            || (!state.isAir() && !state.canBeReplaced())) {
                        return null;
                    }
                }
            }
        }
        return origin;
    }

    private static ProjectBounds bounds(BlockPos origin, List<Placement> placements) {
        int minimumX = Integer.MAX_VALUE;
        int minimumY = Integer.MAX_VALUE;
        int minimumZ = Integer.MAX_VALUE;
        int maximumX = Integer.MIN_VALUE;
        int maximumY = Integer.MIN_VALUE;
        int maximumZ = Integer.MIN_VALUE;
        for (Placement placement : placements) {
            BlockPos position = origin.offset(placement.dx, placement.dy, placement.dz);
            minimumX = Math.min(minimumX, position.getX());
            minimumY = Math.min(minimumY, position.getY());
            minimumZ = Math.min(minimumZ, position.getZ());
            maximumX = Math.max(maximumX, position.getX());
            maximumY = Math.max(maximumY, position.getY());
            maximumZ = Math.max(maximumZ, position.getZ());
        }
        return new ProjectBounds(
                new BlockPos(minimumX, minimumY, minimumZ),
                new BlockPos(maximumX, maximumY, maximumZ));
    }

    private static ProjectBounds projectBounds(
            ServerLevel level, EconomyState.VillageProject project) {
        if (project.boundsMinPos != 0L || project.boundsMaxPos != 0L) {
            return new ProjectBounds(
                    BlockPos.of(project.boundsMinPos), BlockPos.of(project.boundsMaxPos));
        }
        BlockPos origin = BlockPos.of(project.originPos);
        return bounds(origin, template(level, origin, project.type));
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
            for (Placement placement : placements) {
                BlockPos relative = new BlockPos(placement.dx, placement.dy, placement.dz);
                if (!occupied.add(relative)) {
                    throw new IllegalStateException(
                            "Duplicate physical placement for " + type + " at " + relative);
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
            }
        }
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
        return placements;
    }

    private static List<Placement> house(Palette palette) {
        List<Placement> placements = simpleBuilding(palette, 9, 9, 4);
        addBeds(placements, 9, 9, 6);
        placements.add(new Placement(2, 1, 6, Blocks.BOOKSHELF.defaultBlockState()));
        placements.add(new Placement(6, 1, 6, Blocks.CHEST.defaultBlockState()));
        return placements;
    }

    private static List<Placement> inn(Palette palette) {
        List<Placement> placements = simpleBuilding(palette, 11, 9, 4);
        addBeds(placements, 11, 9, 8);
        for (int x = 2; x <= 8; x += 2) {
            placements.add(new Placement(x, 1, 7, Blocks.CHEST.defaultBlockState()));
        }
        placements.add(new Placement(5, 1, 4, Blocks.CRAFTING_TABLE.defaultBlockState()));
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
        return placements;
    }

    private static List<Placement> smithy(Palette palette) {
        List<Placement> placements = simpleBuilding(palette, 9, 7, 4);
        placements.add(new Placement(2, 1, 4, Blocks.ANVIL.defaultBlockState()));
        placements.add(new Placement(4, 1, 4, Blocks.BLAST_FURNACE.defaultBlockState()));
        placements.add(new Placement(6, 1, 4, Blocks.SMITHING_TABLE.defaultBlockState()));
        placements.add(new Placement(7, 1, 2, Blocks.CHEST.defaultBlockState()));
        return placements;
    }

    private static List<Placement> granary(Palette palette) {
        List<Placement> placements = simpleBuilding(palette, 9, 7, 4);
        for (int x = 1; x <= 7; x += 2) {
            placements.add(new Placement(x, 1, 4, Blocks.CHEST.defaultBlockState()));
            placements.add(new Placement(x, 2, 4, Blocks.HAY_BLOCK.defaultBlockState()));
        }
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
        return placements;
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
                    Blocks.SANDSTONE_SLAB);
        }
        if (biome.is(BiomeTags.HAS_VILLAGE_SAVANNA)) {
            return new Palette(
                    Blocks.STONE_BRICKS,
                    Blocks.ACACIA_PLANKS,
                    Blocks.STRIPPED_ACACIA_LOG,
                    Blocks.ACACIA_SLAB);
        }
        if (biome.is(BiomeTags.HAS_VILLAGE_SNOWY)
                || biome.is(BiomeTags.HAS_VILLAGE_TAIGA)) {
            return new Palette(
                    Blocks.STONE_BRICKS,
                    Blocks.SPRUCE_PLANKS,
                    Blocks.STRIPPED_SPRUCE_LOG,
                    Blocks.SPRUCE_SLAB);
        }
        return new Palette(
                Blocks.STONE_BRICKS,
                Blocks.OAK_PLANKS,
                Blocks.STRIPPED_OAK_LOG,
                Blocks.DARK_OAK_SLAB);
    }

    private static StructureSize size(VillageProsperityEngine.ProjectType type) {
        return switch (type) {
            case COTTAGE, MINE_ENTRANCE, GUARD_POST -> new StructureSize(7, 7, 6);
            case HOUSE -> new StructureSize(9, 9, 6);
            case INN -> new StructureSize(11, 9, 6);
            case WAREHOUSE, SMITHY, GRANARY -> new StructureSize(9, 7, 6);
            case MARKET_SQUARE -> new StructureSize(11, 11, 4);
            case EXCHANGE_HALL -> new StructureSize(13, 9, 7);
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

    private record Placement(int dx, int dy, int dz, BlockState state) {
    }

    private record StructureSize(int width, int depth, int height) {
    }

    private record ProjectBounds(BlockPos minimum, BlockPos maximum) {
    }

    private record Palette(Block floor, Block wall, Block corner, Block roof) {
    }
}
