package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.minecraft.AuthoredVillageStructures.Builder;
import com.chedidandrew.emeraldstandard.minecraft.AuthoredVillageStructures.Cell;
import com.chedidandrew.emeraldstandard.minecraft.AuthoredVillageStructures.Materials;
import com.chedidandrew.emeraldstandard.minecraft.AuthoredVillageStructures.Metadata;
import com.chedidandrew.emeraldstandard.minecraft.AuthoredVillageStructures.Phase;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/** A single authored market's coherent stall roofs, with no edits to placed structures. */
final class AuthoredMarketRefinements {
    private AuthoredMarketRefinements() {
    }

    /**
     * Replaces the exact ribbed roof recipe before regional trim and light composition. Two low,
     * continuous canopies make the open street and taller closing bell arch legible. Longitudinal
     * purlins connect the existing transverse frames; their small knees remain above head height.
     */
    static void finishLane(Builder base, Metadata metadata, Materials materials) {
        if (base.templateRevision < 3 || base.templateRevision > 9
                || metadata.templateRevision != base.templateRevision) {
            return;
        }
        if (metadata.width != 17 || metadata.depth != 13 || metadata.height != 10) {
            throw new IllegalStateException("Market lane refinement requires its exact authored envelope");
        }
        // Validate the complete old recipe first: never clear an unrelated fixture/decoration or
        // leave half a canopy if this master is later edited without updating its refinement.
        for (int side : new int[] {0, 10}) {
            for (int z = 0; z <= 12; z++) {
                for (int x = side; x <= side + 6; x++) {
                    int oldY = 5 + (side == 0 ? x : 16 - x) / 2;
                    Cell roof = base.cellAt(new BlockPos(x, oldY, z));
                    if (roof == null || roof.phase() != Phase.ROOF
                            || !roof.state().is(materials.roofStairs())) {
                        throw new IllegalStateException("Market lane roof recipe changed at "
                                + new BlockPos(x, oldY, z));
                    }
                    Cell newRoof = base.cellAt(new BlockPos(x, 5, z));
                    if (oldY != 5 && newRoof != null && !isInnerPost(x, z, newRoof, materials)) {
                        throw new IllegalStateException("Market lane canopy would cover an existing cell at "
                                + new BlockPos(x, 5, z));
                    }
                }
            }
        }
        for (int x : new int[] {5, 11}) {
            for (int z : new int[] {1, 4, 7, 10, 12}) {
                for (int y = 5; y <= 6; y++) {
                    Cell tip = base.cellAt(new BlockPos(x, y, z));
                    if (tip == null || !isInnerPost(x, z, tip, materials)) {
                        throw new IllegalStateException("Market lane inner post tip changed at "
                                + new BlockPos(x, y, z));
                    }
                }
            }
        }
        for (int x : new int[] {1, 5, 11, 15}) {
            for (int z = 0; z <= 12; z++) {
                Cell bearing = base.cellAt(new BlockPos(x, 4, z));
                if (bearing != null && (bearing.phase() != Phase.FRAME
                        || !bearing.state().is(materials.timber()))) {
                    throw new IllegalStateException("Market lane purlin would replace non-timber at "
                            + new BlockPos(x, 4, z));
                }
            }
        }
        for (int side : new int[] {0, 10}) {
            for (int x = side + 2; x <= side + 4; x++) {
                int oldY = 5 + (side == 0 ? x : 16 - x) / 2;
                for (int y = 6; y <= 7; y++) {
                    if (y != oldY && base.isOccupied(new BlockPos(x, y, 0))) {
                        throw new IllegalStateException("Market merchant pediment would cover a cell at "
                                + new BlockPos(x, y, 0));
                    }
                }
            }
        }

        for (int side : new int[] {0, 10}) {
            for (int z = 0; z <= 12; z++) {
                for (int x = side; x <= side + 6; x++) {
                    int oldY = 5 + (side == 0 ? x : 16 - x) / 2;
                    base.remove(x, oldY, z);
                }
            }
        }
        for (int x : new int[] {5, 11}) {
            for (int z : new int[] {1, 4, 7, 10, 12}) {
                for (int y = 5; y <= 6; y++) {
                    base.remove(x, y, z);
                }
            }
        }

        BlockState purlin = materials.timber().defaultBlockState()
                .setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z);
        for (int x : new int[] {1, 5, 11, 15}) {
            for (int z = 0; z <= 12; z++) {
                base.force(Phase.FRAME, x, 4, z, purlin);
            }
        }
        for (int side : new int[] {0, 10}) {
            for (int z = 0; z <= 12; z++) {
                for (int x = side; x <= side + 6; x++) {
                    base.put(Phase.ROOF, x, 5, z, materials.roofSlab().defaultBlockState());
                }
            }
            for (int z : new int[] {0, 12}) {
                BlockState fascia = materials.roofStairs().defaultBlockState()
                        .setValue(StairBlock.HALF, Half.TOP)
                        .setValue(StairBlock.FACING, z == 0 ? Direction.NORTH : Direction.SOUTH);
                for (int x = side; x <= side + 6; x++) {
                    base.putIfFree(Phase.FRAME, x, 4, z, fascia);
                }
            }
            for (int z : new int[] {1, 4, 7, 10, 12}) {
                base.putIfFree(Phase.FRAME, side + 2, 3, z,
                        materials.roofStairs().defaultBlockState()
                                .setValue(StairBlock.HALF, Half.TOP)
                                .setValue(StairBlock.FACING, Direction.WEST));
                base.putIfFree(Phase.FRAME, side + 4, 3, z,
                        materials.roofStairs().defaultBlockState()
                                .setValue(StairBlock.HALF, Half.TOP)
                                .setValue(StairBlock.FACING, Direction.EAST));
            }
            // Small merchant pediments belong to the front fascia, below the rear bell arch.
            // Their full-depth bearing band and timber panel meet without a half-slab gap.
            for (int x = side + 2; x <= side + 4; x++) {
                base.force(Phase.ROOF, x, 5, 0, materials.roofSlab().defaultBlockState()
                        .setValue(SlabBlock.TYPE, SlabType.DOUBLE));
                base.put(Phase.FRAME, x, 6, 0, x == side + 3
                        ? materials.accent().defaultBlockState() : purlin);
                base.put(Phase.ROOF, x, 7, 0, x == side + 3
                        ? materials.roofSlab().defaultBlockState()
                        : materials.roofStairs().defaultBlockState().setValue(StairBlock.FACING,
                                x < side + 3 ? Direction.EAST : Direction.WEST));
            }
        }
    }

    private static boolean isInnerPost(int x, int z, Cell cell, Materials materials) {
        return (x == 5 || x == 11) && (z == 1 || z == 4 || z == 7 || z == 10 || z == 12)
                && cell.phase() == Phase.FRAME && cell.state().is(materials.timber());
    }
}
