package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.SitePreparationPlan;
import java.util.*;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/** Read-only new-lot survey. Frozen removals, not this heuristic, authorize subsequent clearing. */
final class VillageSitePreparation {
    private VillageSitePreparation() { }

    static boolean torch(BlockState state) {
        return state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH)
                || state.is(Blocks.SOUL_TORCH) || state.is(Blocks.SOUL_WALL_TORCH);
    }

    static boolean naturalLeaves(BlockState state) {
        return state.getBlock() instanceof LeavesBlock
                && state.hasProperty(LeavesBlock.PERSISTENT) && !state.getValue(LeavesBlock.PERSISTENT);
    }

    static boolean naturalLog(BlockState state) {
        return state.is(BlockTags.LOGS)
                && !BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath().startsWith("stripped_");
    }

    static boolean vegetation(BlockState state) {
        // Crops/farmland and persistent (player-placed) leaves are intentionally not included.
        return torch(state) || naturalLeaves(state)
                || state.is(BlockTags.FLOWERS) || state.getBlock() instanceof net.minecraft.world.level.block.SaplingBlock
                || state.is(Blocks.AZALEA) || state.is(Blocks.FLOWERING_AZALEA)
                || state.is(Blocks.SHORT_GRASS) || state.is(Blocks.TALL_GRASS)
                || state.is(Blocks.FERN) || state.is(Blocks.LARGE_FERN) || state.is(Blocks.DEAD_BUSH)
                || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.BAMBOO)
                || state.is(Blocks.BAMBOO_SAPLING) || state.is(Blocks.SUGAR_CANE)
                || state.is(Blocks.CACTUS) || state.is(Blocks.VINE)
                || state.is(Blocks.BROWN_MUSHROOM) || state.is(Blocks.RED_MUSHROOM)
                || state.is(Blocks.SNOW) || state.is(Blocks.MOSS_CARPET)
                || state.is(Blocks.LEAF_LITTER) || state.is(Blocks.BUSH)
                || state.is(Blocks.FIREFLY_BUSH) || state.is(Blocks.SHORT_DRY_GRASS)
                || state.is(Blocks.TALL_DRY_GRASS);
    }

    static boolean clearable(ServerLevel level, BlockPos pos) {
        return new Survey(level).clearable(pos);
    }

    static boolean matchesRemoval(BlockState current, String before) {
        if (BlockStateParser.serialize(current).equals(before)) return true;
        // Leaf distance, berry age and other natural growth can change while a slow job is active.
        // Persistent landscaping leaves, different block types, storage and player walls never qualify.
        String id = BuiltInRegistries.BLOCK.getKey(current.getBlock()).toString();
        return (vegetation(current) || dryNaturalGround(current))
                && (before.equals(id) || before.startsWith(id + "["));
    }

    static boolean dryNaturalGround(BlockState state) {
        return VillageProsperityManager.isNaturalProjectGround(state)
                || state.is(Blocks.GRAVEL) || state.is(Blocks.DEEPSLATE)
                || state.is(Blocks.TUFF) || state.is(Blocks.CALCITE);
    }

    static final class Survey {
        private final ServerLevel level;
        private final Map<BlockPos, Set<BlockPos>> trees = new HashMap<>();
        private final Map<BlockPos, Boolean> surroundings = new HashMap<>();
        private final Map<Long, Integer> surfaces = new HashMap<>();

        Survey(ServerLevel level) { this.level = level; }

        boolean loaded(BlockPos pos) {
            return pos.getY() >= level.getMinY() && pos.getY() <= level.getMaxY()
                    && level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4);
        }

        boolean clearable(BlockPos pos) {
            if (!loaded(pos)) return false;
            BlockState state = level.getBlockState(pos);
            if (state.hasBlockEntity() || !state.getFluidState().isEmpty()) return false;
            return vegetation(state) || (naturalLog(state) && !tree(pos).isEmpty());
        }

        /** Ignore canopy, complete tall trunks, branches and bushes when finding real ground. */
        Integer surface(int x, int z) {
            long key = BlockPos.asLong(x, 0, z);
            if (surfaces.containsKey(key)) return surfaces.get(key);
            if (!level.hasChunk(x >> 4, z >> 4)) return null;
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
            for (int y = top; y >= Math.max(level.getMinY(), top - 64); y--) {
                BlockPos pos = new BlockPos(x, y, z);
                BlockState state = level.getBlockState(pos);
                if (state.isAir() || clearable(pos)) continue;
                if (state.hasBlockEntity() || !state.getFluidState().isEmpty() || !dryNaturalGround(state))
                    return null;
                surfaces.put(key, y + 1);
                return y + 1;
            }
            return null;
        }

        /** Nearby crafted blocks/storage veto excavation and tree removal, not ordinary foliage. */
        boolean naturalSurroundings(BlockPos pos) {
            return surroundings.computeIfAbsent(pos.immutable(), p -> {
                for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) for (int y = -2; y <= 2; y++) {
                    BlockPos nearby = p.offset(x, y, z);
                    if (!loaded(nearby)) return false;
                    BlockState s = level.getBlockState(nearby);
                    if (s.hasBlockEntity()) return false;
                    if (s.isAir() || vegetation(s) || naturalLog(s) || dryNaturalGround(s) || s.is(Blocks.DIRT_PATH)
                            || !s.getFluidState().isEmpty()) continue;
                    return false;
                }
                return true;
            });
        }

        private Set<BlockPos> tree(BlockPos start) {
            if (trees.containsKey(start)) return trees.get(start);
            Set<BlockPos> logs = new LinkedHashSet<>();
            ArrayDeque<BlockPos> queue = new ArrayDeque<>();
            queue.add(start.immutable()); logs.add(start.immutable());
            boolean canopy = false, vertical = false, safe = true, floating = false;
            while (!queue.isEmpty() && safe) {
                BlockPos pos = queue.removeFirst();
                if (logs.size() > 2048 || Math.abs(pos.getX() - start.getX()) > 32
                        || Math.abs(pos.getZ() - start.getZ()) > 32
                        || Math.abs(pos.getY() - start.getY()) > 64 || !naturalSurroundings(pos)) {
                    safe = false; break;
                }
                BlockState state = level.getBlockState(pos);
                boolean upright = state.hasProperty(RotatedPillarBlock.AXIS)
                        && state.getValue(RotatedPillarBlock.AXIS) == Direction.Axis.Y;
                vertical |= upright;
                floating |= upright && level.getBlockState(pos.below()).isAir();
                for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) for (int y = -2; y <= 2; y++) {
                    BlockPos nearby = pos.offset(x, y, z);
                    if (!loaded(nearby)) { safe = false; continue; }
                    BlockState other = level.getBlockState(nearby);
                    canopy |= naturalLeaves(other);
                    if (Math.abs(x) <= 1 && Math.abs(y) <= 1 && Math.abs(z) <= 1
                            && naturalLog(other) && logs.add(nearby)) queue.addLast(nearby);
                }
            }
            Set<BlockPos> result = safe && vertical && (canopy || floating) ? Set.copyOf(logs) : Set.of();
            for (BlockPos pos : logs) trees.put(pos, result);
            return result;
        }

        boolean excavatable(BlockPos pos, int floorY) {
            if (!loaded(pos) || pos.getY() < floorY
                    || pos.getY() >= floorY + TerrainFoundationPlan.MAX_TERRAIN_CUT) return false;
            BlockState state = level.getBlockState(pos);
            if (!dryNaturalGround(state) || !state.getFluidState().isEmpty()
                    || state.hasBlockEntity() || !naturalSurroundings(pos)) return false;
            for (Direction face : Direction.values())
                if (!loaded(pos.relative(face)) || !level.getFluidState(pos.relative(face)).isEmpty()) return false;
            // Do not excavate suspended stone floors/bridges or tunnel below a natural cavity.
            for (int y = pos.getY(); y >= floorY - 1; y--)
                if (!dryNaturalGround(level.getBlockState(new BlockPos(pos.getX(), y, pos.getZ())))) return false;
            return true;
        }

        boolean available(BlockPos pos, int floorY) {
            return loaded(pos) && (level.getBlockState(pos).isAir() || clearable(pos) || excavatable(pos, floorY));
        }

        /** Includes connected natural branch/trunk remnants, so the roof does not leave floating wood. */
        SitePreparationPlan freeze(Collection<BlockPos> volume, int floorY, UUID villageId, long projectId) {
            Set<BlockPos> removals = new LinkedHashSet<>();
            for (BlockPos pos : volume) {
                if (!loaded(pos)) return null;
                BlockState state = level.getBlockState(pos);
                if (state.isAir()) continue;
                if (!clearable(pos) && !excavatable(pos, floorY)) return null;
                removals.add(pos.immutable());
                if (naturalLog(state)) removals.addAll(tree(pos));
                if (removals.size() > SitePreparationPlan.MAX_CELLS) return null;
            }
            List<SitePreparationPlan.Cell> cells = new ArrayList<>();
            // Top down: no falling soil, severed branches or leftover upper trunks during preparation.
            for (BlockPos pos : removals.stream().sorted(Comparator.<BlockPos>comparingInt(BlockPos::getY).reversed()
                    .thenComparingInt(BlockPos::getZ).thenComparingInt(BlockPos::getX)).toList()) {
                BlockState state = level.getBlockState(pos);
                if (!VillageDevelopmentProtection.mayPlace(level, villageId, projectId, pos, state,
                        Blocks.AIR.defaultBlockState())) return null;
                cells.add(new SitePreparationPlan.Cell(pos.asLong(), BlockStateParser.serialize(state)));
            }
            return new SitePreparationPlan(cells);
        }
    }
}
