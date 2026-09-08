package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.minecraft.AuthoredVillageStructures.Builder;
import com.chedidandrew.emeraldstandard.minecraft.AuthoredVillageStructures.Cell;
import com.chedidandrew.emeraldstandard.minecraft.AuthoredVillageStructures.Materials;
import com.chedidandrew.emeraldstandard.minecraft.AuthoredVillageStructures.Metadata;
import com.chedidandrew.emeraldstandard.minecraft.AuthoredVillageStructures.Phase;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Targeted third-revision craft changes to the seven yard motifs below Carol's visual threshold.
 *
 * <p>Call each motif hook immediately after its original authoring method, only for revision 3 or
 * later. Revisions 1 and 2 retain their frozen layouts. These methods operate on the authored plan,
 * never on placed world blocks, and cosmetic cargo uses solid blocks without an inventory.
 */
final class AuthoredDoodadRefinements {
    private AuthoredDoodadRefinements() {
    }

    /**
     * A low domestic bench suits these four cottage yards better than the tall public-seat rig.
     * This runs after stage-two presentation so an adjoining canopy can keep every shared support.
     */
    static void refineCompactCottageRearBench(
            Builder stage, Metadata metadata, Materials p, String templateId) {
        if (stage.templateRevision < 3 || !hasCompactCottageRearBench(templateId)) {
            return;
        }
        preserveCottageSeatBases(stage, p);
        int centerX = metadata.width / 2;
        lowerExactRearBench(stage, metadata, p, centerX - 1, metadata.depth + 5);
        lowerExactRearBench(stage, metadata, p, centerX - 2, metadata.depth + 4);
    }

    private static boolean hasCompactCottageRearBench(String templateId) {
        return switch (templateId) {
            case "cottage_hearth_01", "cottage_garden_02",
                    "cottage_courtyard_03", "cottage_bay_04" -> true;
            default -> false;
        };
    }

    private static void preserveCottageSeatBases(Builder stage, Materials p) {
        for (Cell cell : stage.values()) {
            if (cell.phase() != Phase.FOUNDATION || !cell.state().is(Blocks.DIRT_PATH)) {
                continue;
            }
            Cell seat = stage.cellAt(new BlockPos(cell.x(), cell.y() + 1, cell.z()));
            if (seat != null && seat.phase() == Phase.DECOR
                    && (seat.state().is(p.timber()) || seat.state().is(p.wall()))) {
                // A solid stump or planter above a path makes Minecraft turn that path into dirt.
                // The same authored footprint gets a durable masonry pad before world placement.
                stage.force(Phase.FOUNDATION, cell.x(), cell.y(), cell.z(),
                        p.foundation().defaultBlockState());
            }
        }
    }

    private static void lowerExactRearBench(
            Builder stage, Metadata metadata, Materials p, int minX, int z) {
        int maxX = minX + 2;
        int backZ = z + 1;
        BlockState endSeat = p.roofStairs().defaultBlockState()
                .setValue(StairBlock.FACING, Direction.NORTH);
        BlockState backLog = p.timber().defaultBlockState()
                .setValue(RotatedPillarBlock.AXIS, Direction.Axis.X);
        BlockState lowBack = openTrapdoor(trapdoorBlock(p), Direction.NORTH);
        for (int x : new int[] {minX, maxX}) {
            if (!hasState(stage, x, 1, z, endSeat)
                    || !hasState(stage, x, 2, z, p.fence().defaultBlockState())
                    || !isBlock(stage, x, 3, z, p.roofSlab())
                    || !hasState(stage, x, 1, backZ, p.fence().defaultBlockState())
                    || !hasState(stage, x, 2, backZ, backLog)) {
                return;
            }
        }
        BlockPos newBackCenter = new BlockPos(minX + 1, 1, backZ);
        if (!hasState(stage, minX + 1, 1, z, p.roofSlab().defaultBlockState())
                || !hasState(stage, minX + 1, 2, backZ, lowBack)
                || stage.isOccupied(newBackCenter)
                || metadata.reservedAir.contains(newBackCenter)
                || metadata.accessTargets.contains(newBackCenter)) {
            return;
        }
        Set<BlockPos> tallAssembly = Set.of(
                new BlockPos(minX, 2, z), new BlockPos(maxX, 2, z),
                new BlockPos(minX, 3, z), new BlockPos(maxX, 3, z),
                new BlockPos(minX, 2, backZ), new BlockPos(minX + 1, 2, backZ),
                new BlockPos(maxX, 2, backZ));
        if (hasSharedBenchSupport(stage, tallAssembly)) {
            return;
        }
        for (BlockPos position : tallAssembly) {
            stage.remove(position.getX(), position.getY(), position.getZ());
        }
        for (int x = minX; x <= maxX; x++) {
            stage.force(Phase.FRAME, x, 1, backZ, lowBack);
        }
    }

    private static boolean hasSharedBenchSupport(Builder stage, Set<BlockPos> tallAssembly) {
        for (BlockPos position : tallAssembly) {
            for (Direction direction : new Direction[] {
                    Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
            }) {
                BlockPos contact = position.relative(direction);
                if (!tallAssembly.contains(contact) && stage.isOccupied(contact)) {
                    Cell neighbor = stage.cellAt(contact);
                    if (direction == Direction.UP || neighbor == null
                            || neighbor.phase() == Phase.FRAME || neighbor.phase() == Phase.SHELL
                            || neighbor.phase() == Phase.ROOF || neighbor.phase() == Phase.FIXTURE) {
                        // Previous-stage contacts, overhead objects and lateral architectural
                        // bearings keep the whole bench. A neighboring pot or low work tray has
                        // its own foundation and does not turn an outdoor seat into a roof support.
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** The garden's decorative shrubs are live vegetation: their planting tiles must be soil. */
    static void ensureGardenShrubSubstrates(Builder stage, Materials p, int centerX, int z) {
        if (stage.templateRevision != 3) {
            return;
        }
        for (int x : new int[] {centerX - 3, centerX - 1}) {
            Cell plant = stage.cellAt(new BlockPos(x, 1, z));
            Cell foundation = stage.cellAt(new BlockPos(x, 0, z));
            if (plant != null && plant.phase() == Phase.DECOR
                    && (plant.state().is(Blocks.AZALEA) || plant.state().is(Blocks.FLOWERING_AZALEA))
                    && foundation != null && foundation.phase() == Phase.FOUNDATION
                    && (foundation.state().is(p.accent()) || foundation.state().is(p.foundation()))) {
                // Keep the same plant, footprint and border. Moss is a vanilla azalea substrate,
                // unlike the stone display plinth that looked plausible but dropped on updates.
                stage.force(Phase.FOUNDATION, x, 0, z, Blocks.MOSS_BLOCK.defaultBlockState());
            }
        }
    }

    /** D1: connect a solitary side-yard pot to its lamp with a low, L-shaped planted border. */
    static void refineStandalonePlanters(Builder stage, Metadata metadata, Materials p) {
        for (int x : new int[] {-4, metadata.width + 3}) {
            Cell pot = stage.cellAt(new BlockPos(x, 1, 1));
            Cell nextPlant = stage.cellAt(new BlockPos(x, 1, 2));
            Cell lampPost = stage.cellAt(new BlockPos(x, 1, 4));
            if (pot == null || !(pot.state().getBlock() instanceof FlowerPotBlock)
                    || nextPlant != null || lampPost == null) {
                // The longer D8 planter and every other already-composed yard scene stay intact.
                continue;
            }
            int outward = x < 0 ? -1 : 1;
            int edgeX = x + outward;
            BlockPos[] needed = {
                new BlockPos(x, 0, 2), new BlockPos(x, 1, 2),
                new BlockPos(edgeX, 0, 1), new BlockPos(edgeX, 0, 2),
                new BlockPos(edgeX, 0, 3)
            };
            if (!allFree(stage, needed)) {
                continue;
            }
            stage.put(Phase.FOUNDATION, x, 0, 2, Blocks.MOSS_BLOCK.defaultBlockState());
            stage.put(Phase.DECOR, x, 1, 2, Blocks.FLOWERING_AZALEA.defaultBlockState());
            // Mirrored lamps can already have their stone knee at z=3; join that foot instead of
            // requiring an empty gap or replacing the existing fixture support.
            if (stage.putIfFree(Phase.FOUNDATION, x, 0, 3, Blocks.MOSS_BLOCK.defaultBlockState())) {
                stage.putIfFree(Phase.DECOR, x, 1, 3, Blocks.MOSS_CARPET.defaultBlockState());
            }
            stage.put(Phase.FOUNDATION, edgeX, 0, 1, p.roofSlab().defaultBlockState());
            stage.put(Phase.FOUNDATION, edgeX, 0, 2, topSlab(p));
            stage.put(Phase.FOUNDATION, edgeX, 0, 3, p.roofSlab().defaultBlockState());
        }
    }

    /** D3: a masonry socket, knee brace and shallow cap keep the yard lantern visibly supported. */
    static void refineDressingLamp(Builder stage, int x, int z, Materials p) {
        int armStepX = ((x + z) & 1) == 0 ? (x < 0 ? 1 : -1) : 0;
        int armStepZ = armStepX == 0 ? -1 : 0;
        int armX = x + armStepX;
        int armZ = z + armStepZ;
        if (!isBlock(stage, x, 4, z, p.timber())
                || !isBlock(stage, armX, 4, armZ, p.timber())) {
            return;
        }
        stage.force(Phase.FOUNDATION, x, 0, z, p.foundation().defaultBlockState());
        stage.force(Phase.FRAME, x, 1, z, stoneSocket(p));
        // The post cap is full-depth so a later stage's finial has a real bearing. The arm may
        // tuck below an existing portal/eave from an earlier stage: its underside still carries
        // the chain, but a shallow slab here would introduce a half-block gap below that roof.
        // Retain the original full timber arm at those shared contacts; otherwise use a slim cap.
        stage.force(Phase.FRAME, x, 4, z, p.timber().defaultBlockState());
        stage.force(Phase.FRAME, armX, 4, armZ,
                stage.isOccupied(new BlockPos(armX, 5, armZ))
                        ? p.timber().defaultBlockState().setValue(RotatedPillarBlock.AXIS,
                                armStepX == 0 ? Direction.Axis.Z : Direction.Axis.X)
                        : p.roofSlab().defaultBlockState());
        int footX = x - armStepZ;
        int footZ = z + armStepX;
        if (isBlock(stage, footX, 1, footZ, p.roofSlab())) {
            stage.force(Phase.FOUNDATION, footX, 0, footZ,
                    p.foundation().defaultBlockState());
            Direction towardPost = footX < x ? Direction.EAST
                    : footX > x ? Direction.WEST
                    : footZ < z ? Direction.SOUTH : Direction.NORTH;
            stage.force(Phase.DECOR, footX, 1, footZ,
                    p.entryStairs().defaultBlockState().setValue(StairBlock.FACING, towardPost));
        }
    }

    /** D4: actual bark, exposed cross-cut ends, light end stops and contrasting iron bindings. */
    static void refineLogRackZ(
            Builder stage, Materials p, int x, int minZ, int length, int tiers, boolean railBound) {
        int top = Math.max(1, tiers);
        int outwardX = x < 0 ? x - 1 : x + 1;
        int inwardX = x < 0 ? x + 1 : x - 1;
        BlockState log = barkLog(p).defaultBlockState()
                .setValue(RotatedPillarBlock.AXIS, Direction.Axis.X);
        for (int z = minZ; z < minZ + length; z++) {
            int stackHeight = top > 1 && z > minZ && z < minZ + length - 1 ? top : 1;
            for (int y = 1; y <= stackHeight; y++) {
                stage.force(Phase.FRAME, x, y, z, log);
            }
        }
        for (int z : new int[] {minZ, minZ + length - 1}) {
            replaceBlock(stage, outwardX, 2, z, p.roofStairs(),
                    Phase.DECOR, p.roofSlab().defaultBlockState());
            // An inclined foot under the thin end stop gives the rack its visible knee brace.
            replaceBlock(stage, inwardX, 1, z, p.roofStairs(), Phase.FRAME,
                    p.roofStairs().defaultBlockState().setValue(
                            StairBlock.FACING, z == minZ ? Direction.SOUTH : Direction.NORTH));
        }
        for (int strapZ : new int[] {
                minZ + Math.min(1, length - 1), minZ + Math.max(0, length - 2)
        }) {
            removeBlock(stage, outwardX, 1, strapZ, Blocks.IRON_BARS);
            removeBlock(stage, outwardX, 2, strapZ, Blocks.IRON_BARS);
        }
        if (railBound && length >= 3) {
            int strapZ = minZ + length / 2;
            // One thin iron belt leaves every upper billet and both end billets in clear view.
            stage.putIfFree(Phase.DECOR, outwardX, 1, strapZ,
                    openTrapdoor(Blocks.IRON_TRAPDOOR,
                            x < 0 ? Direction.EAST : Direction.WEST));
            stage.putIfFree(Phase.DECOR, inwardX, 1, strapZ,
                    openTrapdoor(Blocks.IRON_TRAPDOOR,
                            x < 0 ? Direction.WEST : Direction.EAST));
        }
    }

    /** D9: three wooden parcels sit directly on one thin pallet, with no stone caps or rails. */
    static void refineCrateCluster(Builder stage, Materials p, int x, int z, boolean stacked) {
        for (int dx = 0; dx <= 1; dx++) {
            stage.force(Phase.FOUNDATION, x + dx, 0, z, topSlab(p));
            stage.force(Phase.DECOR, x + dx, 1, z,
                    (dx == 0 ? Blocks.NOTE_BLOCK : p.wall()).defaultBlockState());
            stage.remove(x + dx, 2, z);
            stage.remove(x + dx, 3, z);
            if (isBlock(stage, x + dx, 1, z + 1, p.roofSlab())) {
                stage.force(Phase.FOUNDATION, x + dx, 0, z + 1, topSlab(p));
                stage.remove(x + dx, 1, z + 1);
            }
        }
        stage.remove(x, 4, z);
        removeBlock(stage, x + 1, 2, z + 1, p.roofStairs());
        removeBlock(stage, x + 1, 3, z + 1, trapdoorBlock(p));
        if (!stage.isOccupied(new BlockPos(x + 1, 1, z + 1))) {
            stage.put(Phase.DECOR, x + 1, 1, z + 1, Blocks.NOTE_BLOCK.defaultBlockState());
            stage.putIfFree(Phase.DECOR, x + 1, 2, z + 1,
                    trapdoorBlock(p).defaultBlockState());
        }
        BlockState front = openTrapdoor(trapdoorBlock(p), Direction.SOUTH);
        BlockState side = openTrapdoor(trapdoorBlock(p), Direction.EAST);
        for (int dx = 0; dx <= 1; dx++) {
            removeBlock(stage, x + dx, 2, z - 1, trapdoorBlock(p));
            stage.putIfFree(Phase.DECOR, x + dx, 1, z - 1, front);
        }
        removeBlock(stage, x - 1, 2, z, trapdoorBlock(p));
        removeBlock(stage, x - 1, 3, z, trapdoorBlock(p));
        stage.putIfFree(Phase.DECOR, x - 1, 1, z, side);
        if (stacked) {
            stage.put(Phase.DECOR, x, 2, z, Blocks.NOTE_BLOCK.defaultBlockState());
            stage.put(Phase.DECOR, x, 3, z, trapdoorBlock(p).defaultBlockState());
            stage.putIfFree(Phase.DECOR, x - 1, 2, z, side);
            stage.putIfFree(Phase.DECOR, x, 2, z - 1, front);
        } else {
            stage.put(Phase.DECOR, x, 2, z, trapdoorBlock(p).defaultBlockState());
        }
        stage.put(Phase.DECOR, x + 1, 2, z, trapdoorBlock(p).defaultBlockState());
    }

    /** D10: a narrow, low bed exposes its axle, round log-end wheels, hubs and two long handles. */
    static void refineHandCart(Builder stage, Materials p, int centerX, int z, Block cargo) {
        int axleZ = z + 1;
        for (int x = centerX - 1; x <= centerX + 1; x++) {
            for (int dz = 0; dz <= 2; dz++) {
                stage.remove(x, 2, z + dz);
                stage.force(Phase.FOUNDATION, x, 0, z + dz,
                        Blocks.DIRT_PATH.defaultBlockState());
            }
        }
        for (int wheelX : new int[] {centerX - 2, centerX + 2}) {
            stage.remove(wheelX, 1, axleZ);
            for (int dz = 0; dz <= 2; dz++) {
                stage.remove(wheelX, 2, z + dz);
            }
        }
        for (int rimZ : new int[] {z, z + 2}) {
            stage.remove(centerX - 1, 3, rimZ);
            stage.remove(centerX + 1, 3, rimZ);
        }
        stage.remove(centerX, 3, axleZ);
        stage.remove(centerX, 4, axleZ);
        for (int dz = 3; dz <= 4; dz++) {
            for (int handleX : new int[] {centerX - 1, centerX + 1}) {
                stage.remove(handleX, 2, z + dz);
                if (dz == 3) {
                    stage.remove(handleX, 1, z + dz);
                }
            }
        }
        BlockState axle = barkLog(p).defaultBlockState()
                .setValue(RotatedPillarBlock.AXIS, Direction.Axis.X);
        for (int x = centerX - 1; x <= centerX + 1; x++) {
            stage.force(Phase.FRAME, x, 1, axleZ, axle);
        }
        stage.put(Phase.FRAME, centerX, 1, z, topSlab(p));
        stage.put(Phase.FRAME, centerX, 1, z + 2, axle);
        for (int side : new int[] {-1, 1}) {
            int sideX = centerX + side;
            stage.put(Phase.DECOR, centerX + side * 2, 1, axleZ,
                    Blocks.STONE_BUTTON.defaultBlockState()
                            .setValue(ButtonBlock.FACE, AttachFace.WALL)
                            .setValue(ButtonBlock.FACING, side < 0 ? Direction.WEST : Direction.EAST));
            for (int dz = 0; dz <= 2; dz++) {
                stage.put(Phase.FRAME, sideX, 2, z + dz,
                        openTrapdoor(trapdoorBlock(p), side < 0 ? Direction.EAST : Direction.WEST));
            }
            for (int dz = 2; dz <= 4; dz++) {
                stage.put(Phase.FRAME, sideX, 1, z + dz, p.fence().defaultBlockState());
            }
        }
        stage.put(Phase.DECOR, centerX, 2, axleZ, cargo.defaultBlockState());
        stage.put(Phase.DECOR, centerX, 3, axleZ, Blocks.RAIL.defaultBlockState());
    }

    /** D14: a low, graded pile uses broken stone silhouettes and recesses the raw stock in grade. */
    static void refineMaterialPile(
            Builder stage, Materials p, int x, int z, Block material, int richness) {
        int outward = x < 0 ? -1 : 1;
        int stockX = x - outward;
        Direction slope = outward < 0 ? Direction.WEST : Direction.EAST;
        stage.force(Phase.DECOR, x, 1, z, Blocks.COBBLED_DEEPSLATE_STAIRS.defaultBlockState()
                .setValue(StairBlock.FACING, slope));
        replaceBlock(stage, x + outward, 1, z, Blocks.COBBLESTONE_WALL,
                Phase.DECOR, Blocks.COBBLESTONE_SLAB.defaultBlockState());
        if (isBlock(stage, stockX, 1, z + 1, material)) {
            stage.force(Phase.FOUNDATION, stockX, 0, z + 1, material.defaultBlockState());
            stage.force(Phase.DECOR, stockX, 1, z + 1,
                    Blocks.ANDESITE_SLAB.defaultBlockState());
        }
        removeBlock(stage, stockX, 2, z + 1, p.roofSlab());
        replaceBlock(stage, x, 1, z + 1, p.roofSlab(), Phase.DECOR,
                Blocks.COBBLESTONE_STAIRS.defaultBlockState().setValue(
                        StairBlock.FACING, Direction.SOUTH));
        if (richness >= 1) {
            removeBlock(stage, x, 2, z, p.roofSlab());
            replaceBlock(stage, stockX, 1, z, p.roofStairs(), Phase.DECOR,
                    Blocks.ANDESITE_STAIRS.defaultBlockState().setValue(
                            StairBlock.FACING, slope.getOpposite()));
            replaceBlock(stage, x + outward, 1, z + 2, Blocks.COBBLESTONE_WALL,
                    Phase.DECOR, Blocks.COBBLED_DEEPSLATE_SLAB.defaultBlockState());
        }
        if (richness >= 2) {
            replaceBlock(stage, stockX, 1, z + 2, p.roofStairs(),
                    Phase.DECOR, Blocks.ANDESITE_SLAB.defaultBlockState());
            replaceBlock(stage, x, 1, z + 2, Blocks.COBBLESTONE_WALL, Phase.DECOR,
                    Blocks.COBBLESTONE_STAIRS.defaultBlockState().setValue(
                            StairBlock.FACING, Direction.NORTH));
            removeBlock(stage, x, 2, z + 2, p.roofSlab());
        }
    }

    /** D15: a thin high crossbar leaves the long metal tool and low work shelf visible. */
    static void refineToolRackZ(Builder stage, Materials p, int x, int minZ, int length) {
        int ledgeX = x < 0 ? x - 1 : x + 1;
        for (int i = 0; i < length; i++) {
            if (stage.isOccupied(new BlockPos(x, 3, minZ + i))) {
                return;
            }
        }
        for (int i = 0; i < length; i++) {
            refineToolRackCell(stage, p, x, minZ + i, ledgeX, minZ + i,
                    i == 0 || i == length - 1, i == length / 2);
        }
    }

    static void refineToolRackX(Builder stage, Materials p, int minX, int z, int length) {
        for (int i = 0; i < length; i++) {
            if (stage.isOccupied(new BlockPos(minX + i, 3, z))) {
                return;
            }
        }
        for (int i = 0; i < length; i++) {
            refineToolRackCell(stage, p, minX + i, z, minX + i, z + 1,
                    i == 0 || i == length - 1, i == length / 2);
        }
    }

    private static void refineToolRackCell(
            Builder stage, Materials p, int x, int z, int shelfX, int shelfZ,
            boolean end, boolean center) {
        stage.force(Phase.FRAME, x, 2, z,
                (end ? p.fence() : Blocks.IRON_BARS).defaultBlockState());
        stage.putIfFree(Phase.FRAME, x, 3, z, p.roofSlab().defaultBlockState());
        if (!end) {
            stage.force(Phase.FRAME, x, 1, z, Blocks.IRON_BARS.defaultBlockState());
        }
        // The blade occupies the former shallow shelf cell. Its half block profile meets the
        // handle, while the two empty spaces around it keep the motif readable from the side.
        if (center) {
            replaceBlock(stage, shelfX, 1, shelfZ, Blocks.IRON_TRAPDOOR, Phase.DECOR,
                    Blocks.POLISHED_ANDESITE_SLAB.defaultBlockState().setValue(
                            SlabBlock.TYPE, SlabType.TOP));
        } else if (isBlock(stage, shelfX, 1, shelfZ, p.roofStairs())) {
            stage.force(Phase.DECOR, shelfX, 1, shelfZ,
                    p.roofSlab().defaultBlockState());
        }
    }

    private static BlockState stoneSocket(Materials p) {
        return (p.dialect() == com.chedidandrew.emeraldstandard.core.VillageArchitecture.BiomeDialect.DESERT
                        ? Blocks.SANDSTONE_WALL : Blocks.STONE_BRICK_WALL)
                .defaultBlockState();
    }

    private static Block barkLog(Materials p) {
        return switch (p.dialect()) {
            case DESERT, SAVANNA -> Blocks.ACACIA_LOG;
            case TAIGA, SNOWY -> Blocks.SPRUCE_LOG;
            case PLAINS -> Blocks.OAK_LOG;
        };
    }

    private static Block trapdoorBlock(Materials p) {
        return switch (p.dialect()) {
            case DESERT, SAVANNA -> Blocks.ACACIA_TRAPDOOR;
            case TAIGA, SNOWY -> Blocks.SPRUCE_TRAPDOOR;
            case PLAINS -> Blocks.OAK_TRAPDOOR;
        };
    }

    private static BlockState openTrapdoor(Block block, Direction facing) {
        return block.defaultBlockState()
                .setValue(TrapDoorBlock.FACING, facing)
                .setValue(TrapDoorBlock.HALF, Half.BOTTOM)
                .setValue(TrapDoorBlock.OPEN, true);
    }

    private static BlockState topSlab(Materials p) {
        return p.roofSlab().defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP);
    }

    private static boolean allFree(Builder stage, BlockPos[] positions) {
        for (BlockPos position : positions) {
            if (stage.isOccupied(position)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isBlock(Builder stage, int x, int y, int z, Block block) {
        Cell cell = stage.cellAt(new BlockPos(x, y, z));
        return cell != null && cell.state().is(block);
    }

    private static boolean hasState(Builder stage, int x, int y, int z, BlockState state) {
        Cell cell = stage.cellAt(new BlockPos(x, y, z));
        return cell != null && cell.state().equals(state);
    }

    private static void removeBlock(Builder stage, int x, int y, int z, Block block) {
        if (isBlock(stage, x, y, z, block)) {
            stage.remove(x, y, z);
        }
    }

    private static void replaceBlock(
            Builder stage, int x, int y, int z, Block oldBlock, Phase phase, BlockState replacement) {
        if (isBlock(stage, x, y, z, oldBlock)) {
            stage.force(phase, x, y, z, replacement);
        }
    }
}
