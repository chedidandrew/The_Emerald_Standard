package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.EconomyService;
import com.chedidandrew.emeraldstandard.core.EconomyState;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** Read-only district food census. Empty/non-crop sections are skipped; chunks are never loaded. */
final class VillageFoodEnvironment {
    static final int CELLS_PER_TICK = 4_096;
    static final int STARTER_RADIUS = 64, FARM_MARGIN = 24;
    private static final LinkedHashMap<UUID, Scan> PENDING = new LinkedHashMap<>();
    private static ServerLevel cachedLevel;
    private static long cachedTick;
    private static List<EconomyState.VillageRecord> cachedVillages = List.of();

    private VillageFoodEnvironment() { }

    static void reset() {
        PENDING.clear(); cachedLevel = null; cachedVillages = List.of();
    }

    static void schedule(ServerLevel level, EconomyService economy, EconomyState.VillageRecord village) {
        if (economy.isCatchingUp() || !economy.villageProsperitySimulationEnabled()
                || PENDING.containsKey(village.villageId) || PENDING.size() >= 128) return;
        if (cachedLevel != level || cachedTick != level.getGameTime()) {
            cachedLevel = level; cachedTick = level.getGameTime();
            cachedVillages = economy.villageSnapshots().stream().map(EconomyService.VillageSnapshot::village)
                    .filter(v -> v.dimensionKey.equals(village.dimensionKey)).toList();
        }
        PENDING.put(village.villageId, new Scan(level, village, cachedVillages));
    }

    static void tick(MinecraftServer server, EconomyService economy) {
        if (PENDING.isEmpty() || economy.isCatchingUp()) return;
        if (!economy.villageProsperitySimulationEnabled()) { reset(); return; }
        var first = PENDING.entrySet().iterator().next();
        UUID id = first.getKey(); Scan scan = first.getValue(); PENDING.remove(id);
        if (scan.level.getServer() != server || economy.villageSnapshot(id) == null) return;
        if (!scan.advance(CELLS_PER_TICK)) { PENDING.put(id, scan); return; }
        if (scan.loadedColumns >= 64)
            economy.observeVillageFoodSources(id, Math.min(1_000_000, scan.crops), scan.livestock());
    }

    /** The complete developed rectangle, including intervening land and a farm/pen margin. */
    static Coverage coverage(EconomyState.VillageRecord village) {
        BlockPos center = BlockPos.of(village.centerPos);
        int minX = center.getX() - STARTER_RADIUS, maxX = center.getX() + STARTER_RADIUS;
        int minZ = center.getZ() - STARTER_RADIUS, maxZ = center.getZ() + STARTER_RADIUS;
        for (var project : village.projects) {
            if (project.originPos == 0 || (project.materializedBlocks == 0 && !project.materializedComplete)) continue;
            BlockPos low = BlockPos.of(project.boundsMinPos), high = BlockPos.of(project.boundsMaxPos);
            if (project.boundsMinPos == 0 && project.boundsMaxPos == 0) {
                // Pre-bounds legacy saves must not accidentally claim the world-origin corridor.
                low = BlockPos.of(project.originPos).offset(-24, 0, -24);
                high = BlockPos.of(project.originPos).offset(24, 0, 24);
            }
            minX = Math.min(minX, low.getX() - FARM_MARGIN); maxX = Math.max(maxX, high.getX() + FARM_MARGIN);
            minZ = Math.min(minZ, low.getZ() - FARM_MARGIN); maxZ = Math.max(maxZ, high.getZ() + FARM_MARGIN);
        }
        if (village.bankAnchorPos != 0) {
            BlockPos bank = BlockPos.of(village.bankAnchorPos);
            minX = Math.min(minX, bank.getX() - 8 - FARM_MARGIN);
            maxX = Math.max(maxX, bank.getX() + 8 + FARM_MARGIN);
            minZ = Math.min(minZ, bank.getZ() - 14 - FARM_MARGIN);
            maxZ = Math.max(maxZ, bank.getZ() + 3 + FARM_MARGIN);
        }
        return new Coverage(village.villageId, center, minX, maxX, minZ, maxZ);
    }

    record Coverage(UUID id, BlockPos center, int minX, int maxX, int minZ, int maxZ) {
        boolean contains(BlockPos pos) {
            return pos.getX() >= minX && pos.getX() <= maxX && pos.getZ() >= minZ && pos.getZ() <= maxZ;
        }
        boolean overlaps(Coverage other) {
            return minX <= other.maxX && maxX >= other.minX && minZ <= other.maxZ && maxZ >= other.minZ;
        }
        double distance(BlockPos pos) {
            double dx = (double) pos.getX() - center.getX(), dz = (double) pos.getZ() - center.getZ();
            return dx * dx + dz * dz;
        }
    }

    static double cropUnits(BlockState state) {
        if (state.is(Blocks.WHEAT) || state.is(Blocks.CARROTS) || state.is(Blocks.POTATOES)
                || state.is(Blocks.BEETROOTS)) {
            CropBlock crop = (CropBlock) state.getBlock();
            return 0.25 + 0.75 * crop.getAge(state) / crop.getMaxAge();
        }
        if (state.is(Blocks.MELON) || state.is(Blocks.PUMPKIN)) return 1.0;
        if (state.is(Blocks.MELON_STEM) || state.is(Blocks.PUMPKIN_STEM))
            return 0.05 + 0.20 * state.getValue(StemBlock.AGE) / 7.0;
        if (state.is(Blocks.ATTACHED_MELON_STEM) || state.is(Blocks.ATTACHED_PUMPKIN_STEM)) return 0.25;
        if (state.is(Blocks.SWEET_BERRY_BUSH)) return 0.5 * state.getValue(SweetBerryBushBlock.AGE) / 3.0;
        if (state.is(Blocks.COCOA)) return 0.5 * state.getValue(CocoaBlock.AGE) / 2.0;
        if (state.is(Blocks.SUGAR_CANE)) return 0.15;
        return 0.0;
    }

    static double animalUnits(Animal animal) {
        if (!animal.isAlive()) return 0;
        var type = animal.getType();
        if (type != EntityTypes.COW && type != EntityTypes.MOOSHROOM && type != EntityTypes.PIG
                && type != EntityTypes.SHEEP && type != EntityTypes.CHICKEN && type != EntityTypes.RABBIT) return 0;
        return animal.isBaby() ? 0.25 : 1.0;
    }

    static final class Scan {
        final ServerLevel level;
        final Coverage territory;
        final List<Coverage> neighbors;
        final int minChunkX, minChunkZ, chunksWide, chunksDeep, sectionCount;
        long chunkCursor;
        int sectionCursor, cellCursor, loadedColumns;
        double crops;

        Scan(ServerLevel level, EconomyState.VillageRecord village, List<EconomyState.VillageRecord> neighbors) {
            this.level = level;
            territory = coverage(village);
            this.neighbors = neighbors.stream().map(VillageFoodEnvironment::coverage)
                    .filter(territory::overlaps).toList();
            minChunkX = Math.floorDiv(territory.minX, 16); minChunkZ = Math.floorDiv(territory.minZ, 16);
            chunksWide = Math.floorDiv(territory.maxX, 16) - minChunkX + 1;
            chunksDeep = Math.floorDiv(territory.maxZ, 16) - minChunkZ + 1;
            sectionCount = (level.getMaxY() - level.getMinY() + 16) / 16;
        }

        boolean owns(BlockPos position) {
            if (!territory.contains(position)) return false;
            UUID owner = territory.id;
            double nearest = territory.distance(position);
            for (Coverage other : neighbors) {
                if (!other.contains(position)) continue;
                double distance = other.distance(position);
                if (distance < nearest || (distance == nearest && other.id.compareTo(owner) < 0)) {
                    nearest = distance; owner = other.id;
                }
            }
            return territory.id.equals(owner);
        }

        boolean advance(int budget) {
            for (int inspected = 0; chunkCursor < (long) chunksWide * chunksDeep && inspected < budget; inspected++) {
                int cx = minChunkX + (int) (chunkCursor % chunksWide);
                int cz = minChunkZ + (int) (chunkCursor / chunksWide);
                if (!level.hasChunk(cx, cz)) { nextChunk(); continue; }
                if (sectionCursor == 0 && cellCursor == 0) loadedColumns += 256;
                var chunk = level.getChunk(cx, cz); // Guarded above, on the server thread.
                if (sectionCursor >= chunk.getSections().length) { nextChunk(); continue; }
                var section = chunk.getSection(sectionCursor);
                if (cellCursor == 0 && (section.hasOnlyAir() || !section.maybeHas(s -> cropUnits(s) > 0))) {
                    sectionCursor++;
                } else {
                    int x = cellCursor & 15, z = (cellCursor >> 4) & 15, y = cellCursor >> 8;
                    double units = cropUnits(section.getBlockState(x, y, z));
                    if (units > 0 && owns(new BlockPos(cx * 16 + x,
                            level.getMinY() + sectionCursor * 16 + y, cz * 16 + z))) crops += units;
                    if (++cellCursor == 4096) { cellCursor = 0; sectionCursor++; }
                }
                if (sectionCursor >= sectionCount) nextChunk();
            }
            return chunkCursor >= (long) chunksWide * chunksDeep;
        }

        private void nextChunk() { chunkCursor++; sectionCursor = cellCursor = 0; }

        double livestock() {
            double units = 0;
            for (Animal animal : level.getEntitiesOfClass(Animal.class,
                    new AABB(territory.minX, level.getMinY(), territory.minZ,
                            territory.maxX + 1.0, level.getMaxY() + 1.0, territory.maxZ + 1.0), Animal::isAlive)) {
                if (owns(animal.blockPosition())) units += animalUnits(animal);
            }
            return Math.min(1_000_000, units);
        }
    }
}
