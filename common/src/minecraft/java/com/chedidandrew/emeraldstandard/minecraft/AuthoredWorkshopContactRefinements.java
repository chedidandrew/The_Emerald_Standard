package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.minecraft.AuthoredVillageStructures.Builder;
import com.chedidandrew.emeraldstandard.minecraft.AuthoredVillageStructures.Cell;
import com.chedidandrew.emeraldstandard.minecraft.AuthoredVillageStructures.Materials;
import com.chedidandrew.emeraldstandard.minecraft.AuthoredVillageStructures.Metadata;
import com.chedidandrew.emeraldstandard.minecraft.AuthoredVillageStructures.Phase;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Two authored workshop joints, composed before lighting; never edits placed world blocks. */
final class AuthoredWorkshopContactRefinements {
    private AuthoredWorkshopContactRefinements() { }

    static void refine(Builder base, Metadata metadata, Materials p, String templateId) {
        if (base.templateRevision < 4 || metadata.templateRevision < 4) return;
        List<Joint> joints = new ArrayList<>();
        if (templateId.equals("warehouse_basilica_05")) {
            // Nave piers already reach the ground at these three bays. Short transverse ties
            // attach both long railing runs to them without filling the open loading aisles.
            for (int z : new int[] {4, 8, 12}) {
                joints.add(new Joint(new BlockPos(7, 6, z), new BlockPos(6, 6, z),
                        new BlockPos(8, 6, z), Direction.Axis.X));
                joints.add(new Joint(new BlockPos(17, 6, z), new BlockPos(18, 6, z),
                        new BlockPos(16, 6, z), Direction.Axis.X));
            }
        } else if (templateId.equals("mine_adit_03")) {
            // Raised shed-roof strips had a half-block gap above the lower stair courses.
            // Continuous inset timber purlins meet both courses, clear of the central rail throat
            // and its hanging light. Keep the existing roof silhouette and every original cell.
            for (int z = 4; z <= 11; z++) {
                joints.add(new Joint(new BlockPos(3, 5, z), new BlockPos(3, 6, z),
                        new BlockPos(2, 5, z), Direction.Axis.Z));
            }
            for (int z = 6; z <= 11; z++) {
                joints.add(new Joint(new BlockPos(7, 5, z), new BlockPos(7, 6, z),
                        new BlockPos(8, 5, z), Direction.Axis.Z));
            }
        } else return;
        for (Joint joint : joints) {
            Cell target = base.cellAt(joint.target());
            Cell anchor = base.cellAt(joint.anchor());
            boolean basilica = templateId.equals("warehouse_basilica_05");
            if (base.isOccupied(joint.position()) || metadata.reservedAir.contains(joint.position())
                    || metadata.accessTargets.contains(joint.position()) || target == null || anchor == null
                    || (basilica ? !(target.state().getBlock() instanceof FenceBlock)
                            : !target.state().is(p.roofStairs()))
                    || !(anchor.state().isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, joint.anchor())
                            || !basilica && anchor.state().is(p.roofStairs()))) {
                return; // Unknown or reserved authored parcels remain unchanged as a group.
            }
        }
        for (Joint joint : joints) {
            BlockPos pos = joint.position();
            BlockState timber = p.timber().defaultBlockState()
                    .setValue(RotatedPillarBlock.AXIS, joint.axis());
            base.put(Phase.FRAME, pos.getX(), pos.getY(), pos.getZ(), timber);
        }
    }

    private record Joint(BlockPos position, BlockPos target, BlockPos anchor, Direction.Axis axis) { }
}
