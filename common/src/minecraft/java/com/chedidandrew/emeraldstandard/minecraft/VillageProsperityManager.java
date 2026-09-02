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
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.entity.npc.villager.Villager;
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

    private VillageProsperityManager() {
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

        ServerLevel level = server.overworld();
        long gameTime = level.getGameTime();
        if (gameTime % config.villageProsperityScanIntervalTicks() == 0L) {
            scanLoadedVillages(level, economy, config);
        }
        if (config.villageVisualProgressionEnabled()
                && gameTime % config.villageConstructionIntervalTicks() == 0L) {
            materializeDevelopment(level, economy, config, gameTime);
        }
    }

    public static void forgetPlayer(UUID playerId) {
        // Player-specific state is intentionally not stored here. Kept as an integration seam.
    }

    /** Records only an actual death event. Entity absence or chunk unload never counts as death. */
    public static void onVillagerDeath(
            Villager villager, DamageSource source, EconomyService economy) {
        UUID villageId = villageId(villager);
        if (villageId == null) {
            EconomyService.VillageSnapshot nearest = economy.nearestVillageSnapshot(
                    "minecraft:overworld", villager.blockPosition().asLong(), 128.0);
            villageId = nearest == null ? null : nearest.village().villageId;
        }
        if (villageId == null) {
            return;
        }

        Entity killer = responsibleEntity(source);
        UUID responsiblePlayer = killer instanceof ServerPlayer player ? player.getUUID() : null;
        VillageProsperityEngine.IncidentCause cause = classifyCause(killer);
        String profession = String.valueOf(villager.getVillagerData().profession());
        economy.recordVillagerDeath(
                villageId,
                villager.getUUID(),
                profession,
                villager.blockPosition().asLong(),
                cause,
                responsiblePlayer);
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
            List<Villager> villagers = level.getEntitiesOfClass(
                    Villager.class, area, villager -> villager.isAlive());
            BlockPos center = stableCenter(level, villagers, player.blockPosition());
            int bedCount = countBeds(level, center, 24, 6);
            List<Monster> hostiles = level.getEntitiesOfClass(
                    Monster.class,
                    new AABB(center).inflate(40.0, 16.0, 40.0),
                    LivingEntity::isAlive);
            boolean raidActive = hostiles.stream().anyMatch(VillageProsperityManager::isRaider);
            long regionKey = regionKey(center, config.villageRegionSize());
            Long anchor = economy.generatedBankAnchor(regionKey);

            List<EconomyService.ResidentObservation> residents = new ArrayList<>();
            for (Villager villager : villagers) {
                if (!villager.isAlive()) {
                    continue;
                }
                residents.add(new EconomyService.ResidentObservation(
                        villager.getUUID(),
                        String.valueOf(villager.getVillagerData().profession()),
                        villager.blockPosition().asLong()));
            }
            UUID preferredVillageId = preferredTaggedVillage(villagers);
            EconomyService.VillageSnapshot snapshot = economy.observeVillage(
                    preferredVillageId,
                    new EconomyService.VillageObservation(
                            "minecraft:overworld",
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
            if (anchor != null) {
                economy.associateBankRegionWithVillage(
                        regionKey, snapshot.village().villageId, anchor);
            }
        }
    }

    private static void materializeDevelopment(
            ServerLevel level,
            EconomyService economy,
            EmeraldConfig config,
            long gameTime) {
        int remainingBudget = config.villageConstructionBlocksPerTick();
        for (EconomyService.VillageSnapshot snapshot : economy.villageSnapshots()) {
            EconomyState.VillageRecord village = snapshot.village();
            if (!"minecraft:overworld".equals(village.dimensionKey)
                    || !hasNearbyPlayer(level, village.centerPos, config.villageDevelopmentRadius())) {
                continue;
            }
            spawnPendingSettler(level, economy, village, config, gameTime);
            if (remainingBudget <= 0) {
                continue;
            }
            EconomyState.VillageProject project = village.nextVisualProject();
            if (project == null) {
                continue;
            }
            if (project.originPos == 0L) {
                BlockPos origin = findProjectOrigin(level, village, project, config);
                if (origin == null) {
                    continue;
                }
                List<Placement> template = template(level, origin, project.type);
                if (!economy.reserveVillageProjectSite(
                        village.villageId, project.projectId, origin.asLong(), template.size())) {
                    continue;
                }
                project.originPos = origin.asLong();
                project.totalBlocks = template.size();
            }

            BlockPos origin = BlockPos.of(project.originPos);
            List<Placement> placements = template(level, origin, project.type);
            int index = Math.min(project.materializedBlocks, placements.size());
            int placedThisTick = 0;
            boolean blocked = false;
            while (index < placements.size() && remainingBudget > 0) {
                Placement placement = placements.get(index);
                BlockPos target = origin.offset(placement.dx, placement.dy, placement.dz);
                if (!level.hasChunk(Math.floorDiv(target.getX(), 16), Math.floorDiv(target.getZ(), 16))) {
                    break;
                }
                BlockState current = level.getBlockState(target);
                if (!current.equals(placement.state)) {
                    boolean safe = level.getBlockEntity(target) == null
                            && (current.isAir() || current.canBeReplaced());
                    if (!safe || !level.setBlock(target, placement.state, 3)) {
                        blocked = true;
                        break;
                    }
                    placedThisTick++;
                    remainingBudget--;
                }
                index++;
            }
            boolean complete = index >= placements.size();
            if (blocked && index == 0 && project.materializedBlocks == 0) {
                economy.releaseVillageProjectSite(village.villageId, project.projectId);
                continue;
            }
            economy.updateVillageProjectMaterialization(
                    village.villageId, project.projectId, index, complete, blocked);
            if (placedThisTick > 0) {
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
    }

    private static void spawnPendingSettler(
            ServerLevel level,
            EconomyService economy,
            EconomyState.VillageRecord village,
            EmeraldConfig config,
            long gameTime) {
        if (village.lifecycle == VillageProsperityEngine.Lifecycle.ABANDONED
                || village.lifecycle == VillageProsperityEngine.Lifecycle.EXTINCT
                || (village.population <= 0 && !config.villageAutomaticRecoveryEnabled())) {
            return;
        }
        long previous = LAST_SETTLER_TICK.getOrDefault(village.villageId, Long.MIN_VALUE / 2L);
        if (gameTime - previous < config.villageSettlerSpawnIntervalTicks()) {
            return;
        }
        BlockPos center = BlockPos.of(village.centerPos);
        AABB area = new AABB(center).inflate(48.0, 24.0, 48.0);
        int living = level.getEntitiesOfClass(
                        Villager.class, area, Villager::isAlive)
                .size();
        int targetPopulation = Math.min(
                VillageProsperityEngine.MAX_ABSTRACT_POPULATION,
                Math.max(village.population, village.observedPopulation + village.pendingSettlers));
        boolean reconciliationNeeded = living < targetPopulation;
        int usableBeds = countBeds(level, center, 24, 6);
        if ((village.pendingSettlers <= 0 && !reconciliationNeeded)
                || village.housingCapacity <= living
                || usableBeds <= living) {
            return;
        }
        List<Monster> threats = level.getEntitiesOfClass(
                Monster.class, new AABB(center).inflate(24.0, 12.0, 24.0), LivingEntity::isAlive);
        if (!threats.isEmpty()) {
            return;
        }
        int y = level.getHeight(
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, center.getX(), center.getZ());
        BlockPos spawn = new BlockPos(center.getX(), y, center.getZ());
        Villager settler = EntityTypes.VILLAGER.create(level, EntitySpawnReason.NATURAL);
        if (settler == null) {
            return;
        }
        settler.teleportTo(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5);
        settler.setPersistenceRequired();
        settler.setHomeTo(center, Math.max(8, config.villageDevelopmentRadius() / 3));
        assignVillage(settler, village.villageId);
        if (level.addFreshEntity(settler)) {
            LAST_SETTLER_TICK.put(village.villageId, gameTime);
            economy.consumePendingSettler(village.villageId);
        } else {
            settler.discard();
        }
    }

    private static BlockPos findProjectOrigin(
            ServerLevel level,
            EconomyState.VillageRecord village,
            EconomyState.VillageProject project,
            EmeraldConfig config) {
        BlockPos center = BlockPos.of(village.centerPos);
        int[][] offsets = {
                {36, 0}, {-36, 0}, {0, 36}, {0, -36},
                {42, 22}, {-42, 22}, {42, -22}, {-42, -22},
                {54, 0}, {-54, 0}, {0, 54}, {0, -54}
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
            boolean overlaps = village.projects.stream()
                    .filter(other -> other.originPos != 0L && other.projectId != project.projectId)
                    .map(other -> BlockPos.of(other.originPos))
                    .anyMatch(other -> other.distSqr(origin) < 16.0 * 16.0);
            if (!overlaps) {
                return origin;
            }
        }
        return null;
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

    private static List<Placement> template(
            ServerLevel level, BlockPos origin, VillageProsperityEngine.ProjectType type) {
        Palette palette = palette(level, origin);
        return switch (type) {
            case COTTAGE -> cottage(palette);
            case WAREHOUSE -> warehouse(palette);
            case MINE_ENTRANCE -> mineEntrance(palette);
        };
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
            case COTTAGE, MINE_ENTRANCE -> new StructureSize(7, 7, 5);
            case WAREHOUSE -> new StructureSize(9, 7, 5);
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

    private static boolean isNaturalProjectGround(BlockState state) {
        return state.is(BlockTags.DIRT)
                || state.is(Blocks.SAND)
                || state.is(Blocks.RED_SAND)
                || state.is(Blocks.STONE)
                || state.is(Blocks.ANDESITE)
                || state.is(Blocks.DIORITE)
                || state.is(Blocks.GRANITE)
                || state.is(Blocks.SNOW)
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

    private static boolean hasNearbyPlayer(ServerLevel level, long packedCenter, int radius) {
        BlockPos center = BlockPos.of(packedCenter);
        double radiusSquared = radius * (double) radius;
        return level.getServer().getPlayerList().getPlayers().stream()
                .anyMatch(player -> player.level() == level
                        && player.distanceToSqr(
                                        center.getX() + 0.5,
                                        center.getY() + 0.5,
                                        center.getZ() + 0.5)
                                <= radiusSquared);
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

    private static long regionKey(BlockPos position, int regionSize) {
        int regionX = Math.floorDiv(position.getX(), regionSize);
        int regionZ = Math.floorDiv(position.getZ(), regionSize);
        return ((long) regionX << 32) ^ (regionZ & 0xFFFFFFFFL);
    }

    private record Placement(int dx, int dy, int dz, BlockState state) {
    }

    private record StructureSize(int width, int depth, int height) {
    }

    private record Palette(Block floor, Block wall, Block corner, Block roof) {
    }
}
