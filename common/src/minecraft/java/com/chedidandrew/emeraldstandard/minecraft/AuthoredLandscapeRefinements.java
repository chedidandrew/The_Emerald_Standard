package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.VillageArchitecture.BiomeDialect;
import com.chedidandrew.emeraldstandard.core.VillageProsperityEngine.ProjectType;
import com.chedidandrew.emeraldstandard.minecraft.AuthoredVillageStructures.Builder;
import com.chedidandrew.emeraldstandard.minecraft.AuthoredVillageStructures.Cell;
import com.chedidandrew.emeraldstandard.minecraft.AuthoredVillageStructures.Materials;
import com.chedidandrew.emeraldstandard.minecraft.AuthoredVillageStructures.Metadata;
import com.chedidandrew.emeraldstandard.minecraft.AuthoredVillageStructures.Phase;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.GrindstoneBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.block.state.properties.AttachFace;

/** Revision-four plan-only landscape finishing. Never examines or repairs player world blocks. */
final class AuthoredLandscapeRefinements {
    private AuthoredLandscapeRefinements() {
    }

    /** Called after each stage's composition and before its lighting admission. */
    static void refineStage(Builder stage, Metadata metadata, Materials p,
            String templateId, List<Cell> priorCells) {
        if (stage.templateRevision < 4 || metadata.templateRevision < 4) {
            return;
        }
        Map<BlockPos, Cell> complete = cells(priorCells, stage.values());
        boolean trainingTargetRetained = priorCells.stream().anyMatch(c -> c.state().is(Blocks.TARGET));
        for (Cell cell : stage.values()) {
            BlockPos position = position(cell);
            BlockState state = cell.state();
            if (cell.phase() == Phase.FOUNDATION && state.is(Blocks.DIRT_PATH)
                    && needsFullFooting(stateAt(complete, position.above()))) {
                // Dirt path is 15/16 high. Keep it on walking lanes, but give an authored seat,
                // post or solid cargo an exact full-height pad rather than a visible thin gap.
                replace(stage, complete, cell, (stateAt(complete, position.above()).is(Blocks.MOSS_CARPET)
                        ? Blocks.MOSS_BLOCK : p.foundation()).defaultBlockState());
            } else if (state.is(Blocks.TARGET)) {
                if (!trainingTargetRetained && isTrainingTarget(cell, metadata, complete)) {
                    trainingTargetRetained = true;
                } else {
                    replace(stage, complete, cell, cargo(metadata.type, p, position));
                }
            } else if (state.getBlock() instanceof SlabBlock
                    && state.getValue(SlabBlock.TYPE) == SlabType.TOP
                    && stateAt(complete, position.below()).getBlock() instanceof FenceBlock) {
                // A fence's collision box is 1.5 blocks high, but its VISIBLE post is only one.
                // An upper-half slab in the next cell therefore floats by half a block.
                // Lower an isolated cap; keep a full-depth bearing if something rests above it.
                SlabType contact = stage.isOccupied(position.above())
                        || complete.containsKey(position.above()) ? SlabType.DOUBLE : SlabType.BOTTOM;
                replace(stage, complete, cell, state.setValue(SlabBlock.TYPE, contact));
            } else if (state.getBlock() instanceof SlabBlock
                    && state.getValue(SlabBlock.TYPE) == SlabType.BOTTOM
                    && needsFullFooting(stateAt(complete, position.above()))) {
                // The inverse seam: a fence above a lower-half pedestal needs its upper bearing.
                // Fill only this already-authored slab cell, never a column through free space.
                replace(stage, complete, cell, state.setValue(SlabBlock.TYPE, SlabType.DOUBLE));
            } else if (state.getBlock() instanceof SlabBlock && cell.phase() != Phase.ROOF
                    && state.getValue(SlabBlock.TYPE) == SlabType.TOP
                    && (stateAt(complete, position.below()).isCollisionShapeFullBlock(
                            EmptyBlockGetter.INSTANCE, position.below())
                            || hasLateralFence(complete, position))) {
                SlabType contact = stage.isOccupied(position.above())
                        || hasLateralFence(complete, position) ? SlabType.DOUBLE : SlabType.BOTTOM;
                replace(stage, complete, cell, state.setValue(SlabBlock.TYPE, contact));
            } else if (state.is(Blocks.GRINDSTONE)
                    && state.getValue(GrindstoneBlock.FACE) == AttachFace.WALL) {
                BlockPos back = position.relative(state.getValue(GrindstoneBlock.FACING).getOpposite());
                if (!stateAt(complete, back).isFaceSturdy(EmptyBlockGetter.INSTANCE, back,
                        state.getValue(GrindstoneBlock.FACING))) {
                    if (stateAt(complete, position.below()).isCollisionShapeFullBlock(
                            EmptyBlockGetter.INSTANCE, position.below())) {
                        replace(stage, complete, cell, state.setValue(GrindstoneBlock.FACE, AttachFace.FLOOR));
                    } else if (stateAt(complete, position.above()).is(Blocks.IRON_CHAIN)) {
                        replace(stage, complete, cell, state.setValue(GrindstoneBlock.FACE, AttachFace.CEILING));
                    }
                }
            } else if (state.getBlock() instanceof FlowerPotBlock && !state.is(Blocks.FLOWER_POT)) {
                replace(stage, complete, cell, regionalPot(p, position));
            } else if (state.getBlock() instanceof LeavesBlock) {
                replace(stage, complete, cell, regionalLeaves(p));
            } else if (state.is(Blocks.AZALEA) || state.is(Blocks.FLOWERING_AZALEA)) {
                if (p.dialect() != BiomeDialect.PLAINS) {
                    // Potted natives do not acquire growth, decay, or a hidden soil dependency.
                    replace(stage, complete, cell, regionalPot(p, position));
                } else {
                    Cell below = stage.cellAt(position.below());
                    if (below != null && below.phase() == Phase.FOUNDATION
                            && (below.state().is(p.foundation()) || below.state().is(p.accent()))) {
                        replace(stage, complete, below, Blocks.MOSS_BLOCK.defaultBlockState());
                    }
                }
            }
        }
        if (templateId.equals("smithy_courtyard_01")) {
            finishForgeCorbels(stage, metadata, p, complete);
        }
        finishExactWorkshopDetails(stage, p, templateId, complete);
        AuthoredStructuralContactRefinements.refine(stage, metadata, p, templateId, priorCells);
    }

    /**
     * Called only once, after final stage-two composition. Two adjacent existing side/rear pads
     * acquire one restrained planted group. Unplanted industrial masters may receive a small
     * supported bed inside their existing side-yard margin; no route is claimed.
     */
    static void finishPlanting(Builder stage, Metadata metadata, Materials p,
            String templateId, List<Cell> priorCells) {
        if (stage.templateRevision < 4 || metadata.templateRevision < 4) {
            return;
        }
        Map<BlockPos, Cell> complete = cells(priorCells, stage.values());
        for (Cell support : complete.values().stream()
                .sorted(java.util.Comparator.comparingInt(Cell::z).thenComparingInt(Cell::x))
                .toList()) {
            BlockPos first = position(support).above();
            if (!canPlant(first, stage, metadata, p, complete)) {
                continue;
            }
            for (Direction direction : new Direction[] {Direction.SOUTH, Direction.EAST}) {
                BlockPos second = first.relative(direction);
                if (!canPlant(second, stage, metadata, p, complete)) {
                    continue;
                }
                // Both destinations and their clearance were checked before either write.
                // Dry biomes use two small clay pots, not a lush cube hedge beside every door.
                boolean dry = p.dialect() == BiomeDialect.DESERT;
                stage.put(Phase.DECOR, first.getX(), first.getY(), first.getZ(),
                        dry ? regionalPot(p, first) : regionalLeaves(p));
                stage.put(Phase.DECOR, second.getX(), second.getY(), second.getZ(),
                        regionalPot(p, second));
                return;
            }
        }
        if (metadata.height < 2 || complete.values().stream().anyMatch(c -> isGreenery(c.state()))) {
            return;
        }
        // A few industrial masters have no planted sockets. Add a compact two-tile bed only
        // within their existing side-yard margin, after every structural stage is composed.
        // The normal construction preflight still owns natural terrain/player protection.
        for (int x : new int[] {-2, metadata.width + 1}) {
            for (int z = 3; z < metadata.depth - 3; z++) {
                BlockPos first = new BlockPos(x, 1, z);
                BlockPos second = first.south();
                if (stage.isOccupied(first.below()) || stage.isOccupied(second.below())
                        || complete.containsKey(first.below()) || complete.containsKey(second.below())) {
                    continue;
                }
                Map<BlockPos, Cell> candidate = new HashMap<>(complete);
                for (BlockPos plant : new BlockPos[] {first, second}) {
                    candidate.put(plant.below(), new Cell(plant.getX(), 0, plant.getZ(),
                            p.foundation().defaultBlockState(), Phase.FOUNDATION));
                }
                if (!canPlant(first, stage, metadata, p, candidate)
                        || !canPlant(second, stage, metadata, p, candidate)) {
                    continue;
                }
                for (BlockPos plant : new BlockPos[] {first, second}) {
                    stage.put(Phase.FOUNDATION, plant.getX(), 0, plant.getZ(),
                            p.foundation().defaultBlockState());
                    stage.put(Phase.DECOR, plant.getX(), 1, plant.getZ(), regionalPot(p, plant));
                }
                return;
            }
        }
    }

    private static boolean canPlant(BlockPos position, Builder stage, Metadata metadata,
            Materials p, Map<BlockPos, Cell> complete) {
        if (position.getY() != 1 || !sideOrRear(position, metadata)
                || stage.isOccupied(position) || stage.isOccupied(position.above())
                || complete.containsKey(position) || complete.containsKey(position.above())) {
            return false;
        }
        Cell support = complete.get(position.below());
        if (support == null || support.phase() != Phase.FOUNDATION
                || !(support.state().is(p.foundation()) || support.state().is(p.accent())
                        || support.state().is(Blocks.MOSS_BLOCK))
                || !support.state().isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, position.below())) {
            // Dirt paths, gravel, farmland, stair approaches and bare terrain are not planters.
            return false;
        }
        for (BlockPos route : metadata.reservedAir) {
            if (Math.abs(route.getX() - position.getX()) <= 1
                    && Math.abs(route.getZ() - position.getZ()) <= 1) {
                return false;
            }
        }
        for (BlockPos access : metadata.accessTargets) {
            if (Math.abs(access.getX() - position.getX()) <= 2
                    && Math.abs(access.getZ() - position.getZ()) <= 2) {
                return false;
            }
        }
        // Keep a usable lane around doorway/stair/rail details even outside interior metadata.
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int y = 0; y <= 2; y++) {
                    Block block = stateAt(complete, new BlockPos(
                            position.getX() + dx, y, position.getZ() + dz)).getBlock();
                    if (block instanceof DoorBlock || block instanceof StairBlock
                            || block instanceof RailBlock || block == Blocks.LADDER) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static boolean sideOrRear(BlockPos position, Metadata metadata) {
        int x = position.getX();
        int z = position.getZ();
        return z >= 2 && z <= metadata.depth - 2 && (x < 0 || x >= metadata.width)
                || z >= metadata.depth && x >= 2 && x <= metadata.width - 3;
    }

    private static boolean isTrainingTarget(Cell cell, Metadata metadata,
            Map<BlockPos, Cell> complete) {
        BlockPos position = position(cell);
        if (metadata.type != ProjectType.GUARD_POST || cell.y() != 1
                || !sideOrRear(position, metadata)
                || !(stateAt(complete, position.above()).getBlock() instanceof RotatedPillarBlock)) {
            return false;
        }
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (stateAt(complete, position.relative(direction)).getBlock() instanceof FenceBlock) {
                return true;
            }
        }
        return false;
    }

    private static BlockState cargo(ProjectType type, Materials p, BlockPos position) {
        return switch (type) {
            case GRANARY, GUARD_POST -> Blocks.HAY_BLOCK.defaultBlockState();
            case MARKET_SQUARE -> (Math.floorMod(position.getX() + position.getZ(), 2) == 0
                    ? Blocks.WOOL.brown() : Blocks.WOOL.white()).defaultBlockState();
            case MINE_ENTRANCE -> Blocks.COBBLED_DEEPSLATE.defaultBlockState();
            case SMITHY -> p.chimney().defaultBlockState();
            case WAREHOUSE -> Blocks.NOTE_BLOCK.defaultBlockState();
            case EXCHANGE_HALL -> p.timber().defaultBlockState();
            default -> Blocks.BOOKSHELF.defaultBlockState();
        };
    }

    private static boolean needsFullFooting(BlockState above) {
        return above.getBlock() instanceof FenceBlock || above.getBlock() instanceof StairBlock
                || above.getBlock() instanceof SlabBlock || above.getBlock() instanceof FlowerPotBlock
                || above.getBlock() instanceof TrapDoorBlock || above.is(Blocks.MOSS_CARPET)
                || above.getBlock() instanceof RotatedPillarBlock
                || !above.isAir() && above.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
    }

    private static boolean hasLateralFence(Map<BlockPos, Cell> complete, BlockPos position) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (stateAt(complete, position.relative(direction)).getBlock() instanceof FenceBlock) {
                return true;
            }
        }
        return false;
    }

    private static void finishExactWorkshopDetails(Builder stage, Materials p, String templateId,
            Map<BlockPos, Cell> complete) {
        if (templateId.equals("market_bazaar_05")) {
            for (int[] socket : new int[][] {{14, 10}, {14, 16}, {4, 18}, {20, 18}}) {
                BlockPos position = new BlockPos(socket[0], 2, socket[1]);
                Cell cap = stage.cellAt(position);
                BlockState below = stateAt(complete, position.below());
                if (cap != null && cap.state().getBlock() instanceof SlabBlock
                        && (below.is(Blocks.CHEST) || below.is(Blocks.GRINDSTONE) || below.is(Blocks.STONECUTTER))
                        && !stage.isOccupied(position.above())) {
                    // These little caps floated over partial workstations and hid chest lids.
                    // The real stall canopy remains; keep the usable display/work block exposed.
                    stage.remove(position.getX(), position.getY(), position.getZ());
                    complete.remove(position);
                }
            }
        } else if (templateId.equals("granary_windmill_02")) {
            BlockPos hub = new BlockPos(5, 3, 8);
            Cell axle = stage.cellAt(hub);
            if (axle != null && axle.state().getBlock() instanceof FenceBlock
                    && stateAt(complete, hub.north()).is(Blocks.OAK_TRAPDOOR)
                    && stateAt(complete, hub.south()).is(Blocks.OAK_TRAPDOOR)) {
                // The old putIfFree hub was shadowed by its already-created fence axle.
                // Restore the intended full wheel hub so its two wooden paddles really meet it.
                replace(stage, complete, axle, p.accent().defaultBlockState());
            }
        } else if (templateId.equals("smithy_hammerhall_02")) {
            BlockPos pipe = new BlockPos(17, 2, 9);
            Cell cap = stage.cellAt(pipe);
            if (cap != null && cap.state().is(Blocks.IRON_BARS)
                    && stateAt(complete, pipe.below()).is(Blocks.CAULDRON)
                    && !stage.isOccupied(pipe.above())) {
                // A free bar above the cauldron's hollow center is not a supported pipe.
                stage.remove(pipe.getX(), pipe.getY(), pipe.getZ());
            }
        } else if (templateId.equals("warehouse_crane_02")) {
            BlockPos hanger = new BlockPos(10, 4, 7);
            Cell link = stage.cellAt(hanger);
            BlockState counterweight = stateAt(complete, hanger.below());
            if (link != null && link.phase() == Phase.FRAME && link.state().is(Blocks.IRON_CHAIN)
                    && counterweight.is(Blocks.GRINDSTONE)
                    && counterweight.getValue(GrindstoneBlock.FACE) == AttachFace.CEILING) {
                // The grindstone's two mounting prongs straddle a narrow central chain.
                // One timber yoke occupies the old lowest chain cell and joins both prongs;
                // the upper chain still runs into the existing gantry crosshead.
                replace(stage, complete, link, p.timber().defaultBlockState()
                        .setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
            }
        }
    }

    private static boolean isGreenery(BlockState state) {
        return state.getBlock() instanceof FlowerPotBlock && !state.is(Blocks.FLOWER_POT)
                || state.getBlock() instanceof LeavesBlock
                || state.is(Blocks.AZALEA) || state.is(Blocks.FLOWERING_AZALEA);
    }

    private static void finishForgeCorbels(Builder stage, Metadata metadata, Materials p,
            Map<BlockPos, Cell> complete) {
        for (int[] corner : new int[][] {{4, 7, 5}, {12, 8, 11}}) {
            BlockPos corbel = new BlockPos(corner[0], 3, corner[1]);
            BlockPos header = corbel.above();
            BlockPos bearing = new BlockPos(corner[2], 4, corner[1]);
            Cell current = stage.cellAt(corbel);
            if (current != null && current.phase() == Phase.FRAME
                    && current.state().is(p.roofStairs())
                    && stateAt(complete, bearing).is(p.timber())
                    && !stage.isOccupied(header) && !metadata.reservedAir.contains(header)
                    && !metadata.accessTargets.contains(header)) {
                // The old bracket touched the beam only diagonally. A one-cell header joins its
                // complete top face to that existing beam, above normal walking clearance.
                stage.put(Phase.FRAME, header.getX(), header.getY(), header.getZ(),
                        p.timber().defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
            }
        }
        Cell shelf = stage.cellAt(new BlockPos(6, 1, 4));
        if (shelf != null && shelf.state().getBlock() instanceof SlabBlock
                && shelf.state().getValue(SlabBlock.TYPE) == SlabType.TOP
                && stateAt(complete, new BlockPos(6, 0, 4)).isCollisionShapeFullBlock(
                        EmptyBlockGetter.INSTANCE, new BlockPos(6, 0, 4))
                && !stage.isOccupied(new BlockPos(6, 2, 4))) {
            stage.force(shelf.phase(), shelf.x(), shelf.y(), shelf.z(),
                    shelf.state().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
        }
    }

    private static BlockState regionalLeaves(Materials p) {
        Block block = switch (p.dialect()) {
            case PLAINS -> Blocks.OAK_LEAVES;
            case DESERT, SAVANNA -> Blocks.ACACIA_LEAVES;
            case TAIGA, SNOWY -> Blocks.SPRUCE_LEAVES;
        };
        return block.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
    }

    private static BlockState regionalPot(Materials p, BlockPos position) {
        boolean alternate = Math.floorMod(position.getX() + position.getZ(), 2) == 0;
        Block block = switch (p.dialect()) {
            case PLAINS -> alternate ? Blocks.POTTED_DANDELION : Blocks.POTTED_BLUE_ORCHID;
            case DESERT -> alternate ? Blocks.POTTED_CACTUS : Blocks.POTTED_DEAD_BUSH;
            case SAVANNA -> alternate ? Blocks.POTTED_ACACIA_SAPLING : Blocks.POTTED_DEAD_BUSH;
            case TAIGA, SNOWY -> alternate ? Blocks.POTTED_FERN : Blocks.POTTED_SPRUCE_SAPLING;
        };
        return block.defaultBlockState();
    }

    private static void replace(Builder stage, Map<BlockPos, Cell> complete,
            Cell cell, BlockState state) {
        stage.force(cell.phase(), cell.x(), cell.y(), cell.z(), state);
        complete.put(position(cell), new Cell(cell.x(), cell.y(), cell.z(), state, cell.phase()));
    }

    private static Map<BlockPos, Cell> cells(List<Cell> previous, List<Cell> current) {
        Map<BlockPos, Cell> result = new HashMap<>();
        previous.forEach(cell -> result.put(position(cell), cell));
        current.forEach(cell -> result.put(position(cell), cell));
        return result;
    }

    private static BlockPos position(Cell cell) {
        return new BlockPos(cell.x(), cell.y(), cell.z());
    }

    private static BlockState stateAt(Map<BlockPos, Cell> cells, BlockPos position) {
        Cell cell = cells.get(position);
        return cell == null ? Blocks.AIR.defaultBlockState() : cell.state();
    }
}
