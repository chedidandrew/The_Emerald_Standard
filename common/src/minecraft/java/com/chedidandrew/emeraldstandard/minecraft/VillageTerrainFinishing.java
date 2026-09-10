package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.SitePreparationPlan;
import java.util.*;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;

/** Optional, frozen terrain finish for fresh sites only. Never grants repair/replay authority. */
final class VillageTerrainFinishing {
    private VillageTerrainFinishing() { }

    static boolean satisfied(ServerLevel level, BlockState current, String after) {
        if (BlockStateParser.serialize(current).equals(after)) return true;
        BlockState expected;
        try { expected = state(level, after); }
        catch (IllegalArgumentException malformed) { return false; }
        if (!current.is(expected.getBlock()) || !current.getFluidState().isEmpty()) return false;
        if (current.getBlock() instanceof WallBlock) return true; // Neighbor connections are not edits.
        if (current.getBlock() instanceof LeavesBlock) return current.getValue(LeavesBlock.PERSISTENT)
                && expected.getValue(LeavesBlock.PERSISTENT); // Natural distance updates are expected.
        if (current.is(Blocks.GRASS_BLOCK)) return true; // Weather can change the snowy property.
        if (!(current.getBlock() instanceof StairBlock)) return false;
        return current.is(expected.getBlock()) && current.getValue(StairBlock.FACING) == expected.getValue(StairBlock.FACING)
                && current.getValue(StairBlock.HALF) == expected.getValue(StairBlock.HALF)
                && current.getFluidState().isEmpty();
    }

    static boolean unchanged(ServerLevel level, BlockState current, String before) {
        if (VillageSitePreparation.matchesRemoval(current, before)) return true;
        if (!current.isAir()) return false;
        try { return VillageSitePreparation.vegetation(state(level, before)); }
        catch (IllegalArgumentException malformed) { return false; }
    }

    static BlockState state(ServerLevel level, String serialized) {
        try {
            return BlockStateParser.parseForBlock(level.registryAccess().lookupOrThrow(Registries.BLOCK),
                    serialized, false).blockState();
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException ex) {
            throw new IllegalArgumentException("Invalid frozen terrain state", ex);
        }
    }

    static SitePreparationPlan finish(ServerLevel level, SitePreparationPlan base, BlockPos origin,
            int minX, int maxX, int minZ, int maxZ, Set<BlockPos> occupied,
            List<BlockPos> route, int arrivalHeight, BlockState masonry, BlockState stairs,
            UUID village, long project) {
        Map<Long, SitePreparationPlan.Cell> cells = new LinkedHashMap<>();
        for (var cell : base.cells()) cells.put(cell.position(), cell);
        // Stay in the already surveyed lot's outside border. Do not add a new reservation footprint.
        var survey = new VillageSitePreparation.Survey(level);
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) {
            if (x != minX && x != maxX && z != minZ && z != maxZ) continue;
            BlockPos column = origin.offset(x, 0, z);
            if (route.stream().anyMatch(p -> p.getX() == column.getX() && p.getZ() == column.getZ())) continue;
            Integer surface = survey.surface(column.getX(), column.getZ());
            if (surface == null || surface <= origin.getY() || surface > origin.getY() + 4) continue;
            Map<Long, SitePreparationPlan.Cell> wall = new LinkedHashMap<>();
            boolean safe = true;
            for (int y = origin.getY(); y < surface; y++) {
                BlockPos pos = new BlockPos(column.getX(), y, column.getZ());
                if (occupied.contains(pos) || !survey.excavatable(pos, origin.getY())
                        || !approve(level, pos, masonry, village, project)) {
                    safe = false; break;
                }
                wall.put(pos.asLong(), cell(level, pos, masonry));
            }
            if (safe) cells.putAll(wall); // Full courses seated on natural ground, top follows the hill.
        }
        Map<Long, SitePreparationPlan.Cell> road = road(level, route, arrivalHeight, occupied,
                masonry, stairs, village, project);
        // An obstructed optional connection must not veto an otherwise usable building lot.
        if (road != null && cells.size() + road.size() <= SitePreparationPlan.MAX_CELLS) {
            cells.putAll(road);
            var pocket = pocket(level, route, occupied, cells.keySet(), road, village, project);
            if (cells.size() + pocket.size() <= SitePreparationPlan.MAX_CELLS) cells.putAll(pocket);
        }
        return new SitePreparationPlan(cells.values().stream()
                .sorted(Comparator.<SitePreparationPlan.Cell>comparingInt(c -> c.after().equals("minecraft:air") ? 0 : 1)
                        .thenComparingInt(c -> c.after().equals("minecraft:air")
                                ? -BlockPos.of(c.position()).getY() : BlockPos.of(c.position()).getY())
                        .thenComparingLong(SitePreparationPlan.Cell::position)).toList());
    }

    /** All-or-nothing connection preflight; no partial road ends caused by our own grading. */
    static Map<Long, SitePreparationPlan.Cell> road(ServerLevel level, List<BlockPos> route,
            int arrivalHeight, Set<BlockPos> occupied, BlockState masonry, BlockState stairs,
            UUID village, long project) {
        if (route.isEmpty() || route.size() > 256) return null;
        var survey = new VillageSitePreparation.Survey(level);
        List<Integer> ground = new ArrayList<>();
        boolean joinsRoad = false;
        for (int i = 0; i < route.size(); i++) {
            BlockPos pos = route.get(i);
            if (i > 0 && Math.abs(pos.getX() - route.get(i - 1).getX())
                    + Math.abs(pos.getZ() - route.get(i - 1).getZ()) != 1) return null;
            if (!survey.loaded(pos)) return null;
            int top = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    pos.getX(), pos.getZ());
            BlockState existing = level.getBlockState(new BlockPos(pos.getX(), top - 1, pos.getZ()));
            if (existing.is(Blocks.DIRT_PATH) || existing.is(Blocks.GRAVEL) || existing.is(Blocks.COARSE_DIRT)) {
                ground.add(top); route = route.subList(0, i + 1); joinsRoad = true; break;
            }
            Integer surface = survey.surface(pos.getX(), pos.getZ());
            if (surface == null) return null;
            ground.add(surface);
        }
        var grade = TerrainRoadPlan.grade(ground, arrivalHeight);
        if (grade.isEmpty()) return null;
        List<Integer> heights = grade.orElseThrow();
        Map<Long, SitePreparationPlan.Cell> result = new LinkedHashMap<>();
        for (int i = 0; i < route.size(); i++) {
            int y = heights.get(i), groundY = ground.get(i);
            BlockPos pos = new BlockPos(route.get(i).getX(), y, route.get(i).getZ());
            if (joinsRoad && i == route.size() - 1) {
                // Adopt the existing road, including its material; never excavate shared infrastructure.
                if (y != groundY || !level.getBlockState(pos).isAir() || !level.getBlockState(pos.above()).isAir())
                    return null;
                continue;
            }
            BlockPos uphill = i + 1 < route.size() && heights.get(i + 1) > y ? route.get(i + 1)
                    : i > 0 && heights.get(i - 1) > y ? route.get(i - 1) : null;
            Map<BlockPos, BlockState> desired = new LinkedHashMap<>();
            Set<BlockPos> clearance = new HashSet<>();
            for (int h = Math.min(y - 1, groundY); h <= Math.max(y + 2, groundY - 1); h++)
                clearance.add(new BlockPos(pos.getX(), h, pos.getZ()));
            var cleared = survey.freeze(clearance, Math.min(y - 1, groundY), village, project);
            if (cleared == null) return null;
            for (var cell : cleared.cells()) result.putIfAbsent(cell.position(), cell);
            // Gravel is confined to flat sound ground. Raised landings use non-falling masonry.
            for (int h = groundY; h < y; h++) desired.put(new BlockPos(pos.getX(), h, pos.getZ()), masonry);
            desired.put(pos.below(), y > groundY ? masonry : Blocks.GRAVEL.defaultBlockState());
            desired.put(pos, Blocks.AIR.defaultBlockState());
            desired.put(pos.above(), Blocks.AIR.defaultBlockState());
            desired.put(pos.above(2), Blocks.AIR.defaultBlockState());
            if (uphill != null) {
                Direction facing = uphill.getX() > pos.getX() ? Direction.EAST : uphill.getX() < pos.getX()
                        ? Direction.WEST : uphill.getZ() > pos.getZ() ? Direction.SOUTH : Direction.NORTH;
                desired.put(pos, stairs.setValue(StairBlock.FACING, facing));
            }
            for (var entry : desired.entrySet()) {
                if (occupied.contains(entry.getKey()) || !approve(level, entry.getKey(), entry.getValue(), village, project))
                    return null;
                result.put(entry.getKey().asLong(), cell(level, entry.getKey(), entry.getValue()));
            }
        }
        if (result.size() > SitePreparationPlan.MAX_CELLS) return null;
        // Connected tree clearance must not reach another authored cell either.
        if (result.keySet().stream().map(BlockPos::of).anyMatch(occupied::contains)) return null;
        return result;
    }

    /** A small square, garden or meeting nook beside a road. Optional failure never blocks growth. */
    static Map<Long, SitePreparationPlan.Cell> pocket(ServerLevel level, List<BlockPos> route,
            Set<BlockPos> occupied, Set<Long> reserved, Map<Long, SitePreparationPlan.Cell> road, UUID village, long project) {
        if (route.size() < 7) return Map.of();
        int middle = route.size() / 2;
        BlockPos a = route.get(middle), b = route.get(middle + 1);
        int dx = b.getX() - a.getX(), dz = b.getZ() - a.getZ();
        if (Math.abs(dx) + Math.abs(dz) != 1) return Map.of();
        var survey = new VillageSitePreparation.Survey(level);
        int style = VillageNeighborhoodPlan.pocketStyle(village, project);
        for (int side : new int[]{1, -1}) {
            int sx = -dz * side, sz = dx * side;
            BlockPos center = a.offset(sx * 3, 0, sz * 3);
            Integer height = survey.surface(center.getX(), center.getZ());
            if (height == null) continue;
            BlockPos junction = new BlockPos(a.getX(), height, a.getZ());
            var roadFloor = road.get(junction.below().asLong());
            var roadFeet = road.get(junction.asLong());
            if (roadFloor == null || roadFeet == null || !roadFeet.after().equals("minecraft:air")
                    || !state(level, roadFloor.after()).isFaceSturdy(level, junction.below(), Direction.UP)) continue;
            Set<BlockPos> volume = new HashSet<>();
            Map<BlockPos, BlockState> desired = new LinkedHashMap<>();
            boolean safe = true;
            for (int u = -2; u <= 2 && safe; u++) for (int v = -2; v <= 2 && safe; v++) {
                BlockPos feet = new BlockPos(center.getX() + dx * u + sx * v, height,
                        center.getZ() + dz * u + sz * v);
                Integer surface = survey.surface(feet.getX(), feet.getZ());
                if (!Objects.equals(surface, height)) { safe = false; break; }
                for (int h = -1; h <= 2; h++) {
                    BlockPos pos = feet.above(h);
                    if (occupied.contains(pos) || reserved.contains(pos.asLong())
                            || (h >= 0 && !level.getBlockState(pos).isAir()
                                && !VillageSitePreparation.vegetation(level.getBlockState(pos)))) { safe = false; break; }
                    volume.add(pos);
                }
                if (!safe) break;
                boolean planter = Math.abs(u) == 2 && v >= 1;
                boolean dry = level.getBlockState(feet.below()).is(Blocks.SAND)
                        || level.getBlockState(feet.below()).is(Blocks.RED_SAND);
                desired.put(feet.below(), planter ? (dry ? Blocks.SAND : Blocks.GRASS_BLOCK).defaultBlockState()
                        : (style == 0 ? Blocks.STONE_BRICKS : Blocks.GRAVEL).defaultBlockState());
                desired.put(feet, Blocks.AIR.defaultBlockState());
                if (planter) desired.put(feet, dry ? Blocks.DEAD_BUSH.defaultBlockState()
                        : style == 1 ? Blocks.POPPY.defaultBlockState()
                        : Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true));
                if (v == 2 && Math.abs(u) <= 1 && style != 1) {
                    Direction face = sx > 0 ? Direction.WEST : sx < 0 ? Direction.EAST
                            : sz > 0 ? Direction.NORTH : Direction.SOUTH;
                    desired.put(feet, Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, face));
                }
                if (v == 0 && u == 2 && style == 2) {
                    desired.put(feet, Blocks.STONE_BRICK_WALL.defaultBlockState());
                    desired.put(feet.above(), Blocks.LANTERN.defaultBlockState());
                }
            }
            if (!safe) continue;
            var cleared = survey.freeze(volume, height - 1, village, project);
            if (cleared == null) continue;
            Map<Long, SitePreparationPlan.Cell> result = new LinkedHashMap<>();
            for (var cell : cleared.cells()) result.put(cell.position(), cell);
            for (var entry : desired.entrySet()) {
                if (!approve(level, entry.getKey(), entry.getValue(), village, project)) { safe = false; break; }
                result.put(entry.getKey().asLong(), cell(level, entry.getKey(), entry.getValue()));
            }
            if (safe && result.keySet().stream().noneMatch(p -> reserved.contains(p) || occupied.contains(BlockPos.of(p))))
                return result;
        }
        return Map.of();
    }

    private static boolean approve(ServerLevel level, BlockPos pos, BlockState after, UUID village, long project) {
        if (!level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4) || pos.getY() < level.getMinY()
                || pos.getY() > level.getMaxY()) return false;
        BlockState before = level.getBlockState(pos);
        return !before.hasBlockEntity() && before.getFluidState().isEmpty()
                && VillageDevelopmentProtection.mayPlace(level, village, project, pos, before, after);
    }

    private static SitePreparationPlan.Cell cell(ServerLevel level, BlockPos pos, BlockState after) {
        return new SitePreparationPlan.Cell(pos.asLong(), BlockStateParser.serialize(level.getBlockState(pos)),
                BlockStateParser.serialize(after));
    }
}
