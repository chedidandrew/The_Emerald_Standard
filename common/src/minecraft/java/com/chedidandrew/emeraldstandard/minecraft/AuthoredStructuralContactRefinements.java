package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.minecraft.AuthoredVillageStructures.Builder;
import com.chedidandrew.emeraldstandard.minecraft.AuthoredVillageStructures.Cell;
import com.chedidandrew.emeraldstandard.minecraft.AuthoredVillageStructures.Materials;
import com.chedidandrew.emeraldstandard.minecraft.AuthoredVillageStructures.Metadata;
import com.chedidandrew.emeraldstandard.minecraft.AuthoredVillageStructures.Phase;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/** Small, explicitly authored bearing joints; never a world repair or a generic gap filler. */
final class AuthoredStructuralContactRefinements {
    private AuthoredStructuralContactRefinements() {
    }

    static void refine(Builder stage, Metadata metadata, Materials p,
            String templateId, List<Cell> priorCells) {
        if (stage.templateRevision < 4 || metadata.templateRevision < 4) {
            return;
        }
        Map<BlockPos, Cell> complete = new HashMap<>();
        priorCells.forEach(c -> complete.put(position(c), c));
        stage.values().forEach(c -> complete.put(position(c), c));
        switch (templateId) {
            case "mine_headframe_01" -> {
                for (int z : new int[] {5, 9}) {
                    // Close the upper elbows of each diagonal. Nothing is added in the two-high
                    // undercroft/rail route, and the existing heavy y=5 tie carries the assembly.
                    List<Bearing> joints = new ArrayList<>();
                    for (int x : new int[] {5, 9}) {
                        if (x == 5 && z == 9 && originalMineShedBearing(stage, p)) {
                            // This rear-left elbow already meets the little shed's original
                            // roof stair. Keep that exact roof and finish only the open elbows.
                            continue;
                        }
                        joints.add(new Bearing(new BlockPos(x, 3, z), new BlockPos(x, 4, z),
                                new BlockPos(x, 5, z), p.timber().defaultBlockState()));
                    }
                    for (int x : new int[] {6, 8}) {
                        joints.add(new Bearing(new BlockPos(x, 2, z), new BlockPos(x, 3, z),
                                new BlockPos(x, 4, z), p.timber().defaultBlockState()));
                    }
                    install(stage, metadata, complete, joints, p.timber().defaultBlockState(), Phase.FRAME);
                }
            }
            case "granary_loft_01" -> {
                for (int z : new int[] {2, 9}) {
                    List<Bearing> joints = new ArrayList<>();
                    for (int x : new int[] {3, 9}) {
                        joints.add(new Bearing(new BlockPos(x, 2, z), new BlockPos(x, 3, z),
                                new BlockPos(x, 4, z), p.timber().defaultBlockState()
                                        .setValue(RotatedPillarBlock.AXIS, Direction.Axis.X)));
                    }
                    install(stage, metadata, complete, joints, p.timber().defaultBlockState(), Phase.FRAME);
                }
            }
            case "house_towercourt_06" -> corbel(stage, metadata, complete, p,
                    new BlockPos(-1, 9, 15), Direction.EAST, Direction.SOUTH);
            case "guard_bastion_02" -> {
                corbel(stage, metadata, complete, p, new BlockPos(-1, 10, 2), Direction.EAST, Direction.SOUTH);
                corbel(stage, metadata, complete, p, new BlockPos(15, 10, 2), Direction.WEST, Direction.SOUTH);
            }
            case "granary_windmill_02" -> {
                // The shallow receiving-bin gable projects behind its timber frame. Give this
                // isolated stair a one-block eave tie, anchored into the existing rear gable wall.
                BlockPos roof = new BlockPos(18, 6, 11);
                install(stage, metadata, complete, List.of(new Bearing(roof, roof.below(),
                        new BlockPos(18, 5, 10), p.timber().defaultBlockState()
                                .setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z))),
                        p.roofStairs().defaultBlockState().setValue(StairBlock.FACING, Direction.EAST), Phase.ROOF);
            }
            case "inn_gallery_01" -> finishArrivalKingPost(stage, metadata, p);
            case "inn_courtyard_05" -> {
                lowerCourtyardCap(stage, metadata, p, 7, 6, Direction.WEST, 7);
                lowerCourtyardCap(stage, metadata, p, 7, 14, Direction.WEST, 7);
                lowerCourtyardCap(stage, metadata, p, 17, 14, Direction.EAST, 18);
            }
            default -> {
                // This is an enumerated authored revision, not a global structural repair pass.
            }
        }
    }

    private static void finishArrivalKingPost(Builder stage, Metadata metadata, Materials p) {
        // The larger arrival gable retained the old gatehouse's four-cell king-post stack.
        // Its top ends at y=10 while the new ridge is y=12: one upper tie makes the retained
        // hanging timber deliberate, without placing a column into the public hall below it.
        for (int y = 7; y <= 10; y++) {
            Cell old = stage.cellAt(new BlockPos(8, y, 4));
            if (old == null || (old.phase() != Phase.FRAME && old.phase() != Phase.SHELL)
                    || !old.state().equals((y <= 8 ? p.timber() : p.wall()).defaultBlockState())) {
                return;
            }
        }
        BlockPos tie = new BlockPos(8, 11, 4);
        Cell ridge = stage.cellAt(tie.above());
        if (!clear(stage, metadata, tie) || ridge == null || ridge.phase() != Phase.ROOF
                || !ridge.state().equals(p.roofSlab().defaultBlockState())) {
            return;
        }
        stage.put(Phase.FRAME, 8, 11, 4, p.timber().defaultBlockState());
    }

    private static void lowerCourtyardCap(Builder stage, Metadata metadata, Materials p,
            int minX, int minZ, Direction anchorFacing, int anchorX) {
        // These exact 2x2 crossing patches were one whole course above their intersecting
        // slopes. Lower the existing cap, rather than bulking out the roof with filler columns.
        BlockState cap = p.roofSlab().defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE);
        Cell anchor = stage.cellAt(new BlockPos(anchorX, 9, minZ));
        if (anchor == null || anchor.phase() != Phase.ROOF
                || !anchor.state().equals(p.roofStairs().defaultBlockState()
                        .setValue(StairBlock.FACING, anchorFacing))) {
            return;
        }
        for (int x = minX; x <= minX + 1; x++) {
            for (int z = minZ; z <= minZ + 1; z++) {
                Cell old = stage.cellAt(new BlockPos(x, 11, z));
                if (old == null || old.phase() != Phase.ROOF || !old.state().equals(cap)
                        || !clear(stage, metadata, new BlockPos(x, 10, z))) {
                    return;
                }
            }
        }
        for (int x = minX; x <= minX + 1; x++) {
            for (int z = minZ; z <= minZ + 1; z++) {
                stage.remove(x, 11, z);
                stage.put(Phase.ROOF, x, 10, z, cap);
            }
        }
    }

    private static boolean clear(Builder stage, Metadata metadata, BlockPos position) {
        return !stage.isOccupied(position) && !metadata.reservedAir.contains(position)
                && !metadata.accessTargets.contains(position);
    }

    private static boolean originalMineShedBearing(Builder stage, Materials p) {
        Cell roof = stage.cellAt(new BlockPos(5, 4, 9));
        Cell below = stage.cellAt(new BlockPos(5, 3, 9));
        Cell above = stage.cellAt(new BlockPos(5, 5, 9));
        return roof != null && roof.phase() == Phase.ROOF
                && roof.state().equals(p.roofStairs().defaultBlockState()
                        .setValue(StairBlock.FACING, Direction.WEST))
                && below != null && below.phase() == Phase.FRAME
                && below.state().equals(p.timber().defaultBlockState())
                && above != null && above.phase() == Phase.FRAME
                && above.state().equals(p.timber().defaultBlockState());
    }

    private static void corbel(Builder stage, Metadata metadata, Map<BlockPos, Cell> complete,
            Materials p, BlockPos roof, Direction wall, Direction roofFacing) {
        BlockPos bearing = roof.below();
        BlockState bracket = p.entryStairs().defaultBlockState()
                .setValue(StairBlock.FACING, wall).setValue(StairBlock.HALF, Half.TOP);
        install(stage, metadata, complete, List.of(new Bearing(roof, bearing,
                bearing.relative(wall), bracket)), p.roofStairs().defaultBlockState()
                        .setValue(StairBlock.FACING, roofFacing), Phase.ROOF);
    }

    private static void install(Builder stage, Metadata metadata, Map<BlockPos, Cell> complete,
            List<Bearing> bearings, BlockState expectedTarget, Phase targetPhase) {
        // Inspect the complete parcel first. Unknown cells, occupied future stages and reserved
        // circulation are never overwritten; only the exact original base-stage target qualifies.
        for (Bearing bearing : bearings) {
            Cell target = stage.cellAt(bearing.target());
            Cell anchor = complete.get(bearing.anchor());
            if (target == null || target.phase() != targetPhase || !target.state().equals(expectedTarget)
                    || stage.isOccupied(bearing.position()) || complete.containsKey(bearing.position())
                    || metadata.reservedAir.contains(bearing.position())
                    || metadata.accessTargets.contains(bearing.position())
                    || anchor == null || !anchor.state().isCollisionShapeFullBlock(
                            EmptyBlockGetter.INSTANCE, bearing.anchor())) {
                return;
            }
        }
        for (Bearing bearing : bearings) {
            BlockPos pos = bearing.position();
            stage.put(Phase.FRAME, pos.getX(), pos.getY(), pos.getZ(), bearing.state());
            complete.put(pos, stage.cellAt(pos));
        }
    }

    private static BlockPos position(Cell cell) {
        return new BlockPos(cell.x(), cell.y(), cell.z());
    }

    private record Bearing(BlockPos target, BlockPos position, BlockPos anchor, BlockState state) {
    }
}
