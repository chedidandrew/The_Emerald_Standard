package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.minecraft.AuthoredVillageStructures.Builder;
import com.chedidandrew.emeraldstandard.minecraft.AuthoredVillageStructures.Cell;
import com.chedidandrew.emeraldstandard.minecraft.AuthoredVillageStructures.Materials;
import com.chedidandrew.emeraldstandard.minecraft.AuthoredVillageStructures.Metadata;
import com.chedidandrew.emeraldstandard.minecraft.AuthoredVillageStructures.Phase;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;

/** Fixed roof carpentry for the courtyard smithy; no random ornaments or runtime world repairs. */
final class AuthoredSmithyRoofRefinements {
    private AuthoredSmithyRoofRefinements() { }

    static void refine(Builder b, Metadata m, Materials p) {
        if (m.templateRevision < 7) return;
        // Preserve the existing canopy underside, work bays and lights. Fill the roof volumes
        // above them: two unequal-length pitched wings joined by a lower rear saddle.
        boolean lowProfile = m.templateRevision >= 9;
        pitchedWing(b, p, 0, 3, 12, lowProfile);
        pitchedWing(b, p, 11, 6, 12, lowProfile);
        for (int x = 6; x <= 10; x++) {
            for (int z = 9; z <= 12; z++) {
                solid(b, p, x, 5, z);
                roof(b, x, 6, z, z == 9 ? stair(p, Direction.SOUTH)
                        : z == 12 ? stair(p, Direction.NORTH) : solid(p));
                if (z == 10 || z == 11) roof(b, x, 7, z, p.roofSlab().defaultBlockState());
            }
        }
        // A broad, stone-shouldered collector stays integrated with the forge below; the
        // skyline becomes two short slender pots instead of a tall crenellated brick panel.
        for (int x = 13; x <= 15; x++) {
            for (int y = 8; y <= 10; y++) b.remove(x, y, 10);
            b.force(Phase.ROOF, x, 8, 10, p.chimney().defaultBlockState());
        }
        BlockState pot = (p.chimney() == Blocks.CUT_SANDSTONE
                ? Blocks.SANDSTONE_WALL : Blocks.BRICK_WALL).defaultBlockState();
        for (int x : new int[] {13, 15}) b.force(Phase.ROOF, x, 9, 10, pot);
        if (lowProfile) perimeterEave(b, m, p);
        m.roofPeak = 9;
    }

    private static void pitchedWing(Builder b, Materials p, int left, int front, int rear,
            boolean lowProfile) {
        for (int x = left; x <= left + 5; x++) {
            int inset = Math.min(x - left, left + 5 - x);
            int top = 6 + (lowProfile ? Math.min(1, inset) : inset);
            for (int z = front; z <= rear; z++) {
                solid(b, p, x, 5, z);
                for (int y = 6; y < top; y++) solid(b, p, x, y, z);
                roof(b, x, top, z, (lowProfile ? inset >= 1 : inset == 2) ? p.roofSlab().defaultBlockState()
                        : stair(p, x < left + 3 ? Direction.EAST : Direction.WEST));
                // Gable-end timber trusses are inset beneath the roof, bearing on the old
                // wall-top course. Repeated ends make the long roof read as built carpentry.
                if ((z == front || z == rear) && inset > 0) {
                    for (int y = 6; y < top; y++) roof(b, x, y, z,
                            p.timber().defaultBlockState().setValue(RotatedPillarBlock.AXIS,
                                    y == 6 ? Direction.Axis.X : Direction.Axis.Y));
                }
            }
        }
    }

    /** One continuous shallow fascia follows the joined wings, including the courtyard returns. */
    private static void perimeterEave(Builder b, Metadata m, Materials p) {
        BlockState trim = p.roofSlab().defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP);
        for (int x = -1; x <= 17; x++) {
            for (int z = 2; z <= 13; z++) {
                if (roofFootprint(x, z)) continue;
                boolean adjacent = false;
                // Corner infill joins the two cardinal fascia runs without a missing corner tile.
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) adjacent |= roofFootprint(x + dx, z + dz);
                }
                BlockPos pos = new BlockPos(x, 5, z);
                if (adjacent && b.cellAt(pos) == null && !m.reservedAir.contains(pos)
                        && !m.accessTargets.contains(pos)) b.force(Phase.ROOF, x, 5, z, trim);
            }
        }
        // The fascia shelters one additional floor row. Small front-post sconces illuminate
        // these new covered edges without moving any original interior or yard fixture.
        for (int[] corner : new int[][] {{0, 2}, {16, 5}}) {
            BlockPos pos = new BlockPos(corner[0], 3, corner[1]);
            if (b.cellAt(pos) == null && !m.reservedAir.contains(pos) && !m.accessTargets.contains(pos)) {
                b.force(Phase.DECOR, pos.getX(), pos.getY(), pos.getZ(),
                        Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.NORTH));
            }
        }
    }

    private static boolean roofFootprint(int x, int z) {
        return (x >= 0 && x <= 5 && z >= 3 && z <= 12)
                || (x >= 11 && x <= 16 && z >= 6 && z <= 12)
                || (x >= 6 && x <= 10 && z >= 9 && z <= 12);
    }

    private static BlockState stair(Materials p, Direction facing) {
        return p.roofStairs().defaultBlockState().setValue(StairBlock.FACING, facing);
    }

    private static BlockState solid(Materials p) {
        return p.roofSlab().defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE);
    }

    private static void solid(Builder b, Materials p, int x, int y, int z) {
        roof(b, x, y, z, solid(p));
    }

    private static void roof(Builder b, int x, int y, int z, BlockState state) {
        Cell prior = b.cellAt(new BlockPos(x, y, z));
        // Do not replace the collector's existing masonry or fixtures with timber roofing.
        if (prior != null && !(prior.state().getBlock() instanceof SlabBlock)
                && !(prior.state().getBlock() instanceof StairBlock)) return;
        b.force(Phase.ROOF, x, y, z, state);
    }
}
